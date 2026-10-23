package com.game_server.general.dao;

import com.game.server.proto.ShopModel.ShopDailySnapshotProto;
import com.game.server.proto.ShopPurchaseResponse.ShopPurchaseResponseProto;
import com.game.server.proto.BattlePassModel.PlayerBattlePassStateProto;
import com.game_server.general.service.GameException;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.*;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

public final class GameDao {
    private final DynamoDbClient db;
    private final String table;

    public GameDao(DynamoDbClient db, String table) {
        this.db = Objects.requireNonNull(db);
        this.table = Objects.requireNonNull(table);
    }

    public String table() { return table; }

    public void prepare() {
        try { db.describeTable(DescribeTableRequest.builder().tableName(table).build()); }
        catch (ResourceNotFoundException missing) {
            try {
                db.createTable(CreateTableRequest.builder().tableName(table).billingMode(BillingMode.PAY_PER_REQUEST)
                        .keySchema(KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build())
                        .attributeDefinitions(AttributeDefinition.builder().attributeName("id").attributeType(ScalarAttributeType.S).build()).build());
            } catch (ResourceInUseException concurrent) { }
        }
        db.waiter().waitUntilTableExists(DescribeTableRequest.builder().tableName(table).build());
    }

    public Identity identity(String deviceId) {
        Map<String, AttributeValue> device = get("device:" + deviceId);
        if (device.isEmpty()) return null;
        String accountId = device.get("account_id").s();
        String playerId = device.get("player_id").s();
        Map<String, AttributeValue> account = get("account:" + accountId);
        if (account.isEmpty() || !deviceId.equals(account.get("device_id").s())
                || !playerId.equals(account.get("player_id").s()))
            throw new IllegalStateException("Inconsistent account identity");
        return new Identity(accountId, playerId, deviceId, "", 0);
    }

    public Identity createIdentity(String deviceId) {
        Identity existing = identity(deviceId);
        if (existing != null) return existing;
        String accountId = UUID.randomUUID().toString();
        String playerId = UUID.randomUUID().toString();
        Map<String, AttributeValue> device = new HashMap<>();
        device.put("id", s("device:" + deviceId)); device.put("account_id", s(accountId)); device.put("player_id", s(playerId));
        Map<String, AttributeValue> account = new HashMap<>();
        account.put("id", s("account:" + accountId)); account.put("device_id", s(deviceId)); account.put("player_id", s(playerId));
        Player player = new Player(accountId, playerId, 0, Map.of("gold", 1000L, "gem", 100L, "xp", 0L), ShopDailySnapshotProto.getDefaultInstance());
        try {
            db.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(
                    absent(device), absent(account), absent(item(player, 0))).build());
            return new Identity(accountId, playerId, deviceId, "", 0);
        } catch (TransactionCanceledException conflict) {
            if (!conditional(conflict)) throw conflict;
            existing = identity(deviceId);
            if (existing != null) return existing;
            throw new GameException(409, "RETRY", "Retry this request");
        }
    }

    public Player player(String playerId) {
        Map<String, AttributeValue> data = get("player:" + playerId);
        if (data.isEmpty()) throw new GameException(401, "AUTH_INVALID", "Player does not exist");
        Map<String, Long> resources = new TreeMap<>();
        data.get("resources").m().forEach((key, value) -> resources.put(key, Long.parseLong(value.n())));
        try {
            return new Player(data.get("account_id").s(), playerId, Long.parseLong(data.get("version").n()), resources,
                    data.containsKey("daily") ? ShopDailySnapshotProto.parseFrom(data.get("daily").b().asByteArray())
                            : ShopDailySnapshotProto.getDefaultInstance());
        } catch (InvalidProtocolBufferException malformed) { throw new IllegalStateException("Invalid persisted daily snapshot", malformed); }
    }

    public BattlePassState battlePass(String playerId) {
        Map<String, AttributeValue> data = get("battlepass:" + playerId);
        if (data.isEmpty()) return new BattlePassState(-1, PlayerBattlePassStateProto.getDefaultInstance());
        try {
            return new BattlePassState(Long.parseLong(data.get("version").n()),
                    PlayerBattlePassStateProto.parseFrom(data.get("state").b().asByteArray()));
        } catch (InvalidProtocolBufferException malformed) { throw new IllegalStateException("Invalid Battle Pass state", malformed); }
    }

    public BattlePassReceipt battlePassReceipt(String playerId, String requestId) {
        return battlePassReceipt(get("battlepass-op:" + playerId + ":" + requestId));
    }

    public BattlePassReceipt questGrant(String playerId, String grantId) {
        return battlePassReceipt(get("battlepass-grant:" + playerId + ":" + grantId));
    }

    private static BattlePassReceipt battlePassReceipt(Map<String, AttributeValue> data) {
        if (data.isEmpty()) return null;
        return new BattlePassReceipt(data.get("operation").s(), data.get("input").b().asByteArray(), data.get("response").b().asByteArray());
    }

    public boolean linkBattlePassReceipt(String playerId, String requestId, BattlePassReceipt receipt) {
        try {
            db.putItem(PutItemRequest.builder().tableName(table)
                    .item(battlePassReceiptItem("battlepass-op:" + playerId + ":" + requestId, receipt))
                    .conditionExpression("attribute_not_exists(id)").build());
            return true;
        } catch (ConditionalCheckFailedException conflict) { return false; }
    }

    public boolean commitBattlePass(Player player, BattlePassState state, String requestId, BattlePassReceipt receipt, String grantId) {
        Map<String, AttributeValue> data = new HashMap<>();
        data.put("id", s("battlepass:" + player.playerId));
        data.put("version", n(state.version + 1));
        data.put("state", AttributeValue.builder().b(SdkBytes.fromByteArray(state.data.toByteArray())).build());
        Put.Builder update = Put.builder().tableName(table).item(data);
        if (state.version < 0) update.conditionExpression("attribute_not_exists(id)");
        else update.conditionExpression("#v = :v").expressionAttributeNames(Map.of("#v", "version"))
                .expressionAttributeValues(Map.of(":v", n(state.version)));
        Put playerUpdate = Put.builder().tableName(table).item(item(player, player.version + 1))
                .conditionExpression("#v = :v").expressionAttributeNames(Map.of("#v", "version"))
                .expressionAttributeValues(Map.of(":v", n(player.version))).build();
        List<TransactWriteItem> writes = new ArrayList<>();
        writes.add(TransactWriteItem.builder().put(playerUpdate).build());
        writes.add(TransactWriteItem.builder().put(update.build()).build());
        writes.add(absent(battlePassReceiptItem("battlepass-op:" + player.playerId + ":" + requestId, receipt)));
        if (grantId != null) writes.add(absent(battlePassReceiptItem("battlepass-grant:" + player.playerId + ":" + grantId, receipt)));
        try {
            db.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(writes).build());
            return true;
        } catch (TransactionCanceledException conflict) {
            if (conditional(conflict)) return false;
            throw conflict;
        }
    }

    private static Map<String, AttributeValue> battlePassReceiptItem(String id, BattlePassReceipt receipt) {
        return Map.of("id", s(id), "operation", s(receipt.operation),
                "input", AttributeValue.builder().b(SdkBytes.fromByteArray(receipt.input)).build(),
                "response", AttributeValue.builder().b(SdkBytes.fromByteArray(receipt.response)).build());
    }

    public boolean save(Player player) {
        try {
            db.putItem(PutItemRequest.builder().tableName(table).item(item(player, player.version + 1))
                    .conditionExpression("#v = :v").expressionAttributeNames(Map.of("#v", "version"))
                    .expressionAttributeValues(Map.of(":v", n(player.version))).build());
            return true;
        } catch (ConditionalCheckFailedException conflict) { return false; }
    }

    public Receipt receipt(String playerId, String requestId) {
        Map<String, AttributeValue> data = get("purchase:" + playerId + ":" + requestId);
        if (data.isEmpty()) return null;
        try { return new Receipt(data.get("offer_id").s(), Integer.parseInt(data.get("amount").n()),
                ShopPurchaseResponseProto.parseFrom(data.get("response").b().asByteArray())); }
        catch (InvalidProtocolBufferException malformed) { throw new IllegalStateException("Invalid persisted purchase receipt", malformed); }
    }

    public boolean purchase(Player player, String requestId, String offerId, int amount, ShopPurchaseResponseProto response) {
        Map<String, AttributeValue> receipt = new HashMap<>();
        receipt.put("id", s("purchase:" + player.playerId + ":" + requestId));
        receipt.put("offer_id", s(offerId)); receipt.put("amount", n(amount));
        receipt.put("response", AttributeValue.builder().b(SdkBytes.fromByteArray(response.toByteArray())).build());
        Put update = Put.builder().tableName(table).item(item(player, player.version + 1))
                .conditionExpression("#v = :v").expressionAttributeNames(Map.of("#v", "version"))
                .expressionAttributeValues(Map.of(":v", n(player.version))).build();
        try {
            db.transactWriteItems(TransactWriteItemsRequest.builder().transactItems(
                    TransactWriteItem.builder().put(update).build(), absent(receipt)).build());
            return true;
        } catch (TransactionCanceledException conflict) {
            if (conditional(conflict)) return false;
            throw conflict;
        }
    }

    private static boolean conditional(TransactionCanceledException error) {
        return error.cancellationReasons().stream().anyMatch(reason -> "ConditionalCheckFailed".equals(reason.code())
                || "TransactionConflict".equals(reason.code()));
    }
    private Map<String, AttributeValue> get(String id) {
        return db.getItem(GetItemRequest.builder().tableName(table).key(Map.of("id", s(id))).consistentRead(true).build()).item();
    }
    private TransactWriteItem absent(Map<String, AttributeValue> data) {
        return TransactWriteItem.builder().put(Put.builder().tableName(table).item(data)
                .conditionExpression("attribute_not_exists(id)").build()).build();
    }
    private static Map<String, AttributeValue> item(Player player, long version) {
        Map<String, AttributeValue> result = new HashMap<>();
        result.put("id", s("player:" + player.playerId)); result.put("account_id", s(player.accountId)); result.put("version", n(version));
        Map<String, AttributeValue> resources = new HashMap<>();
        player.resources.forEach((key, value) -> resources.put(key, n(value)));
        result.put("resources", AttributeValue.builder().m(resources).build());
        result.put("daily", AttributeValue.builder().b(SdkBytes.fromByteArray(player.daily.toByteArray())).build());
        return result;
    }
    private static AttributeValue s(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue n(long value) { return AttributeValue.builder().n(Long.toString(value)).build(); }

    public static final class BattlePassReceipt {
        public final String operation;
        public final byte[] input, response;
        public BattlePassReceipt(String operation, byte[] input, byte[] response) {
            this.operation = operation; this.input = input.clone(); this.response = response.clone();
        }
    }

    public static final class BattlePassState {
        public final long version;
        public final PlayerBattlePassStateProto data;
        public BattlePassState(long version, PlayerBattlePassStateProto data) { this.version = version; this.data = data; }
    }

    public static final class Identity {
        public final String accountId, playerId, deviceId, sessionId;
        public final long expiresAt;
        public Identity(String accountId, String playerId, String deviceId, String sessionId, long expiresAt) {
            this.accountId = accountId; this.playerId = playerId; this.deviceId = deviceId; this.sessionId = sessionId; this.expiresAt = expiresAt;
        }
    }
    public static final class Player {
        public final String accountId, playerId;
        public final long version;
        public final Map<String, Long> resources;
        public final ShopDailySnapshotProto daily;
        public Player(String accountId, String playerId, long version, Map<String, Long> resources, ShopDailySnapshotProto daily) {
            this.accountId = accountId; this.playerId = playerId; this.version = version;
            this.resources = Collections.unmodifiableMap(new TreeMap<>(resources)); this.daily = daily;
        }
    }
    public static final class Receipt {
        public final String offerId;
        public final int amount;
        public final ShopPurchaseResponseProto response;
        public Receipt(String offerId, int amount, ShopPurchaseResponseProto response) {
            this.offerId = offerId; this.amount = amount; this.response = response;
        }
    }
}

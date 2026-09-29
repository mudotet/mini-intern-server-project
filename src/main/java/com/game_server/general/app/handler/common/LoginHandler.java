package com.game_server.general.app.handler.common;

import com.game.server.proto.CommonLoginContract;
import com.game_server.general.app.base.ApiHandler;
import com.game_server.general.app.base.BaseApiHandler;
import com.game_server.general.app.helper.JwtHelper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import redis.clients.jedis.JedisPooled;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

@ApiHandler(value = "001002", auth = false, lock = false, blueprint = false)
public final class LoginHandler extends BaseApiHandler {
    private static final String TABLE = "login_devices";
    private static final int SESSION_SECONDS = 12 * 60 * 60;
    private final DynamoDbClient dynamodb;
    private final JedisPooled redis;
    private final JwtHelper jwt;

    public LoginHandler(DynamoDbClient dynamodb, JedisPooled redis, JwtHelper jwt) {
        this.dynamodb = dynamodb;
        this.redis = redis;
        this.jwt = jwt;
    }

    public void prepare() {
        try {
            dynamodb.describeTable(DescribeTableRequest.builder().tableName(TABLE).build());
        } catch (ResourceNotFoundException exception) {
            dynamodb.createTable(CreateTableRequest.builder().tableName(TABLE)
                    .attributeDefinitions(AttributeDefinition.builder().attributeName("device_id").attributeType(ScalarAttributeType.S).build())
                    .keySchema(KeySchemaElement.builder().attributeName("device_id").keyType(KeyType.HASH).build())
                    .billingMode(BillingMode.PAY_PER_REQUEST).build());
            dynamodb.waiter().waitUntilTableExists(DescribeTableRequest.builder().tableName(TABLE).build());
        }
    }

    @Override
    protected Message process(byte[] body) throws InvalidProtocolBufferException {
        CommonLoginContract.CommonLoginRequestProto request = CommonLoginContract.CommonLoginRequestProto.parseFrom(body);
        boolean newDevice = request.getDeviceId().isBlank();
        String deviceId = newDevice ? UUID.randomUUID().toString() : request.getDeviceId();
        if (deviceId.length() > 256 || request.getPlayerId().length() > 256) {
            throw new IllegalArgumentException("Invalid identity");
        }
        Map<String, AttributeValue> item = dynamodb.getItem(GetItemRequest.builder().tableName(TABLE)
                .key(Map.of("device_id", AttributeValue.fromS(deviceId))).consistentRead(true).build()).item();
        String playerId;
        if (item.isEmpty()) {
            if (!newDevice || !request.getPlayerId().isBlank()) {
                throw new IllegalStateException("Unknown device");
            }
            playerId = UUID.randomUUID().toString();
            try {
                dynamodb.putItem(PutItemRequest.builder().tableName(TABLE)
                        .item(Map.of("device_id", AttributeValue.fromS(deviceId), "player_id", AttributeValue.fromS(playerId)))
                        .conditionExpression("attribute_not_exists(device_id)").build());
            } catch (ConditionalCheckFailedException exception) {
                item = dynamodb.getItem(GetItemRequest.builder().tableName(TABLE)
                        .key(Map.of("device_id", AttributeValue.fromS(deviceId))).consistentRead(true).build()).item();
                if (item.isEmpty()) {
                    throw exception;
                }
                playerId = item.get("player_id").s();
            }
        } else {
            playerId = item.get("player_id").s();
        }
        if (!request.getPlayerId().isBlank() && !request.getPlayerId().equals(playerId)) {
            throw new IllegalStateException("Player mismatch");
        }
        String sessionId = UUID.randomUUID().toString();
        long expiresAt = Instant.now().getEpochSecond() + SESSION_SECONDS;
        String token = jwt.create(playerId, deviceId, sessionId, expiresAt);
        redis.setex("session:" + deviceId, SESSION_SECONDS, sessionId);
        return CommonLoginContract.CommonLoginResponseProto.newBuilder()
                .setPlayerId(playerId).setDeviceId(deviceId).setAccessToken(token).setSessionExpiresAt(expiresAt)
                .build();
    }
}

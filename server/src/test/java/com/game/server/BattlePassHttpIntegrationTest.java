package com.game.server;

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import com.game.server.proto.BattlePassContract.BattlePassMutationResponseProto;
import com.game.server.proto.BattlePassContract.BattlePassPremiumRequestProto;
import com.game.server.proto.BattlePassContract.BattlePassStateProto;
import com.game_server.general.app.helper.JwtHelper;
import com.game_server.general.dao.GameDao;
import com.game_server.general.service.SessionService;
import com.google.protobuf.Empty;
import com.google.protobuf.Message;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPooled;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class BattlePassHttpIntegrationTest {
    private final String table = "mini_bp_test_" + UUID.randomUUID().toString().replace("-", "");
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-09T06:00:00Z"));
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private DynamoDbClient dynamo;
    private JedisPooled redis;
    private GameDao dao;
    private JwtHelper jwt;
    private SessionService sessions;
    private ActorSystem actors;
    private ServerBinding binding;
    private String base;
    private String token;
    private Struct login;

    @BeforeEach
    void start() throws Exception {
        dynamo = DynamoDbClient.builder()
                .endpointOverride(URI.create(System.getenv().getOrDefault("IT_DYNAMODB_ENDPOINT", "http://localhost:8000")))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"))).build();
        redis = new JedisPooled(System.getenv().getOrDefault("IT_REDIS_HOST", "localhost"),
                Integer.parseInt(System.getenv().getOrDefault("IT_REDIS_PORT", "6379")));
        assertEquals("PONG", redis.ping());
        dao = new GameDao(dynamo, table);
        dao.prepare();
        jwt = new JwtHelper(UUID.randomUUID().toString() + UUID.randomUUID(), clock);
        sessions = new SessionService(redis, jwt, table, clock);
        bind();
        login = json(post("001002", "{}", id()), 200);
        token = text(login, "access_token");
    }

    private void bind() throws Exception {
        actors = ActorSystem.create("test-" + table);
        binding = Http.get(actors).newServerAt("127.0.0.1", 0)
                .bind(Main.handlers(dao, redis, jwt, sessions, clock).routes())
                .toCompletableFuture().get(20, TimeUnit.SECONDS);
        base = "http://127.0.0.1:" + binding.localAddress().getPort();
    }

    private void unbind() throws Exception {
        if (binding != null) binding.unbind().toCompletableFuture().get(10, TimeUnit.SECONDS);
        if (actors != null) {
            actors.terminate();
            actors.getWhenTerminated().toCompletableFuture().get(10, TimeUnit.SECONDS);
        }
    }

    @AfterEach
    void stop() throws Exception {
        try {
            unbind();
        } finally {
            try {
                if (redis != null) {
                    for (String key : redis.keys(table + ":*")) redis.del(key);
                    redis.close();
                }
            } finally {
                if (dynamo != null) {
                    try { dynamo.deleteTable(request -> request.tableName(table)); }
                    catch (ResourceNotFoundException ignored) { }
                    finally { dynamo.close(); }
                }
            }
        }
    }

    @Test
    void existingPlayersStartWithoutProgressAndKeepTheirResources() throws Exception {
        Map<String, AttributeValue> legacy = new HashMap<>();
        legacy.put("id", string("player:" + text(login, "player_id")));
        legacy.put("account_id", string(text(login, "account_id")));
        legacy.put("version", numeric(0));
        legacy.put("resources", resourceAttribute(777, 61, 47));
        dynamo.putItem(request -> request.tableName(table).item(legacy));

        Struct initial = state();
        assertEquals("weekly:" + Instant.parse("2026-11-08T17:00:00Z").getEpochSecond(), text(initial, "pass_id"));
        assertEquals(Instant.parse("2026-11-08T17:00:00Z").getEpochSecond(), Long.parseLong(text(initial, "starts_at")));
        assertEquals(7 * 86400, Long.parseLong(text(initial, "ends_at")) - Long.parseLong(text(initial, "starts_at")));
        assertEquals(Instant.parse("2026-11-09T17:00:00Z").getEpochSecond(), Long.parseLong(text(initial, "quests_reset_at")));
        assertEquals(0, number(initial, "expbp"));
        assertFalse(bool(initial, "premium_owned"));
        assertEquals(6, list(initial, "milestones").size());
        for (int step = 1; step <= 6; step++) {
            assertEquals(step * 100, number(list(initial, "milestones").get(step - 1).getStructValue(), "required_expbp"));
            assertFalse(bool(node(initial, "free_" + step), "claimed"));
            assertFalse(bool(node(initial, "premium_" + step), "claimable"));
        }
        assertEquals(50, number(object(object(initial, "premium_price"), "currency"), "gem"));
        assertEquals(0, number(quest(initial, "study"), "progress"));
        assertEquals(initial, state());
        Struct player = player();
        assertEquals(777, resource(player, "gold"));
        assertEquals(61, resource(player, "gem"));
        assertEquals(47, resource(player, "xp"));
    }

    @Test
    void shopCommitsProgressButRetriesAndFailedPurchasesDoNot() throws Exception {
        String purchaseId = id();
        Struct receipt = json(post("003002", purchase("static:xp_gold"), purchaseId), 200);
        Struct afterFirst = state();
        assertEquals(0, number(afterFirst, "expbp"));
        assertEquals(1, number(quest(afterFirst, "trade_once"), "progress"));
        assertEquals(1, number(quest(afterFirst, "trade_three"), "progress"));
        assertEquals(100, number(quest(afterFirst, "study"), "progress"));
        assertEquals(receipt, json(post("003002", purchase("static:xp_gold"), purchaseId), 200));
        assertEquals(afterFirst, state());

        setResources(0, 0, 100);
        error(post("003002", purchase("static:xp_gold"), id()), 409, "INSUFFICIENT_FUNDS");
        error(post("003002", "{\"id\":\"static:xp_gold\",\"amount\":2}", id()), 400, "INVALID_PURCHASE");
        assertEquals(afterFirst, state());
        assertEquals(0, resource(player(), "gold"));
        assertEquals(100, resource(player(), "xp"));
    }

    @Test
    void questsAreManualAndDoNotConfuseXpWithBattlePassExp() throws Exception {
        Struct initial = state();
        String once = text(quest(initial, "trade_once"), "quest_id");
        error(post("004004", questClaim(initial, once), id()), 409, "QUEST_INCOMPLETE");
        buyXp();
        Struct eligible = state();
        assertEquals(0, number(eligible, "expbp"));
        assertTrue(bool(quest(eligible, "trade_once"), "claimable"));
        assertFalse(bool(quest(eligible, "study"), "claimable"));
        String onceId = id();
        Struct firstClaim = json(post("004004", questClaim(initial, once), onceId), 200);
        assertEquals(100, number(object(firstClaim, "pass"), "expbp"));
        assertEquals(100, resource(firstClaim, "xp"));
        assertEquals(100, item(firstClaim, "expbp"));
        assertFalse(list(firstClaim, "resources").stream().map(Value::getStructValue)
                .anyMatch(value -> text(value, "resource_id").equals("expbp")));
        assertEquals(firstClaim, json(post("004004", questClaim(initial, once), onceId), 200));
        error(post("004004", questClaim(initial, once), id()), 409, "ALREADY_CLAIMED");
        buyXp();
        Struct secondClaim = claimQuest("study");
        assertEquals(200, number(object(secondClaim, "pass"), "expbp"));
        buyXp();
        Struct thirdClaim = claimQuest("trade_three");
        assertEquals(350, number(object(thirdClaim, "pass"), "expbp"));
        assertEquals(300, resource(thirdClaim, "xp"));
        assertEquals(200, number(quest(state(), "study"), "progress"));
        assertEquals(3, number(quest(state(), "trade_three"), "progress"));
    }

    @Test
    void freeRewardsRequireThresholdAndClaimOnceWithoutSpendingExp() throws Exception {
        Struct initial = state();
        error(post("004002", nodeClaim(initial, "free_1"), id()), 409, "INSUFFICIENT_EXPBP");
        buyXp();
        claimQuest("trade_once");
        Struct reached = state();
        assertTrue(bool(node(reached, "free_1"), "claimable"));
        error(post("004002", nodeClaim(reached, "premium_1"), id()), 409, "PREMIUM_REQUIRED");
        Struct claimed = json(post("004002", nodeClaim(reached, "free_1"), id()), 200);
        assertEquals(100, number(object(claimed, "pass"), "expbp"));
        assertEquals(1000, resource(claimed, "gold"));
        assertEquals(100, item(claimed, "gold"));
        assertTrue(bool(node(object(claimed, "pass"), "free_1"), "claimed"));
        assertFalse(bool(node(object(claimed, "pass"), "free_1"), "claimable"));
        error(post("004002", nodeClaim(reached, "free_1"), id()), 409, "ALREADY_CLAIMED");
        error(post("004002", nodeClaim(reached, "free_2"), id()), 409, "INSUFFICIENT_EXPBP");
        assertEquals(1, number(quest(state(), "trade_once"), "progress"));
        assertEquals(100, number(quest(state(), "study"), "progress"));
    }

    @Test
    void premiumCostsFiftyGemsOnceAndDoesNotGrantExpOrLockedRewards() throws Exception {
        Struct initial = state();
        Struct premium = json(post("004003", pass(initial), id()), 200);
        assertTrue(bool(object(premium, "pass"), "premium_owned"));
        assertEquals(0, number(object(premium, "pass"), "expbp"));
        assertEquals(50, resource(premium, "gem"));
        assertTrue(list(premium, "items").isEmpty());
        error(post("004002", nodeClaim(initial, "premium_1"), id()), 409, "INSUFFICIENT_EXPBP");
        error(post("004003", pass(initial), id()), 409, "ALREADY_PREMIUM");
        buyXp();
        claimQuest("trade_once");
        Struct claimed = json(post("004002", nodeClaim(initial, "premium_1"), id()), 200);
        assertEquals(60, resource(claimed, "gem"));
        assertEquals(10, item(claimed, "gem"));
        assertEquals(100, number(object(claimed, "pass"), "expbp"));
        error(post("004002", nodeClaim(initial, "premium_2"), id()), 409, "INSUFFICIENT_EXPBP");
    }

    @Test
    void receiptBindsActionPassAndTargetAndReplaysTheOriginalSnapshot() throws Exception {
        buyXp();
        claimQuest("trade_once");
        Struct current = state();
        String requestId = id();
        String body = nodeClaim(current, "free_1");
        Struct receipt = json(post("004002", body, requestId), 200);
        error(post("004002", nodeClaim(current, "free_2"), requestId), 409, "REQUEST_ID_REUSED");
        error(post("004002", "{\"pass_id\":\"weekly:old\",\"node_id\":\"free_1\"}", requestId), 409, "REQUEST_ID_REUSED");
        error(post("004003", pass(current), requestId), 409, "REQUEST_ID_REUSED");
        error(post("004004", questClaim(current, text(quest(current, "study"), "quest_id")), requestId), 409, "REQUEST_ID_REUSED");
        buyXp();
        claimQuest("study");
        Struct resourcesBeforeReplay = player();
        assertEquals(receipt, json(post("004002", body, requestId), 200));
        assertEquals(resourcesBeforeReplay, player());
        assertEquals(100, number(object(receipt, "pass"), "expbp"));
        assertEquals(200, number(state(), "expbp"));
    }

    @Test
    void concurrentClaimsWithSameOrDifferentIdsGrantEachNodeOnlyOnce() throws Exception {
        buyXp();
        claimQuest("trade_once");
        Struct pass = state();
        String requestId = id();
        List<HttpRequest> repeated = new ArrayList<>();
        for (int index = 0; index < 4; index++) repeated.add(request("004002", nodeClaim(pass, "free_1"), requestId));
        List<HttpResponse<String>> replies = concurrent(repeated);
        Struct receipt = json(replies.get(0), 200);
        for (HttpResponse<String> response : replies) assertEquals(receipt, json(response, 200));
        assertEquals(1000, resource(player(), "gold"));

        buyXp();
        claimQuest("study");
        List<HttpRequest> distinct = new ArrayList<>();
        for (int index = 0; index < 4; index++) distinct.add(request("004002", nodeClaim(pass, "free_2"), id()));
        replies = concurrent(distinct);
        assertEquals(1, replies.stream().filter(response -> response.statusCode() == 200).count());
        for (HttpResponse<String> response : replies) {
            if (response.statusCode() != 200) error(response, 409, "ALREADY_CLAIMED");
        }
        assertEquals(105, resource(player(), "gem"));
    }

    @Test
    void premiumAndShopCompeteForOnePlayerResourceVersionWithoutOverspending() throws Exception {
        setResources(1000, 50, 0);
        Struct initial = state();
        List<HttpResponse<String>> replies = concurrent(List.of(
                request("004003", pass(initial), id()),
                request("003002", purchase("static:gold_large"), id())));
        assertEquals(1, replies.stream().filter(response -> response.statusCode() == 200).count());
        for (HttpResponse<String> response : replies) {
            if (response.statusCode() != 200) error(response, 409, "INSUFFICIENT_FUNDS");
        }
        boolean premiumWon = replies.get(0).statusCode() == 200;
        Struct resources = player();
        Struct current = state();
        assertEquals(premiumWon ? 0 : 35, resource(resources, "gem"));
        assertEquals(premiumWon ? 1000 : 1350, resource(resources, "gold"));
        assertEquals(premiumWon, bool(current, "premium_owned"));
        assertEquals(premiumWon ? 0 : 1, number(quest(current, "trade_once"), "progress"));
        assertEquals(0, number(current, "expbp"));
    }

    @Test
    void dayRolloverResetsQuestsButRetainsSeasonPremiumClaimsAndHistoricalReceipts() throws Exception {
        buyXp();
        Struct firstDay = state();
        String questBody = questClaim(firstDay, text(quest(firstDay, "trade_once"), "quest_id"));
        String questRequestId = id();
        Struct questReceipt = json(post("004004", questBody, questRequestId), 200);
        buyXp();
        claimQuest("study");
        buyXp();
        claimQuest("trade_three");
        json(post("004003", pass(firstDay), id()), 200);
        json(post("004002", nodeClaim(firstDay, "free_1"), id()), 200);
        Struct beforeRestart = state();
        Struct resources = player();

        unbind();
        dao = new GameDao(dynamo, table);
        sessions = new SessionService(redis, jwt, table, clock);
        bind();
        assertEquals(beforeRestart, state());

        clock.now = Instant.ofEpochSecond(Long.parseLong(text(firstDay, "quests_reset_at")));
        reauthenticate();
        Struct nextDay = state();
        assertEquals(text(firstDay, "pass_id"), text(nextDay, "pass_id"));
        assertEquals(350, number(nextDay, "expbp"));
        assertTrue(bool(nextDay, "premium_owned"));
        assertTrue(bool(node(nextDay, "free_1"), "claimed"));
        for (Value value : list(nextDay, "quests")) {
            assertEquals(0, number(value.getStructValue(), "progress"));
            assertFalse(bool(value.getStructValue(), "claimed"));
        }
        assertNotEquals(text(quest(firstDay, "trade_once"), "quest_id"), text(quest(nextDay, "trade_once"), "quest_id"));
        error(post("004004", questBody, id()), 409, "QUEST_EXPIRED");
        assertEquals(questReceipt, json(post("004004", questBody, questRequestId), 200));
        assertEquals(resources, player());
        assertEquals(350, number(state(), "expbp"));
        Struct xpReward = json(post("004002", nodeClaim(nextDay, "free_3"), id()), 200);
        assertEquals(resource(resources, "xp") + 150, resource(xpReward, "xp"));
        assertEquals(0, number(quest(state(), "study"), "progress"));
        assertEquals(0, number(quest(state(), "trade_once"), "progress"));
    }

    @Test
    void nextSeasonResetsOnlyPassStateAndStillReplaysExpiredSeasonReceipts() throws Exception {
        buyXp();
        claimQuest("trade_once");
        Struct previous = state();
        String claimBody = nodeClaim(previous, "free_1");
        String requestId = id();
        Struct receipt = json(post("004002", claimBody, requestId), 200);
        json(post("004003", pass(previous), id()), 200);
        Struct resources = player();
        clock.now = Instant.ofEpochSecond(Long.parseLong(text(previous, "ends_at")));
        reauthenticate();
        error(post("004002", claimBody, id()), 409, "PASS_EXPIRED");
        error(post("004003", pass(previous), id()), 409, "PASS_EXPIRED");
        error(post("004004", questClaim(previous, text(quest(previous, "trade_once"), "quest_id")), id()), 409, "PASS_EXPIRED");
        assertEquals(receipt, json(post("004002", claimBody, requestId), 200));
        Struct next = state();
        assertNotEquals(text(previous, "pass_id"), text(next, "pass_id"));
        assertEquals(0, number(next, "expbp"));
        assertFalse(bool(next, "premium_owned"));
        for (int step = 1; step <= 6; step++) {
            assertFalse(bool(node(next, "free_" + step), "claimed"));
            assertFalse(bool(node(next, "premium_" + step), "claimed"));
        }
        assertEquals(0, number(quest(next, "trade_once"), "progress"));
        assertEquals(resources, player());
    }

    @Test
    void protobufAndJsonShareStateMutationReceiptAndAuthenticationContract() throws Exception {
        HttpResponse<byte[]> stateResponse = binary("004001", Empty.getDefaultInstance().toByteArray(), id());
        assertEquals(200, stateResponse.statusCode());
        assertTrue(stateResponse.headers().firstValue("Content-Type").orElse("").startsWith("application/x-protobuf"));
        BattlePassStateProto state = BattlePassStateProto.parseFrom(stateResponse.body());
        assertEquals(state(), jsonMessage(state));
        String requestId = id();
        byte[] request = BattlePassPremiumRequestProto.newBuilder().setPassId(state.getPassId()).build().toByteArray();
        HttpResponse<byte[]> response = binary("004003", request, requestId);
        assertEquals(200, response.statusCode());
        assertEquals(requestId, response.headers().firstValue("X-Request-Id").orElseThrow());
        BattlePassMutationResponseProto mutation = BattlePassMutationResponseProto.parseFrom(response.body());
        assertEquals(jsonMessage(mutation), json(post("004003", pass(jsonMessage(state)), requestId), 200));
        error(post("004003", pass(jsonMessage(state)), "tiny"), 400, "REQUEST_ID_REQUIRED");
        error(post("004001", "{\"player_id\":\"another-player\"}", id()), 400, "INVALID_JSON");
        token = null;
        for (String route : List.of("004001", "004002", "004003", "004004")) {
            error(post(route, "{}", id()), 401, "AUTH_INVALID");
        }
    }

    private Struct state() throws Exception { return json(post("004001", "{}", id()), 200); }
    private Struct player() throws Exception { return json(post("002007", "{}", id()), 200); }
    private void buyXp() throws Exception { json(post("003002", purchase("static:xp_gold"), id()), 200); }
    private Struct claimQuest(String key) throws Exception {
        Struct current = state();
        return json(post("004004", questClaim(current, text(quest(current, key), "quest_id")), id()), 200);
    }

    private void reauthenticate() throws Exception {
        Struct response = json(post("001002", "{\"device_id\":\"" + text(login, "device_id") + "\"}", id()), 200);
        token = text(response, "access_token");
    }

    private void setResources(long gold, long gems, long xp) {
        dynamo.updateItem(request -> request.tableName(table)
                .key(Map.of("id", string("player:" + text(login, "player_id"))))
                .updateExpression("SET #resources = :resources, #version = #version + :one")
                .expressionAttributeNames(Map.of("#resources", "resources", "#version", "version"))
                .expressionAttributeValues(Map.of(":resources", resourceAttribute(gold, gems, xp), ":one", numeric(1))));
    }

    private static AttributeValue resourceAttribute(long gold, long gems, long xp) {
        return AttributeValue.builder().m(Map.of("gold", numeric(gold), "gem", numeric(gems), "xp", numeric(xp))).build();
    }
    private static AttributeValue string(String value) { return AttributeValue.builder().s(value).build(); }
    private static AttributeValue numeric(long value) { return AttributeValue.builder().n(Long.toString(value)).build(); }

    private HttpRequest request(String path, String body, String requestId) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + "/api/" + path))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                .header("X-Request-Id", requestId).POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return builder.build();
    }

    private HttpResponse<String> post(String path, String body, String requestId) throws Exception {
        return http.send(request(path, body, requestId), HttpResponse.BodyHandlers.ofString());
    }

    private List<HttpResponse<String>> concurrent(List<HttpRequest> requests) throws Exception {
        List<CompletableFuture<HttpResponse<String>>> calls = new ArrayList<>();
        for (HttpRequest request : requests) calls.add(http.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
        List<HttpResponse<String>> responses = new ArrayList<>();
        for (CompletableFuture<HttpResponse<String>> call : calls) responses.add(call.get(20, TimeUnit.SECONDS));
        return responses;
    }

    private HttpResponse<byte[]> binary(String path, byte[] body, String requestId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + "/api/" + path))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/x-protobuf")
                .header("X-Request-Id", requestId).POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static Struct json(HttpResponse<String> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        Struct.Builder value = Struct.newBuilder();
        JsonFormat.parser().merge(response.body(), value);
        return value.build();
    }

    private static Struct jsonMessage(Message message) throws Exception {
        Struct.Builder value = Struct.newBuilder();
        JsonFormat.parser().merge(JsonFormat.printer().preservingProtoFieldNames().includingDefaultValueFields().print(message), value);
        return value.build();
    }

    private static void error(HttpResponse<String> response, int status, String code) throws Exception {
        assertEquals(code, text(json(response, status), "code"));
    }

    private static Struct object(Struct value, String field) { return value.getFieldsOrThrow(field).getStructValue(); }
    private static String text(Struct value, String field) { return value.getFieldsOrThrow(field).getStringValue(); }
    private static int number(Struct value, String field) { return (int) value.getFieldsOrThrow(field).getNumberValue(); }
    private static boolean bool(Struct value, String field) { return value.getFieldsOrThrow(field).getBoolValue(); }
    private static List<Value> list(Struct value, String field) { return value.getFieldsOrThrow(field).getListValue().getValuesList(); }
    private static Struct quest(Struct state, String key) {
        return list(state, "quests").stream().map(Value::getStructValue)
                .filter(value -> text(value, "quest_id").startsWith(key + ":")).findFirst().orElseThrow();
    }
    private static Struct node(Struct state, String nodeId) {
        for (Value milestone : list(state, "milestones")) {
            for (String track : List.of("free", "premium")) {
                Struct value = object(milestone.getStructValue(), track);
                if (text(value, "node_id").equals(nodeId)) return value;
            }
        }
        throw new AssertionError("Missing reward node " + nodeId);
    }
    private static long resource(Struct value, String kind) { return quantity(value, "resources", kind); }
    private static long item(Struct value, String kind) { return quantity(value, "items", kind); }
    private static long quantity(Struct value, String field, String kind) {
        return list(value, field).stream().map(Value::getStructValue)
                .filter(resource -> text(resource, "resource_id").equals(kind))
                .mapToLong(resource -> Long.parseLong(text(resource, "quantity"))).findFirst().orElseThrow();
    }
    private static String id() { return UUID.randomUUID().toString(); }
    private static String pass(Struct state) { return "{\"pass_id\":\"" + text(state, "pass_id") + "\"}"; }
    private static String nodeClaim(Struct state, String node) {
        return "{\"pass_id\":\"" + text(state, "pass_id") + "\",\"node_id\":\"" + node + "\"}";
    }
    private static String questClaim(Struct state, String quest) {
        return "{\"pass_id\":\"" + text(state, "pass_id") + "\",\"quest_id\":\"" + quest + "\"}";
    }
    private static String purchase(String offer) { return "{\"id\":\"" + offer + "\",\"amount\":1}"; }

    private static final class MutableClock extends Clock {
        private volatile Instant now;
        private MutableClock(Instant now) { this.now = now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}

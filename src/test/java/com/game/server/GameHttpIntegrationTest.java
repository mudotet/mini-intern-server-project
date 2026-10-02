package com.game.server;

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import com.game.server.proto.CommonLoginContract.CommonLoginRequestProto;
import com.game.server.proto.CommonLoginContract.CommonLoginResponseProto;
import com.game.server.proto.ShopPurchaseRequest.ShopPurchaseRequestProto;
import com.game.server.proto.ShopPurchaseResponse.ShopPurchaseResponseProto;
import com.game_server.general.app.helper.JwtHelper;
import com.game_server.general.dao.GameDao;
import com.game_server.general.service.SessionService;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
class GameHttpIntegrationTest {
    private final String table = "mini_test_" + UUID.randomUUID().toString().replace("-", "");
    private final MutableClock clock = new MutableClock(Instant.parse("2026-10-02T06:00:00Z"));
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
        dynamo = DynamoDbClient.builder().endpointOverride(URI.create(System.getenv().getOrDefault("IT_DYNAMODB_ENDPOINT", "http://localhost:8000")))
                .region(Region.US_EAST_1).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"))).build();
        redis = new JedisPooled(System.getenv().getOrDefault("IT_REDIS_HOST", "localhost"), Integer.parseInt(System.getenv().getOrDefault("IT_REDIS_PORT", "6379")));
        assertEquals("PONG", redis.ping());
        dao = new GameDao(dynamo, table);
        dao.prepare();
        jwt = new JwtHelper(UUID.randomUUID().toString() + UUID.randomUUID(), clock);
        sessions = new SessionService(redis, jwt, table, clock);
        bind();
        login = json(post("/api/001002", "{}", id()), 200);
        token = text(login, "access_token");
    }

    private void bind() throws Exception {
        actors = ActorSystem.create("test-" + table);
        binding = Http.get(actors).newServerAt("127.0.0.1", 0).bind(Main.handlers(dao, redis, jwt, sessions, clock).routes())
                .toCompletableFuture().get(20, TimeUnit.SECONDS);
        base = "http://127.0.0.1:" + binding.localAddress().getPort();
    }

    @AfterEach
    void stop() throws Exception {
        try {
            if (binding != null) binding.unbind().toCompletableFuture().get(10, TimeUnit.SECONDS);
            if (actors != null) {
                actors.terminate();
                actors.getWhenTerminated().toCompletableFuture().get(10, TimeUnit.SECONDS);
            }
        } finally {
            try {
                if (redis != null) {
                    for (String key : redis.keys(table + ":*")) redis.del(key);
                    redis.close();
                }
            } finally {
                if (dynamo != null) {
                    try { dynamo.deleteTable(request -> request.tableName(table)); }
                    catch (software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException ignored) { }
                    finally { dynamo.close(); }
                }
            }
        }
    }

    @Test
    void initLoginPlayerAndShops() throws Exception {
        assertEquals(200, post("/api/001003", "{}", id()).statusCode());
        Struct player = json(post("/api/002007", "{}", id()), 200);
        assertEquals(text(login, "player_id"), text(player, "player_id"));
        assertEquals(1000, resource(player, "gold"));
        assertEquals(100, resource(player, "gem"));
        Struct shop = json(post("/api/003004", "{}", id()), 200);
        assertEquals(6, list(shop, "slots").size());
        Struct daily = json(post("/api/003005", "{}", id()), 200);
        assertEquals(3, list(daily, "slots").size());
        assertEquals(daily, json(post("/api/003005", "{}", id()), 200));
    }

    @Test
    void loginReplayAndCollision() throws Exception {
        String requestId = id();
        Struct first = json(post("/api/001002", "{}", requestId), 200);
        assertEquals(first, json(post("/api/001002", "{}", requestId), 200));
        error(post("/api/001002", "{\"device_id\":\"different-device\"}", requestId), 409, "REQUEST_ID_REUSED");
    }

    @Test
    void protobufLoginAndPurchaseReplayAcrossTransports() throws Exception {
        byte[] request = CommonLoginRequestProto.getDefaultInstance().toByteArray();
        HttpResponse<byte[]> response = binary("/api/001002", request, id());
        assertEquals(200, response.statusCode());
        CommonLoginResponseProto identity = CommonLoginResponseProto.parseFrom(response.body());
        token = identity.getAccessToken();
        String requestId = id();
        Struct receipt = json(post("/api/003002", purchase("static:xp_gold"), requestId), 200);
        HttpResponse<byte[]> replay = binary("/api/003002", ShopPurchaseRequestProto.newBuilder().setId("static:xp_gold").setAmount(1).build().toByteArray(), requestId);
        assertEquals(200, replay.statusCode());
        Struct.Builder decoded = Struct.newBuilder();
        JsonFormat.parser().merge(JsonFormat.printer().preservingProtoFieldNames().includingDefaultValueFields().print(ShopPurchaseResponseProto.parseFrom(replay.body())), decoded);
        assertEquals(receipt, decoded.build());
        assertEquals(900, resource(json(get("/api/info/resources"), 200), "gold"));
    }

    @Test
    void concurrentSameRequestDebitsOnce() throws Exception {
        String requestId = id();
        List<CompletableFuture<HttpResponse<String>>> calls = new ArrayList<>();
        for (int i = 0; i < 4; i++) calls.add(http.sendAsync(request("/api/003002", purchase("static:xp_gold"), requestId), HttpResponse.BodyHandlers.ofString()));
        Struct expected = json(calls.get(0).get(20, TimeUnit.SECONDS), 200);
        for (CompletableFuture<HttpResponse<String>> call : calls) assertEquals(expected, json(call.get(20, TimeUnit.SECONDS), 200));
        assertEquals(900, resource(json(get("/api/info/resources"), 200), "gold"));
        assertEquals(100, resource(json(get("/api/info/resources"), 200), "xp"));
        error(post("/api/003002", purchase("static:other"), requestId), 409, "REQUEST_ID_REUSED");
    }

    @Test
    void insufficientFundsNeverRefills() throws Exception {
        for (int i = 0; i < 10; i++) json(post("/api/003002", purchase("static:xp_gold"), id()), 200);
        error(post("/api/003002", purchase("static:xp_gold"), id()), 409, "INSUFFICIENT_FUNDS");
        assertEquals(0, resource(json(post("/api/002007", "{}", id()), 200), "gold"));
        error(post("/api/003002", "{\"id\":\"static:xp_gold\",\"amount\":2}", id()), 400, "INVALID_PURCHASE");
    }

    @Test
    void competingLastGoldHasOneWinner() throws Exception {
        for (int i = 0; i < 9; i++) json(post("/api/003002", purchase("static:xp_gold"), id()), 200);
        CompletableFuture<HttpResponse<String>> first = http.sendAsync(request("/api/003002", purchase("static:xp_gold"), id()), HttpResponse.BodyHandlers.ofString());
        CompletableFuture<HttpResponse<String>> second = http.sendAsync(request("/api/003002", purchase("static:xp_gold"), id()), HttpResponse.BodyHandlers.ofString());
        List<Integer> statuses = List.of(first.get(20, TimeUnit.SECONDS).statusCode(), second.get(20, TimeUnit.SECONDS).statusCode());
        assertEquals(1, statuses.stream().filter(status -> status == 200).count());
        assertEquals(1, statuses.stream().filter(status -> status == 409).count());
        assertEquals(0, resource(json(get("/api/info/resources"), 200), "gold"));
    }

    @Test
    void dailyLimitTermsAndReceiptPersist() throws Exception {
        Struct daily = json(post("/api/003005", "{}", id()), 200);
        Struct offer = list(daily, "slots").stream().map(value -> value.getStructValue().getFieldsOrThrow("offer").getStructValue())
                .filter(value -> text(value, "package_id").equals("xp_gold")).findFirst()
                .orElseGet(() -> list(daily, "slots").get(0).getStructValue().getFieldsOrThrow("offer").getStructValue());
        String offerId = text(offer, "offer_id");
        String requestId = id();
        Struct first = json(post("/api/003002", purchase(offerId), requestId), 200);
        for (int i = 0; i < 2; i++) json(post("/api/003002", purchase(offerId), id()), 200);
        error(post("/api/003002", purchase(offerId), id()), 409, "PURCHASE_LIMIT");
        assertEquals(first, json(post("/api/003002", purchase(offerId), requestId), 200));
        Struct updated = json(post("/api/003005", "{}", id()), 200);
        Struct changed = list(updated, "slots").stream().map(value -> value.getStructValue().getFieldsOrThrow("offer").getStructValue())
                .filter(value -> text(value, "offer_id").equals(offerId)).findFirst().orElseThrow();
        assertEquals(3, changed.getFieldsOrThrow("purchased").getNumberValue());
        assertEquals(0, changed.getFieldsOrThrow("remaining").getNumberValue());
        for (String field : List.of("items", "original_price", "final_price", "discount_percent")) assertEquals(offer.getFieldsOrThrow(field), changed.getFieldsOrThrow(field));
    }

    @Test
    void vietnamMidnightExpiresOfferButNotReceipt() throws Exception {
        Struct old = json(post("/api/003005", "{}", id()), 200);
        String offer = text(list(old, "slots").get(0).getStructValue().getFieldsOrThrow("offer").getStructValue(), "offer_id");
        String requestId = id();
        Struct receipt = json(post("/api/003002", purchase(offer), requestId), 200);
        clock.now = Instant.ofEpochSecond(Long.parseLong(text(old, "expires_at")));
        Struct next = json(post("/api/003005", "{}", id()), 200);
        assertNotEquals(text(old, "cycle_start"), text(next, "cycle_start"));
        error(post("/api/003002", purchase(offer), id()), 409, "OFFER_EXPIRED");
        assertEquals(receipt, json(post("/api/003002", purchase(offer), requestId), 200));
    }

    @Test
    void invalidExpiredAndReplacedTokens() throws Exception {
        String original = token;
        token = original.substring(0, original.length() - 3) + "bad";
        error(get("/api/info/resources"), 401, "AUTH_INVALID");
        token = original;
        Struct refreshed = json(post("/api/001002", "{\"device_id\":\"" + text(login, "device_id") + "\"}", id()), 200);
        error(get("/api/info/player"), 401, "AUTH_INVALID");
        token = text(refreshed, "access_token");
        clock.now = Instant.ofEpochSecond(Long.parseLong(text(refreshed, "session_expires_at")));
        error(get("/api/info/session"), 401, "AUTH_INVALID");
    }

    @Test
    void infoIsReadOnlyAuthenticatedAndUsesCurrentPlayer() throws Exception {
        Struct player = json(get("/api/info/player?player_id=someone-else"), 200);
        assertEquals(text(login, "player_id"), text(player, "player_id"));
        Struct resources = json(get("/api/info/resources"), 200);
        Struct packages = json(get("/api/info/packages"), 200);
        assertEquals(6, list(packages, "packages").size());
        Struct session = json(get("/api/info/session"), 200);
        assertFalse(session.containsFields("access_token"));
        assertFalse(session.containsFields("session_id"));
        assertFalse(session.containsFields("device_id"));
        clock.now = clock.now.plusSeconds(60);
        Struct later = json(get("/api/info/session"), 200);
        assertEquals(Long.parseLong(text(session, "remaining_seconds")) - 60, Long.parseLong(text(later, "remaining_seconds")));
        assertEquals(resources, json(get("/api/info/resources"), 200));
        token = null;
        error(get("/api/info/player"), 401, "AUTH_INVALID");
    }

    @Test
    void restartRetainsIdentitySnapshotAndReceipt() throws Exception {
        Struct daily = json(post("/api/003005", "{}", id()), 200);
        String requestId = id();
        Struct receipt = json(post("/api/003002", purchase("static:xp_gold"), requestId), 200);
        binding.unbind().toCompletableFuture().get(10, TimeUnit.SECONDS);
        actors.terminate();
        actors.getWhenTerminated().toCompletableFuture().get(10, TimeUnit.SECONDS);
        dao = new GameDao(dynamo, table);
        sessions = new SessionService(redis, jwt, table, clock);
        bind();
        assertEquals(daily, json(post("/api/003005", "{}", id()), 200));
        assertEquals(receipt, json(post("/api/003002", purchase("static:xp_gold"), requestId), 200));
        assertEquals(text(login, "player_id"), text(json(get("/api/info/player"), 200), "player_id"));
    }

    @Test
    void malformedJsonAndRequestIdAreRejected() throws Exception {
        error(post("/api/001002", "{\"unknown\":1}", id()), 400, "INVALID_JSON");
        error(post("/api/001002", "{}", "tiny"), 400, "REQUEST_ID_REQUIRED");
        error(post("/api/003002", "{", id()), 400, "INVALID_JSON");
    }

    @Test
    void concurrentLoginCreatesOneIdentityAndInitialResourcesOnce() throws Exception {
        String body = "{\"device_id\":\"test-device-" + UUID.randomUUID() + "\"}";
        List<CompletableFuture<HttpResponse<String>>> calls = new ArrayList<>();
        for (int i = 0; i < 4; i++) calls.add(http.sendAsync(request("/api/001002", body, id()), HttpResponse.BodyHandlers.ofString()));
        List<Struct> identities = new ArrayList<>();
        for (CompletableFuture<HttpResponse<String>> call : calls) {
            HttpResponse<String> response = call.get(20, TimeUnit.SECONDS);
            for (int attempt = 0; response.statusCode() == 409 && attempt < 10; attempt++) {
                error(response, 409, "RETRY");
                response = http.send(response.request(), HttpResponse.BodyHandlers.ofString());
            }
            identities.add(json(response, 200));
        }
        assertEquals(1, identities.stream().map(value -> text(value, "player_id")).distinct().count());
        assertEquals(1, identities.stream().map(value -> text(value, "account_id")).distinct().count());
        int current = 0;
        for (Struct identity : identities) {
            token = text(identity, "access_token");
            HttpResponse<String> response = get("/api/info/resources");
            if (response.statusCode() == 200) {
                current++;
                assertEquals(1000, resource(json(response, 200), "gold"));
            } else error(response, 401, "AUTH_INVALID");
        }
        assertEquals(1, current);
    }

    @Test
    void removedSessionFailsAuthenticationWithoutDeletingPlayer() throws Exception {
        for (String key : redis.keys(table + ":*")) redis.del(key);
        error(get("/api/info/session"), 401, "AUTH_INVALID");
        Struct recovered = json(post("/api/001002", "{\"device_id\":\"" + text(login, "device_id") + "\"}", id()), 200);
        assertEquals(text(login, "player_id"), text(recovered, "player_id"));
        token = text(recovered, "access_token");
        assertEquals(1000, resource(json(get("/api/info/resources"), 200), "gold"));
    }

    private HttpRequest request(String path, String body, String requestId) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json").header("X-Request-Id", requestId).POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return builder.build();
    }

    private HttpResponse<String> post(String path, String body, String requestId) throws Exception {
        return http.send(request(path, body, requestId), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15)).GET();
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> binary(String path, byte[] body, String requestId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-protobuf").header("X-Request-Id", requestId).POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private static Struct json(HttpResponse<String> response, int status) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
        Struct.Builder builder = Struct.newBuilder();
        JsonFormat.parser().merge(response.body(), builder);
        return builder.build();
    }

    private static void error(HttpResponse<String> response, int status, String code) throws Exception {
        assertEquals(code, text(json(response, status), "code"));
    }

    private static String text(Struct value, String field) { return value.getFieldsOrThrow(field).getStringValue(); }
    private static List<Value> list(Struct value, String field) { return value.getFieldsOrThrow(field).getListValue().getValuesList(); }
    private static long resource(Struct value, String name) {
        return list(value, "resources").stream().map(Value::getStructValue).filter(item -> text(item, "resource_id").equals(name))
                .mapToLong(item -> Long.parseLong(text(item, "quantity"))).findFirst().orElseThrow();
    }
    private static String id() { return UUID.randomUUID().toString(); }
    private static String purchase(String offer) { return "{\"id\":\"" + offer + "\",\"amount\":1}"; }

    private static final class MutableClock extends Clock {
        private volatile Instant now;
        private MutableClock(Instant now) { this.now = now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}

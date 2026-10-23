package com.game.server;

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
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
class BattlePassHttpIntegrationTest {
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
    private boolean questDemoEnabled = true;

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
        binding = Http.get(actors).newServerAt("127.0.0.1", 0).bind(Main.handlers(dao, redis, jwt, sessions, clock, questDemoEnabled).routes())
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

    @Test void viewAndQuestExpCarryWithoutAwardingNodes() throws Exception {
        Struct view = json(get("/api/info/battle-pass"), 200);
        assertEquals(10, list(view, "nodes").size());
        assertEquals(0, num(object(view, "progress"), "total_exp_bp"));
        String pass = text(view, "battle_pass_id");
        json(post("/api/004004", grant(pass, "quest_1", "first", 400), id()), 200);
        Struct added = json(post("/api/004004", grant(pass, "quest_1", "second", 110), id()), 200);
        Struct progress = object(added, "progress");
        assertEquals(510, num(progress, "total_exp_bp"));
        assertEquals(1, progress.getFieldsOrThrow("reached_nodes").getNumberValue());
        assertEquals(10, num(progress, "exp_into_next_node"));
        assertEquals(1000, resource(json(get("/api/info/resources"), 200), "gold"));
    }

    @Test void questGrantReceiptsAllowRepeatQuestsButPreventDuplicateExp() throws Exception {
        String pass = text(json(get("/api/info/battle-pass"),200), "battle_pass_id");
        String request = id();
        String body = grant(pass, "quest_1", "grant_1", 400);
        Struct first = json(post("/api/004004", body, request),200);
        assertEquals(first, json(post("/api/004004",body,request),200));
        String alias = id();
        assertEquals(first, json(post("/api/004004",body,alias),200));
        error(post("/api/004004",grant(pass,"quest_1","grant_2",110),alias),409,"REQUEST_ID_REUSED");
        error(post("/api/004004",grant(pass,"quest_1","grant_1",401),id()),409,"REQUEST_ID_REUSED");
        json(post("/api/004004",grant(pass,"quest_1","grant_2",110),id()),200);
        assertEquals(510, num(object(json(get("/api/info/battle-pass"),200),"progress"),"total_exp_bp"));
    }

    @Test void questExpRequiresFullNodeAndCapsWithoutOverflow() throws Exception {
        String pass = text(json(get("/api/info/battle-pass"),200),"battle_pass_id");
        Struct first = json(post("/api/004004",grant(pass,"q","g1",499),id()),200);
        assertEquals(0,object(first,"progress").getFieldsOrThrow("reached_nodes").getNumberValue());
        Struct reached = json(post("/api/004004",grant(pass,"q","g2",1),id()),200);
        assertEquals(1,object(reached,"progress").getFieldsOrThrow("reached_nodes").getNumberValue());
        Struct capped = json(post("/api/004004",grant(pass,"q","g3",Long.MAX_VALUE),id()),200);
        assertEquals(4500,num(capped,"exp_bp_granted"));
        assertEquals(5000,num(object(capped,"progress"),"total_exp_bp"));
        assertTrue(object(capped,"progress").getFieldsOrThrow("completed").getBoolValue());
        Struct complete = json(post("/api/004004",grant(pass,"q","g4",1),id()),200);
        assertEquals(0,num(complete,"exp_bp_granted"));
        error(post("/api/004004",grant(pass,"q","g5",0),id()),400,"INVALID_QUEST_GRANT");
        error(post("/api/004004",grant(pass,"q","g6",-1),id()),400,"INVALID_QUEST_GRANT");
    }

    @Test void premiumPurchaseChargesOnceAndUnlocksEarlierReachedNodes() throws Exception {
        String pass = text(json(get("/api/info/battle-pass"),200),"battle_pass_id");
        json(post("/api/004004",grant(pass,"q","g1",1500),id()),200);
        assertFalse(list(json(get("/api/info/battle-pass"),200),"nodes").get(1).getStructValue().getFieldsOrThrow("claimable").getBoolValue());
        String purchaseId = id();
        String body = passBody(pass);
        Struct bought = json(post("/api/004002",body,purchaseId),200);
        assertEquals(0,resource(bought,"gem"));
        assertTrue(object(bought,"progress").getFieldsOrThrow("premium_owned").getBoolValue());
        assertEquals(0,list(object(bought,"progress"),"claimed_node_ids").size());
        assertEquals(bought,json(post("/api/004002",body,purchaseId),200));
        assertEquals(0,resource(json(post("/api/004002",body,id()),200),"gem"));
        assertTrue(list(json(get("/api/info/battle-pass"),200),"nodes").get(1).getStructValue().getFieldsOrThrow("claimable").getBoolValue());
        error(post("/api/004004",grant(pass,"q","g2",1),purchaseId),409,"REQUEST_ID_REUSED");
    }

    @Test void claimsRequireFullExpAndGiveExactlyOneReward() throws Exception {
        String pass = text(json(get("/api/info/battle-pass"),200),"battle_pass_id");
        json(post("/api/004004",grant(pass,"q","g1",499),id()),200);
        error(post("/api/004003",claim(pass,"node_1"),id()),409,"NODE_NOT_REACHED");
        json(post("/api/004004",grant(pass,"q","g2",1),id()),200);
        String requestId = id();
        Struct claimed = json(post("/api/004003",claim(pass,"node_1"),requestId),200);
        assertEquals("gold",text(object(object(claimed,"reward"),"resource"),"resource_id"));
        assertEquals(100,num(object(object(claimed,"reward"),"resource"),"quantity"));
        assertEquals(1,object(claimed,"reward").getFieldsCount());
        assertEquals(1100,resource(claimed,"gold"));
        assertEquals(500,num(object(claimed,"progress"),"total_exp_bp"));
        assertEquals(claimed,json(post("/api/004003",claim(pass,"node_1"),requestId),200));
        error(post("/api/004003",claim(pass,"node_1"),id()),409,"NODE_ALREADY_CLAIMED");
        error(post("/api/004003",claim(pass,"node_2"),requestId),409,"REQUEST_ID_REUSED");
        json(post("/api/004004",grant(pass,"q","g3",500),id()),200);
        error(post("/api/004003",claim(pass,"node_2"),id()),409,"PREMIUM_REQUIRED");
        json(post("/api/004002",passBody(pass),id()),200);
        assertEquals(1600,resource(json(post("/api/004003",claim(pass,"node_2"),id()),200),"gold"));
    }

    @Test void heroesAndRarityCompensationPersistAcrossPasses() throws Exception {
        String pass = text(json(get("/api/info/battle-pass"),200),"battle_pass_id");
        json(post("/api/004004",grant(pass,"q","g1",5000),id()),200);
        json(post("/api/004002",passBody(pass),id()),200);
        Struct basic = json(post("/api/004003",claim(pass,"node_3"),id()),200);
        assertEquals("hero_scout",text(object(basic,"reward"),"hero_id"));
        assertEquals(1,object(basic,"reward").getFieldsCount());
        json(post("/api/004003",claim(pass,"node_4"),id()),200);
        json(post("/api/004003",claim(pass,"node_6"),id()),200);
        json(post("/api/004003",claim(pass,"node_10"),id()),200);
        Struct before = json(get("/api/info/battle-pass"),200);
        assertEquals(4,list(before,"owned_heroes").size());
        json(post("/api/004003",claim(pass,"node_1"),id()),200);
        json(post("/api/004003",claim(pass,"node_2"),id()),200);
        json(post("/api/004003",claim(pass,"node_7"),id()),200);
        for (int i=0;i<6;i++) json(post("/api/003002",purchase("static:gem_large"),id()),200);
        clock.now = Instant.parse("2026-10-30T17:00:00Z");
        // Sessions still expire normally; recover the same Player after advancing time.
        login = json(post("/api/001002","{\"device_id\":\""+text(login,"device_id")+"\"}",id()),200);
        token = text(login,"access_token");
        Struct after = json(get("/api/info/battle-pass"),200);
        String next = text(after,"battle_pass_id");
        assertNotEquals(pass,next);
        assertEquals(0,num(object(after,"progress"),"total_exp_bp"));
        assertFalse(object(after,"progress").getFieldsOrThrow("premium_owned").getBoolValue());
        assertEquals(4,list(after,"owned_heroes").size());
        error(post("/api/004003",claim(pass,"node_1"),id()),409,"BATTLE_PASS_EXPIRED");
        json(post("/api/004004",grant(next,"q","g2",5000),id()),200);
        Struct compensation = json(post("/api/004003",claim(next,"node_3"),id()),200);
        assertEquals(100,resource(compensation,"gem"));
        assertEquals(10,num(object(object(compensation,"reward"),"resource"),"quantity"));
        assertEquals(1,object(compensation,"reward").getFieldsCount());
        json(post("/api/004002",passBody(next),id()),200);
        Struct epic = json(post("/api/004003",claim(next,"node_4"),id()),200);
        assertEquals(50,num(object(object(epic,"reward"),"resource"),"quantity"));
        Struct legendary = json(post("/api/004003",claim(next,"node_6"),id()),200);
        assertEquals(100,num(object(object(legendary,"reward"),"resource"),"quantity"));
        assertEquals(150,resource(legendary,"gem"));
        assertEquals(4,list(legendary,"owned_heroes").size());
    }

    @Test void concurrentSameRequestClaimsReplayAndDifferentClaimsAwardOnce() throws Exception {
        String pass = text(json(get("/api/info/battle-pass"),200),"battle_pass_id");
        json(post("/api/004004",grant(pass,"q","g1",1000),id()),200);
        String requestId = id();
        List<CompletableFuture<HttpResponse<String>>> calls = new ArrayList<>();
        for(int i=0;i<4;i++) calls.add(http.sendAsync(request("/api/004003",claim(pass,"node_1"),requestId),HttpResponse.BodyHandlers.ofString()));
        Struct first = json(calls.get(0).get(20,TimeUnit.SECONDS),200);
        for(var call:calls) assertEquals(first,json(call.get(20,TimeUnit.SECONDS),200));
        calls.clear();
        for(int i=0;i<4;i++) calls.add(http.sendAsync(request("/api/004002",passBody(pass),id()),HttpResponse.BodyHandlers.ofString()));
        for(var call:calls) assertEquals(0,resource(json(call.get(20,TimeUnit.SECONDS),200),"gem"));
        calls.clear();
        for(int i=0;i<4;i++) calls.add(http.sendAsync(request("/api/004003",claim(pass,"node_2"),id()),HttpResponse.BodyHandlers.ofString()));
        int successful=0;
        for(var call:calls) {
            var result=call.get(20,TimeUnit.SECONDS);
            if(result.statusCode()==200) successful++;
            else error(result,409,"NODE_ALREADY_CLAIMED");
        }
        assertEquals(1,successful);
        assertEquals(1600,resource(json(get("/api/info/resources"),200),"gold"));
    }

    @Test void concurrentRepeatGrantIdsAndShopUpdatesPreserveState() throws Exception {
        String pass = text(json(get("/api/info/battle-pass"),200),"battle_pass_id");
        Struct daily=json(post("/api/003005","{}",id()),200);
        List<CompletableFuture<HttpResponse<String>>> calls=new ArrayList<>();
        for(int i=0;i<4;i++) calls.add(http.sendAsync(request("/api/004004",grant(pass,"q","same",400),id()),HttpResponse.BodyHandlers.ofString()));
        for(var call:calls) json(call.get(20,TimeUnit.SECONDS),200);
        assertEquals(400,num(object(json(get("/api/info/battle-pass"),200),"progress"),"total_exp_bp"));
        calls.clear();
        for(int i=0;i<4;i++) calls.add(http.sendAsync(request("/api/004004",grant(pass,"q","next"+i,100),id()),HttpResponse.BodyHandlers.ofString()));
        calls.add(http.sendAsync(request("/api/003002",purchase("static:xp_gold"),id()),HttpResponse.BodyHandlers.ofString()));
        for(var call:calls) json(call.get(20,TimeUnit.SECONDS),200);
        assertEquals(800,num(object(json(get("/api/info/battle-pass"),200),"progress"),"total_exp_bp"));
        Struct resources=json(get("/api/info/resources"),200);
        assertEquals(900,resource(resources,"gold"));
        assertEquals(100,resource(resources,"xp"));
        assertEquals(daily,json(post("/api/003005","{}",id()),200));
        json(post("/api/004002",passBody(pass),id()),200);
        json(post("/api/004004",grant(pass,"q","finish",200),id()),200);
        var claimCall=http.sendAsync(request("/api/004003",claim(pass,"node_2"),id()),HttpResponse.BodyHandlers.ofString());
        var shopCall=http.sendAsync(request("/api/003002",purchase("static:xp_gold"),id()),HttpResponse.BodyHandlers.ofString());
        json(claimCall.get(20,TimeUnit.SECONDS),200);
        json(shopCall.get(20,TimeUnit.SECONDS),200);
        assertEquals(1300,resource(json(get("/api/info/resources"),200),"gold"));
        assertEquals(200,resource(json(get("/api/info/resources"),200),"xp"));
    }

    @Test void demoRouteMustBeEnabledAndEveryBattlePassActionRequiresAuth() throws Exception {
        String pass=text(json(get("/api/info/battle-pass"),200),"battle_pass_id");
        String saved=token;
        token=null;
        error(get("/api/info/battle-pass"),401,"AUTH_INVALID");
        error(post("/api/004004",grant(pass,"q","g",500),id()),401,"AUTH_INVALID");
        error(post("/api/004002",passBody(pass),id()),401,"AUTH_INVALID");
        error(post("/api/004003",claim(pass,"node_1"),id()),401,"AUTH_INVALID");
        token=saved;
        error(post("/api/004002",passBody(pass),"short"),400,"REQUEST_ID_REQUIRED");
        error(post("/api/004003",claim(pass,"unknown"),id()),404,"NODE_NOT_FOUND");
        error(post("/api/004004","{",id()),400,"INVALID_JSON");
        disconnect(); questDemoEnabled=false; bind();
        error(post("/api/004004",grant(pass,"q","g",500),id()),404,"ROUTE_NOT_FOUND");
        json(get("/api/info/battle-pass"),200);
    }

    @Test void insufficientGemsDoesNotChangeProgressOrResources() throws Exception {
        String pass=text(json(get("/api/info/battle-pass"),200),"battle_pass_id");
        json(post("/api/003002",purchase("static:gold_small"),id()),200);
        Struct before=json(get("/api/info/battle-pass"),200);
        assertEquals(95,resource(before,"gem"));
        error(post("/api/004002",passBody(pass),id()),409,"INSUFFICIENT_FUNDS");
        assertEquals(before,json(get("/api/info/battle-pass"),200));
    }

    @Test void protobufRetriesAndRestartPreserveProgressHeroesAndReceipts() throws Exception {
        String pass=text(json(get("/api/info/battle-pass"),200),"battle_pass_id");
        json(post("/api/004004",grant(pass,"q","g",1500),id()),200);
        String requestId=id();
        Struct claimed=json(post("/api/004003",claim(pass,"node_3"),requestId),200);
        var body=com.game.server.proto.BattlePassClaimContract.BattlePassClaimRequestProto.newBuilder().setBattlePassId(pass).setNodeId("node_3").build();
        var replay=binary("/api/004003",body.toByteArray(),requestId);
        assertEquals(200,replay.statusCode());
        var decoded=com.game.server.proto.BattlePassClaimContract.BattlePassClaimResponseProto.parseFrom(replay.body());
        Struct.Builder expected=Struct.newBuilder();
        JsonFormat.parser().merge(JsonFormat.printer().preservingProtoFieldNames().includingDefaultValueFields().print(decoded),expected);
        assertEquals(claimed,expected.build());
        Struct before=json(get("/api/info/battle-pass"),200);
        disconnect(); dao=new GameDao(dynamo,table); sessions=new SessionService(redis,jwt,table,clock); bind();
        assertEquals(before,json(get("/api/info/battle-pass"),200));
        assertEquals(claimed,json(post("/api/004003",claim(pass,"node_3"),requestId),200));
        error(post("/api/004003",claim(pass,"node_3"),id()),409,"NODE_ALREADY_CLAIMED");
    }

    @Test void exactExpiryResetsProgressButReplaysOldCommittedOperations() throws Exception {
        String pass=text(json(get("/api/info/battle-pass"),200),"battle_pass_id");
        String requestId=id(); String body=grant(pass,"q","old_grant",510);
        Struct old=json(post("/api/004004",body,requestId),200);
        json(post("/api/004002",passBody(pass),id()),200);
        clock.now=Instant.parse("2026-10-30T16:59:59Z");
        login=json(post("/api/001002","{\"device_id\":\""+text(login,"device_id")+"\"}",id()),200); token=text(login,"access_token");
        assertEquals(pass,text(json(get("/api/info/battle-pass"),200),"battle_pass_id"));
        json(post("/api/004003",claim(pass,"node_1"),id()),200);
        clock.now=clock.now.plusSeconds(1);
        Struct next=json(get("/api/info/battle-pass"),200);
        assertNotEquals(pass,text(next,"battle_pass_id"));
        assertEquals(0,num(object(next,"progress"),"total_exp_bp"));
        assertFalse(object(next,"progress").getFieldsOrThrow("premium_owned").getBoolValue());
        assertEquals(1100,resource(next,"gold"));
        assertEquals(old,json(post("/api/004004",body,requestId),200));
        assertEquals(old,json(post("/api/004004",body,id()),200));
        error(post("/api/004004",grant(pass,"q","new_late",500),id()),409,"BATTLE_PASS_EXPIRED");
        assertEquals(next,json(get("/api/info/battle-pass"),200));
    }

    @Test void premiumPurchaseRacingShopSpendingGemsHasOneValidWinner() throws Exception {
        String pass=text(json(get("/api/info/battle-pass"),200),"battle_pass_id");
        var premium=http.sendAsync(request("/api/004002",passBody(pass),id()),HttpResponse.BodyHandlers.ofString());
        var shop=http.sendAsync(request("/api/003002",purchase("static:gold_small"),id()),HttpResponse.BodyHandlers.ofString());
        var premiumResult=premium.get(20,TimeUnit.SECONDS);
        var shopResult=shop.get(20,TimeUnit.SECONDS);
        Struct view=json(get("/api/info/battle-pass"),200);
        if(premiumResult.statusCode()==200) {
            error(shopResult,409,"INSUFFICIENT_FUNDS");
            assertEquals(0,resource(view,"gem"));
            assertEquals(1000,resource(view,"gold"));
            assertTrue(object(view,"progress").getFieldsOrThrow("premium_owned").getBoolValue());
        } else {
            error(premiumResult,409,"INSUFFICIENT_FUNDS");
            json(shopResult,200);
            assertEquals(95,resource(view,"gem"));
            assertEquals(1100,resource(view,"gold"));
            assertFalse(object(view,"progress").getFieldsOrThrow("premium_owned").getBoolValue());
        }
    }

    private void disconnect() throws Exception {
        if(binding!=null) binding.unbind().toCompletableFuture().get(10,TimeUnit.SECONDS);
        if(actors!=null) { actors.terminate(); actors.getWhenTerminated().toCompletableFuture().get(10,TimeUnit.SECONDS); }
        binding=null; actors=null;
    }

    private static String claim(String pass, String node) { return "{\"battle_pass_id\":\""+pass+"\",\"node_id\":\""+node+"\"}"; }

    private static String passBody(String pass) { return "{\"battle_pass_id\":\""+pass+"\"}"; }

    private static Struct object(Struct value, String field) { return value.getFieldsOrThrow(field).getStructValue(); }
    private static long num(Struct value, String field) { return Long.parseLong(text(value, field)); }
    private static String grant(String pass, String quest, String grant, long exp) {
        return "{\"battle_pass_id\":\"" + pass + "\",\"quest_id\":\"" + quest + "\",\"grant_id\":\"" + grant + "\",\"exp_bp\":\"" + exp + "\"}";
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

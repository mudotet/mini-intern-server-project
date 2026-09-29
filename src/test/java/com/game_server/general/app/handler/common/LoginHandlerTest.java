package com.game_server.general.app.handler.common;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpResponse;
import akka.util.ByteString;
import com.game.server.proto.CommonInitContract;
import com.game.server.proto.CommonLoginContract;
import com.game_server.general.app.helper.JwtHelper;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPooled;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoginHandlerTest {
    @Test
    void initReturnsPublicMetadataWithoutStorage() throws Exception {
        ActorSystem system = ActorSystem.create("init-test");
        try {
            HttpResponse result = new InitHandler().processHttp(CommonInitContract.CommonInitRequest.getDefaultInstance().toByteArray());
            assertEquals(200, result.status().intValue());
            ByteString body = result.entity().getDataBytes()
                    .runFold(ByteString.fromArray(new byte[0]), ByteString::concat, system)
                    .toCompletableFuture().get(5, TimeUnit.SECONDS);
            CommonInitContract.CommonInitResponseProto init = CommonInitContract.CommonInitResponseProto.parseFrom(body.toArray());
            assertTrue(init.getServerTime() > 0);
            assertFalse(init.getContractVersion().isBlank());
            assertFalse(init.getConfigurationVersion().isBlank());
            assertFalse(init.getMinimumClientVersion().isBlank());
            assertFalse(init.getMaintenance());
        } finally {
            system.terminate();
        }
    }

    @Test
    void createsAndRecoversDeviceWithoutTrustingPlayerId() throws Exception {
        DynamoDbClient dynamodb = mock(DynamoDbClient.class);
        JedisPooled redis = mock(JedisPooled.class);
        JwtHelper jwt = new JwtHelper("0123456789abcdef0123456789abcdef");
        LoginHandler handler = new LoginHandler(dynamodb, redis, jwt);
        when(dynamodb.getItem(any(GetItemRequest.class))).thenReturn(
                GetItemResponse.builder().build(),
                GetItemResponse.builder().item(Map.of("device_id", AttributeValue.fromS("known"),
                        "player_id", AttributeValue.fromS("player"))).build(),
                GetItemResponse.builder().build());
        ActorSystem system = ActorSystem.create("login-test");
        try {
            CommonLoginContract.CommonLoginResponseProto created = response(handler, system,
                    CommonLoginContract.CommonLoginRequestProto.getDefaultInstance());
            assertFalse(created.getDeviceId().isBlank());
            assertFalse(created.getPlayerId().isBlank());
            assertFalse(created.getAccessToken().isBlank());
            assertTrue(created.getSessionExpiresAt() > System.currentTimeMillis() / 1000);
            verify(dynamodb).putItem(any(PutItemRequest.class));

            CommonLoginContract.CommonLoginResponseProto recovered = response(handler, system,
                    CommonLoginContract.CommonLoginRequestProto.newBuilder().setDeviceId("known").setPlayerId("player").build());
            assertEquals("player", recovered.getPlayerId());
            assertEquals("known", recovered.getDeviceId());
            verify(redis).setex(eq("session:known"), eq(43200L), anyString());

            HttpResponse mismatch = handler.processHttp(CommonLoginContract.CommonLoginRequestProto.newBuilder()
                    .setDeviceId("known").setPlayerId("other").build().toByteArray());
            assertEquals(409, mismatch.status().intValue());
            assertEquals(400, handler.processHttp(new byte[] {(byte) 0xff}).status().intValue());
            CommonLoginContract.CommonLoginResponseProto suppliedDevice = response(handler, system,
                    CommonLoginContract.CommonLoginRequestProto.newBuilder().setDeviceId("unknown").build());
            assertEquals("unknown", suppliedDevice.getDeviceId());
            assertFalse(suppliedDevice.getPlayerId().isBlank());
        } finally {
            system.terminate();
        }
    }

    private CommonLoginContract.CommonLoginResponseProto response(LoginHandler handler, ActorSystem system,
            CommonLoginContract.CommonLoginRequestProto request) throws Exception {
        HttpResponse result = handler.processHttp(request.toByteArray());
        assertEquals(200, result.status().intValue());
        ByteString body = result.entity().getDataBytes().runFold(ByteString.fromArray(new byte[0]), ByteString::concat, system)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        return CommonLoginContract.CommonLoginResponseProto.parseFrom(body.toArray());
    }
}

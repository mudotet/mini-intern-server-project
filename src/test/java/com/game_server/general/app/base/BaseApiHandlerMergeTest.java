package com.game_server.general.app.base;

import akka.actor.ActorSystem;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.stream.Materializer;
import akka.stream.SystemMaterializer;
import com.game.server.proto.CommonLoginContract.CommonLoginRequestProto;
import com.game.server.proto.CommonLoginContract.CommonLoginResponseProto;
import com.game.server.proto.ErrorContract.AuthErrorProto;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BaseApiHandlerMergeTest {
    private static ActorSystem system;
    private static Materializer materializer;

    @BeforeAll
    static void startActorSystem() {
        system = ActorSystem.create("base-api-handler-merge-test");
        materializer = SystemMaterializer.get(system).materializer();
    }

    @AfterAll
    static void stopActorSystem() throws Exception {
        system.terminate();
        system.getWhenTerminated().toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    @Test
    void byteHandlerWorksThroughAsyncProviderWithProtobufAndRequestId() throws Exception {
        ByteHandler handler = new ByteHandler();
        CommonLoginRequestProto input = CommonLoginRequestProto.newBuilder().setDeviceId("device-1").build();

        HttpResponse response = route(handler, request(handler, input.toByteArray()));

        assertEquals(200, response.status().intValue());
        assertEquals(ApiResult.CONTENT_TYPE, response.entity().getContentType());
        assertRequestId(response);
        assertEquals("device-1", CommonLoginResponseProto.parseFrom(body(response)).getDeviceId());
    }

    @Test
    void byteHandlerKeepsBadRequestAndConflictErrorsThroughAsyncProvider() throws Exception {
        ByteHandler handler = new ByteHandler();

        HttpResponse malformed = route(handler, request(handler, new byte[] {(byte) 0xff}));
        assertEquals(400, malformed.status().intValue());
        assertRequestId(malformed);
        assertEquals("Invalid login request", AuthErrorProto.parseFrom(body(malformed)).getMessage());

        HttpResponse invalidIdentity = route(handler, request(handler,
                CommonLoginRequestProto.newBuilder().setDeviceId("invalid").build().toByteArray()));
        assertEquals(400, invalidIdentity.status().intValue());
        assertRequestId(invalidIdentity);

        HttpResponse mismatch = route(handler, request(handler,
                CommonLoginRequestProto.newBuilder().setPlayerId("mismatch").build().toByteArray()));
        assertEquals(409, mismatch.status().intValue());
        assertRequestId(mismatch);
        assertEquals("Device or player identity does not match",
                AuthErrorProto.parseFrom(body(mismatch)).getMessage());
    }

    @Test
    void asyncHandlerStillRequiresBearerBeforeProcessing() throws Exception {
        AsyncHandler handler = new AsyncHandler();

        HttpResponse response = route(handler, request(handler,
                CommonLoginRequestProto.getDefaultInstance().toByteArray()));

        assertEquals(401, response.status().intValue());
        assertRequestId(response);
        assertNull(handler.received);
    }

    @Test
    void asyncHandlerStillUsesItsOwnHookWithAuthenticatedInput() throws Exception {
        CommonLoginRequestProto input = CommonLoginRequestProto.newBuilder().setDeviceId("device-2").build();
        AsyncHandler handler = new AsyncHandler();

        HttpResponse response = route(handler, request(handler, input.toByteArray())
                .addHeader(HttpHeader.parse("Authorization", "Bearer session-1")));

        assertEquals(200, response.status().intValue());
        assertRequestId(response);
        assertEquals(input, handler.received);
        assertEquals("device-2", CommonLoginResponseProto.parseFrom(body(response)).getDeviceId());
    }

    private static HttpRequest request(BaseApiHandler handler, byte[] body) {
        String api = handler.getClass().getAnnotation(ApiHandler.class).value();
        return HttpRequest.create("/api/" + api).withMethod(HttpMethods.POST)
                .withEntity(HttpEntities.create(ApiResult.CONTENT_TYPE, body))
                .addHeader(HttpHeader.parse(BaseApiHandler.HEADER_ID, "merge-request-1"));
    }

    private static HttpResponse route(BaseApiHandler handler, HttpRequest request) throws Exception {
        return new AppHandlerProvider(List.of(handler)).processHttp(request, materializer)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
    }

    private static byte[] body(HttpResponse response) throws Exception {
        return response.entity().toStrict(5_000, materializer)
                .toCompletableFuture().get(5, TimeUnit.SECONDS).getData().toArray();
    }

    private static void assertRequestId(HttpResponse response) {
        assertEquals("merge-request-1", response.getHeader(BaseApiHandler.HEADER_ID).orElseThrow().value());
    }

    @ApiHandler(value = "merge-byte-handler", auth = false)
    private static final class ByteHandler extends BaseApiHandler {
        @Override
        protected Message process(byte[] body) throws InvalidProtocolBufferException {
            CommonLoginRequestProto input = CommonLoginRequestProto.parseFrom(body);
            if (input.getDeviceId().equals("invalid")) {
                throw new IllegalArgumentException("Invalid identity");
            }
            if (input.getPlayerId().equals("mismatch")) {
                throw new IllegalStateException("Player mismatch");
            }
            return CommonLoginResponseProto.newBuilder().setDeviceId(input.getDeviceId()).build();
        }
    }

    @ApiHandler(value = "merge-async-handler")
    private static final class AsyncHandler extends BaseApiHandler {
        private CommonLoginRequestProto received;

        @Override
        protected boolean authenticate(HttpRequest request) {
            return request.getHeader("Authorization").map(header -> header.value().startsWith("Bearer "))
                    .orElse(false);
        }

        @Override
        protected CompletionStage<ApiResult> process(HttpRequest request, Materializer materializer) {
            return request.entity().toStrict(5_000, materializer).thenApply(entity -> {
                try {
                    received = CommonLoginRequestProto.parseFrom(entity.getData().toArray());
                    return ApiResult.success(CommonLoginResponseProto.newBuilder()
                            .setDeviceId(received.getDeviceId()).build());
                } catch (InvalidProtocolBufferException exception) {
                    throw new IllegalArgumentException(exception);
                }
            });
        }
    }
}

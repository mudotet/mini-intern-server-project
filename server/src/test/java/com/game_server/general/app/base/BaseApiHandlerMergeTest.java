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
import com.game.server.proto.CommonInitContract;
import com.game.server.proto.ErrorContract.BusinessErrorProto;
import com.game_server.general.service.GameException;
import com.game_server.general.app.handler.common.InitHandler;
import com.game_server.general.app.handler.ApiCodes;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void swaggerPageSpecAndAssetsAreServedFromTheApplication() throws Exception {
        AppHandlerProvider provider = new AppHandlerProvider(List.of(new InitHandler()));
        HttpResponse page = provider.routes().handler(system).apply(HttpRequest.create("/swagger/"))
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(200, page.status().intValue());
        assertTrue(new String(body(page), java.nio.charset.StandardCharsets.UTF_8).contains("SwaggerUIBundle"));
        HttpResponse spec = provider.routes().handler(system).apply(HttpRequest.create("/swagger/openapi.json"))
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(200, spec.status().intValue());
        var document = com.google.gson.JsonParser.parseString(new String(body(spec), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals(10, document.getAsJsonObject("paths").size());
        assertEquals("bearer", document.getAsJsonObject("components").getAsJsonObject("securitySchemes")
                .getAsJsonObject("BearerAuth").get("scheme").getAsString());
        HttpResponse bundle = provider.routes().handler(system).apply(HttpRequest.create("/swagger/assets/swagger-ui-bundle.js"))
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(200, bundle.status().intValue());
        assertTrue(body(bundle).length > 1000);
    }

    @Test
    void publicInitUsesTheRuntimeRouteWithContentTypeValidationAndRequestId() throws Exception {
        AppHandlerProvider provider = new AppHandlerProvider(List.of(new InitHandler()));
        HttpRequest request = HttpRequest.create("/api/" + ApiCodes.COMMON_INIT)
                .withMethod(HttpMethods.POST)
                .withEntity(HttpEntities.create(ApiResult.CONTENT_TYPE, new byte[0]))
                .addHeader(HttpHeader.parse(BaseApiHandler.HEADER_ID, "merge-request-1"));

        HttpResponse response = provider.routes().handler(system).apply(request)
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(200, response.status().intValue());
        assertRequestId(response);
        assertEquals("1", CommonInitContract.CommonInitResponseProto.parseFrom(body(response)).getContractVersion());

        HttpResponse json = provider.routes().handler(system)
                .apply(request.withEntity(HttpEntities.create(akka.http.javadsl.model.ContentTypes.APPLICATION_JSON, "{}")))
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(200, json.status().intValue());
        assertRequestId(json);
        assertEquals(akka.http.javadsl.model.ContentTypes.APPLICATION_JSON, json.entity().getContentType());
        assertEquals("1", com.google.gson.JsonParser.parseString(new String(body(json), java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject().get("contract_version").getAsString());

        HttpResponse invalid = provider.routes().handler(system)
                .apply(request.withEntity(HttpEntities.create(akka.http.javadsl.model.ContentTypes.TEXT_PLAIN_UTF8, "{}")))
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        assertEquals(400, invalid.status().intValue());
        assertRequestId(invalid);
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
        assertEquals("INVALID_PROTOBUF", BusinessErrorProto.parseFrom(body(malformed)).getCode());

        HttpResponse invalidIdentity = route(handler, request(handler,
                CommonLoginRequestProto.newBuilder().setDeviceId("invalid").build().toByteArray()));
        assertEquals(400, invalidIdentity.status().intValue());
        assertRequestId(invalidIdentity);

        HttpResponse mismatch = route(handler, request(handler,
                CommonLoginRequestProto.newBuilder().setPlayerId("mismatch").build().toByteArray()));
        assertEquals(409, mismatch.status().intValue());
        assertRequestId(mismatch);
        assertEquals("IDENTITY_MISMATCH", BusinessErrorProto.parseFrom(body(mismatch)).getCode());
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

    @Test
    void streamedOversizedBodyReturnsClientError() throws Exception {
        InitHandler handler = new InitHandler();
        akka.stream.javadsl.Source<akka.util.ByteString, akka.NotUsed> chunks = akka.stream.javadsl.Source.from(List.of(
                akka.util.ByteString.fromArray(new byte[40000]), akka.util.ByteString.fromArray(new byte[40000])));
        HttpRequest request = HttpRequest.create("/api/" + ApiCodes.COMMON_INIT).withMethod(HttpMethods.POST)
                .withEntity(HttpEntities.createChunked(ApiResult.CONTENT_TYPE, chunks));
        HttpResponse response = route(handler, request);
        assertEquals(413, response.status().intValue());
        assertEquals("INVALID_REQUEST", BusinessErrorProto.parseFrom(body(response)).getCode());
    }

    @Test
    void stalledBodyReturnsTimeoutInsteadOfInternalError() throws Exception {
        InitHandler handler = new InitHandler();
        akka.stream.javadsl.Source<akka.util.ByteString, ?> stalled = akka.stream.javadsl.Source.<akka.util.ByteString>maybe();
        HttpRequest request = HttpRequest.create("/api/" + ApiCodes.COMMON_INIT).withMethod(HttpMethods.POST)
                .withEntity(HttpEntities.createChunked(ApiResult.CONTENT_TYPE, stalled));
        HttpResponse response = new AppHandlerProvider(List.of(handler)).routes().handler(system).apply(request)
                .toCompletableFuture().get(10, TimeUnit.SECONDS);
        assertEquals(408, response.status().intValue());
        assertEquals("INVALID_REQUEST", BusinessErrorProto.parseFrom(body(response)).getCode());
    }

    private static HttpRequest request(BaseApiHandler handler, byte[] body) {
        String api = handler.getClass().getAnnotation(ApiHandler.class).value();
        return HttpRequest.create("/api/" + api).withMethod(HttpMethods.POST)
                .withEntity(HttpEntities.create(ApiResult.CONTENT_TYPE, body))
                .addHeader(HttpHeader.parse(BaseApiHandler.HEADER_ID, "merge-request-1"));
    }

    private static HttpResponse route(BaseApiHandler handler, HttpRequest request) throws Exception {
        return new AppHandlerProvider(List.of(handler)).routes().handler(system).apply(request)
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
                throw new GameException(400, "INVALID_IDENTITY", "Invalid identity");
            }
            if (input.getPlayerId().equals("mismatch")) {
                throw new GameException(409, "IDENTITY_MISMATCH", "Player mismatch");
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

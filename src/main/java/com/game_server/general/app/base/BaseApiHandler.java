package com.game_server.general.app.base;

import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.stream.Materializer;
import com.game.server.proto.ErrorContract.BusinessErrorProto;
import com.game_server.general.dao.GameDao.Identity;
import com.game_server.general.service.GameException;
import com.game_server.general.service.SessionService;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Empty;
import com.google.protobuf.util.JsonFormat;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseApiHandler implements AppHandler {
    public static final String HEADER_ID = "X-Request-Id";
    private static final Logger LOG = LoggerFactory.getLogger(BaseApiHandler.class);
    private final SessionService sessions;

    protected BaseApiHandler() { this(null); }
    protected BaseApiHandler(SessionService sessions) { this.sessions = sessions; }

    @Override
    public final CompletionStage<HttpResponse> processHttp(HttpRequest request, Materializer materializer) {
        Objects.requireNonNull(request);
        Objects.requireNonNull(materializer);
        String requestId = request.getHeader(HEADER_ID).map(h -> h.value()).orElse("");
        boolean json = isJson(request);
        ApiHandler api = getClass().getAnnotation(ApiHandler.class);
        try {
            if (api == null) throw new IllegalStateException("Handler must declare @ApiHandler");
            boolean read = api.method().equals("GET");
            if (!request.method().value().equals(api.method())
                    || (!read && !json && !request.entity().getContentType().mediaType().equals(ApiResult.CONTENT_TYPE.mediaType())))
                throw new GameException(400, "INVALID_REQUEST", read ? "Use GET without a request body"
                        : "Use POST with application/json or application/x-protobuf");
            if (sessions == null && !verifyAuth(request, api))
                throw new GameException(401, "AUTH_INVALID", "Authentication required");
            return process(request, materializer).handle((result, error) -> {
                try {
                    return error == null ? Objects.requireNonNull(result).toResponse(requestId, json)
                            : handle(unwrap(error), requestId, json);
                } catch (Throwable conversionError) { return handle(conversionError, requestId, json); }
            });
        } catch (Throwable error) {
            return CompletableFuture.completedFuture(handle(error, requestId, json));
        }
    }

    protected CompletionStage<ApiResult> process(HttpRequest request, Materializer materializer) {
        return request.entity().withSizeLimit(65536).toStrict(5_000, materializer)
                .thenApplyAsync(entity -> {
                    ApiHandler api = getClass().getAnnotation(ApiHandler.class);
                    Identity identity = sessions != null && api.auth()
                            ? sessions.authenticate(request.getHeader("Authorization").map(h -> h.value()).orElse(null)) : null;
                    try {
                        byte[] body = entity.getData().toArray();
                        if (api.method().equals("GET") && body.length != 0)
                            throw new GameException(400, "INVALID_REQUEST", "GET information APIs do not accept a body");
                        if (isJson(request)) {
                            Message.Builder builder = jsonRequestBuilder();
                            String input = entity.getData().utf8String();
                            JsonFormat.parser().merge(input.isBlank() ? "{}" : input, builder);
                            body = builder.build().toByteArray();
                        }
                        return ApiResult.success(process(body, request, identity));
                    } catch (InvalidProtocolBufferException malformed) {
                        throw new GameException(400, isJson(request) ? "INVALID_JSON" : "INVALID_PROTOBUF", "Cannot parse request body");
                    }
                }, materializer.system().dispatchers().lookup("game-blocking-dispatcher"));
    }

    protected Message process(byte[] body, HttpRequest request, Identity identity) throws InvalidProtocolBufferException {
        return process(body);
    }

    protected Message process(byte[] body) throws InvalidProtocolBufferException {
        throw new UnsupportedOperationException("Handler must implement a process method");
    }

    // Empty requests share the same zero-byte Protobuf representation.
    protected Message.Builder jsonRequestBuilder() { return Empty.newBuilder(); }

    private static boolean isJson(HttpRequest request) {
        return request.method().equals(HttpMethods.GET)
                || request.entity().getContentType().mediaType().equals(ContentTypes.APPLICATION_JSON.mediaType());
    }

    protected boolean verifyAuth(HttpRequest request, ApiHandler api) {
        return !api.auth() || authenticate(request);
    }

    protected boolean authenticate(HttpRequest request) { return false; }

    private HttpResponse handle(Throwable error, String requestId, boolean json) {
        if (error instanceof GameException) {
            GameException business = (GameException) error;
            return ApiResult.response(business.status, BusinessErrorProto.newBuilder()
                    .setCode(business.code).setMessage(business.getMessage()).build()).toResponse(requestId, json);
        }
        LOG.error("Unexpected API failure ({})", error.getClass().getSimpleName(), error);
        return ApiResult.response(500, BusinessErrorProto.newBuilder().setCode("INTERNAL_ERROR")
                .setMessage("Unexpected server error").build()).toResponse(requestId, json);
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}

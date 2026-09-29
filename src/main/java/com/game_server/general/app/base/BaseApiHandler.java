package com.game_server.general.app.base;

import akka.http.javadsl.model.HttpMethods;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.stream.Materializer;
import com.game.server.proto.ErrorContract;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

public abstract class BaseApiHandler implements AppHandler {
    public static final String HEADER_ID = "X-Request-Id";

    private final BaseException exceptionHandler;

    protected BaseApiHandler() {
        this(BaseException.internalServerError());
    }

    protected BaseApiHandler(BaseException exceptionHandler) {
        this.exceptionHandler = Objects.requireNonNull(exceptionHandler, "exceptionHandler");
    }

    @Override
    public final CompletionStage<HttpResponse> processHttp(HttpRequest request, Materializer materializer) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(materializer, "materializer");

        String requestId = request.getHeader(HEADER_ID).map(header -> header.value()).orElse("");
        ApiHandler api = getClass().getAnnotation(ApiHandler.class);
        if (api == null) {
            return CompletableFuture.completedFuture(handle(
                    new IllegalStateException("Handler must declare @ApiHandler"), requestId));
        }

        try {
            if (!request.method().equals(HttpMethods.POST)
                    || !request.entity().getContentType().mediaType().equals(ApiResult.CONTENT_TYPE.mediaType())) {
                return CompletableFuture.completedFuture(
                        ApiResult.response(StatusCodes.BAD_REQUEST).toResponse(requestId));
            }
            if (!verifyAuth(request, api)) {
                return CompletableFuture.completedFuture(
                        ApiResult.response(StatusCodes.UNAUTHORIZED).toResponse(requestId));
            }
            CompletionStage<ApiResult> result = process(request, materializer);
            if (result == null) {
                return CompletableFuture.completedFuture(handle(
                        new IllegalStateException("Handler returned null"), requestId));
            }
            return result.handle((value, error) -> {
                try {
                    return error == null
                            ? Objects.requireNonNull(value, "result").toResponse(requestId)
                            : handle(unwrap(error), requestId);
                } catch (Throwable conversionError) {
                    return handle(conversionError, requestId);
                }
            });
        } catch (Throwable error) {
            return CompletableFuture.completedFuture(handle(error, requestId));
        }
    }

    public final HttpResponse processHttp(byte[] body) {
        return processBody(body).toResponse(null);
    }

    protected CompletionStage<ApiResult> process(HttpRequest request, Materializer materializer) {
        return request.entity().toStrict(5_000, materializer)
                .thenApply(entity -> processBody(entity.getData().toArray()));
    }

    protected Message process(byte[] body) throws InvalidProtocolBufferException {
        throw new UnsupportedOperationException("Handler must implement a process method");
    }

    private ApiResult processBody(byte[] body) {
        try {
            return ApiResult.success(process(body));
        } catch (InvalidProtocolBufferException | IllegalArgumentException exception) {
            return ApiResult.response(StatusCodes.BAD_REQUEST,
                    ErrorContract.AuthErrorProto.newBuilder().setMessage("Invalid login request").build());
        } catch (IllegalStateException exception) {
            return ApiResult.response(StatusCodes.CONFLICT,
                    ErrorContract.AuthErrorProto.newBuilder().setMessage("Device or player identity does not match").build());
        }
    }

    protected boolean verifyAuth(HttpRequest request, ApiHandler api) {
        return verifyAuth(api) || authenticate(request);
    }

    protected boolean verifyAuth(ApiHandler api) {
        return !api.auth();
    }

    protected boolean authenticate(HttpRequest request) {
        return false;
    }

    private HttpResponse handle(Throwable error, String requestId) {
        return exceptionHandler.handle(error).toResponse(requestId);
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}

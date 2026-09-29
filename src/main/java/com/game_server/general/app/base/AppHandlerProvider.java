package com.game_server.general.app.base;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.stream.Materializer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class AppHandlerProvider implements AppHandler {
    private final Map<String, AppHandler> handlers;

    public AppHandlerProvider(Iterable<? extends AppHandler> handlers) {
        Objects.requireNonNull(handlers, "handlers");
        Map<String, AppHandler> registered = new HashMap<>();
        for (AppHandler handler : handlers) {
            Objects.requireNonNull(handler, "handler");
            ApiHandler api = handler.getClass().getAnnotation(ApiHandler.class);
            if (api == null) {
                throw new IllegalArgumentException("Handler must declare @ApiHandler: " + handler.getClass().getName());
            }
            if (registered.putIfAbsent(api.value(), handler) != null) {
                throw new IllegalArgumentException("Duplicate API handler: " + api.value());
            }
        }
        this.handlers = Collections.unmodifiableMap(registered);
    }

    @Override
    public CompletionStage<HttpResponse> processHttp(HttpRequest request, Materializer materializer) {
        String api = apiFrom(request.getUri().path());
        AppHandler handler = handlers.get(api);
        if (handler == null) {
            return CompletableFuture.completedFuture(
                    ApiResult.response(StatusCodes.NOT_FOUND).toResponse(requestId(request)));
        }
        return handler.processHttp(request, materializer);
    }

    private static String apiFrom(String path) {
        String prefix = "/api/";
        if (!path.startsWith(prefix)) {
            return "";
        }
        String api = path.substring(prefix.length());
        return api.isEmpty() || api.contains("/") ? "" : api;
    }

    private static String requestId(HttpRequest request) {
        return request.getHeader(BaseApiHandler.HEADER_ID).map(header -> header.value()).orElse("");
    }
}

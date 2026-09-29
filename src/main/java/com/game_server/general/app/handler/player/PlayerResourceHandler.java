package com.game_server.general.app.handler.player;

import akka.http.javadsl.model.HttpRequest;
import akka.stream.Materializer;
import com.game.server.proto.PlayerResourceContract.PlayerResourceInitRequest;
import com.game.server.proto.PlayerResourceContract.PlayerResourceInitResponse;
import com.game_server.general.app.base.ApiHandler;
import com.game_server.general.app.base.ApiResult;
import com.game_server.general.app.base.BaseApiHandler;
import com.game_server.general.app.handler.ApiCodes;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

@ApiHandler(value = ApiCodes.PLAYER_RESOURCE_INIT, lock = false, blueprint = false)
public final class PlayerResourceHandler extends BaseApiHandler {
    private static final long ENTITY_TIMEOUT_MILLIS = 5_000;
    private static final long MAX_ENTITY_BYTES = 64 * 1024;

    private final BiFunction<String, PlayerResourceInitRequest, PlayerResourceInitResponse> initialize;

    public PlayerResourceHandler(
            BiFunction<String, PlayerResourceInitRequest, PlayerResourceInitResponse> initialize) {
        this.initialize = Objects.requireNonNull(initialize, "initialize");
    }

    @Override
    protected boolean authenticate(HttpRequest request) {
        return authorization(request).startsWith("Bearer ");
    }

    @Override
    protected CompletionStage<ApiResult> process(HttpRequest request, Materializer materializer) {
        return request.entity()
                .withSizeLimit(MAX_ENTITY_BYTES)
                .toStrict(ENTITY_TIMEOUT_MILLIS, materializer)
                .thenCompose(entity -> {
                    PlayerResourceInitRequest input;
                    try {
                        input = PlayerResourceInitRequest.parseFrom(entity.getData().toArray());
                    } catch (InvalidProtocolBufferException exception) {
                        return CompletableFuture.completedFuture(ApiResult.response(400));
                    }
                    if (input.hasPlayerId()) {
                        return CompletableFuture.completedFuture(ApiResult.response(400));
                    }
                    PlayerResourceInitResponse response = initialize.apply(authorization(request), input);
                    return CompletableFuture.completedFuture(ApiResult.success(response));
                });
    }

    private static String authorization(HttpRequest request) {
        return request.getHeader("Authorization").map(header -> header.value()).orElse("");
    }
}

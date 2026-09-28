package com.game.server.transport;

import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import akka.http.javadsl.model.StatusCode;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.ExceptionHandler;
import akka.http.javadsl.server.MethodRejection;
import akka.http.javadsl.server.RejectionHandler;
import akka.http.javadsl.server.Route;
import com.game.server.application.ApiErrorCode;
import com.game.server.application.ApiException;
import com.game.server.application.LoginInput;
import com.game.server.application.LoginResult;
import com.game.server.application.LoginService;
import com.game.server.application.ResourceService;
import com.game.server.application.ServerConfig;
import com.game.server.domain.Player;
import com.game.server.proto.CommonInitContract.CommonInitRequest;
import com.game.server.proto.CommonInitContract.CommonInitResponse;
import com.game.server.proto.CommonLoginContract.CommonLoginRequest;
import com.game.server.proto.CommonLoginContract.CommonLoginResponse;
import com.game.server.proto.ErrorContract.ApiError;
import com.game.server.proto.PlayerModel.PlayerProfile;
import com.game.server.proto.PlayerResourceContract.PlayerResourceInitRequest;
import com.game.server.proto.PlayerResourceContract.PlayerResourceInitResponse;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Clock;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GameRoutes extends AllDirectives {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameRoutes.class);
    public static final ContentType PROTOBUF = ContentTypes.create(MediaTypes.applicationBinary("x-protobuf", false));
    private final ServerConfig config;
    private final Clock clock;
    private final LoginService login;
    private final ResourceService resources;

    public GameRoutes(ServerConfig config, Clock clock, LoginService login, ResourceService resources) {
        this.config = config;
        this.clock = clock;
        this.login = login;
        this.resources = resources;
    }

    public Route routes() {
        ExceptionHandler exceptions = ExceptionHandler.newBuilder()
            .match(ApiException.class, e -> complete(error(status(e.code()), e.code())))
            .match(Exception.class, e -> {
                LOGGER.error("Unexpected route failure", e);
                return complete(error(StatusCodes.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_ERROR));
            }).build();
        RejectionHandler rejections = RejectionHandler.newBuilder()
            .handle(MethodRejection.class, rejection -> complete(error(StatusCodes.BAD_REQUEST, ApiErrorCode.INVALID_REQUEST)))
            .handleNotFound(complete(error(StatusCodes.BAD_REQUEST, ApiErrorCode.INVALID_REQUEST)))
            .build();
        return handleExceptions(exceptions, () -> handleRejections(rejections, () -> pathPrefix("api", () -> concat(
            path("001003", () -> protobufEntity(this::init)),
            path("001001", () -> protobufEntity(this::login)),
            path("002007", () -> post(() -> optionalHeaderValueByName("Authorization", auth -> protobufEntity(bytes -> resource(auth.orElse(null), bytes)))))
        ))));
    }

    private Route protobufEntity(Function<byte[], HttpResponse> handler) {
        return post(() -> extractRequest(request -> {
            if (!request.entity().getContentType().mediaType().equals(PROTOBUF.mediaType())) return complete(error(StatusCodes.BAD_REQUEST, ApiErrorCode.INVALID_REQUEST));
            return entity(Unmarshaller.entityToByteArray(), bytes -> complete(handler.apply(bytes)));
        }));
    }

    private HttpResponse init(byte[] bytes) {
        parseInit(bytes);
        ServerConfig.InitResult value = config.init(clock);
        return response(CommonInitResponse.newBuilder().setServerTime(value.serverTime).setContractVersion(value.contractVersion).setConfigurationVersion(value.configurationVersion).setMinimumClientVersion(value.minimumClientVersion).setMaintenance(value.maintenance).build().toByteArray());
    }

    private HttpResponse login(byte[] bytes) {
        CommonLoginRequest request;
        try { request = CommonLoginRequest.parseFrom(bytes); } catch (InvalidProtocolBufferException e) { throw new ApiException(ApiErrorCode.INVALID_REQUEST); }
        LoginResult value = login.login(new LoginInput(request.getRequestId(), request.getDeviceId(), request.getAccountId(), request.hasAccountId(), request.getPlatform(), request.getIdentifier(), request.getClientVersion()));
        return response(CommonLoginResponse.newBuilder().setAccountId(value.accountId).setPlayerId(value.playerId).setDeviceId(value.deviceId).setAccessToken(value.accessToken).setSessionExpiresAt(value.expiresAt).setNewAccount(value.newAccount).build().toByteArray());
    }

    private HttpResponse resource(String authorization, byte[] bytes) {
        PlayerResourceInitRequest request;
        try { request = PlayerResourceInitRequest.parseFrom(bytes); } catch (InvalidProtocolBufferException e) { throw new ApiException(ApiErrorCode.INVALID_REQUEST); }
        if (request.hasPlayerId()) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        Player player = resources.initialize(authorization);
        PlayerResourceInitResponse.Builder response = PlayerResourceInitResponse.newBuilder().setProfile(PlayerProfile.newBuilder().setPlayerId(player.id())).setContractVersion(config.contractVersion()).setConfigurationVersion(config.configurationVersion());
        player.resources().forEach((id, quantity) -> response.addResources(com.game.server.proto.ResourceModel.Resource.newBuilder().setResourceId(id).setQuantity(quantity)));
        player.inventory().forEach((id, quantity) -> response.addInventoryItems(com.game.server.proto.InventoryModel.InventoryItem.newBuilder().setItemId(id).setQuantity(quantity)));
        return response(response.build().toByteArray());
    }

    private static void parseInit(byte[] bytes) {
        try { CommonInitRequest.parseFrom(bytes); } catch (InvalidProtocolBufferException e) { throw new ApiException(ApiErrorCode.INVALID_REQUEST); }
    }

    private static HttpResponse response(byte[] bytes) { return HttpResponse.create().withStatus(StatusCodes.OK).withEntity(HttpEntities.create(PROTOBUF, bytes)); }
    private static HttpResponse error(StatusCode status, ApiErrorCode code) { return HttpResponse.create().withStatus(status).withEntity(HttpEntities.create(PROTOBUF, ApiError.newBuilder().setCode(com.game.server.proto.ErrorContract.ApiErrorCode.valueOf(code.name())).setMessage(code.message()).build().toByteArray())); }
    private static StatusCode status(ApiErrorCode code) {
        switch (code) {
            case INVALID_REQUEST: return StatusCodes.BAD_REQUEST;
            case ACCOUNT_ID_MISMATCH: return StatusCodes.CONFLICT;
            case UNAUTHORIZED:
            case SESSION_EXPIRED: return StatusCodes.UNAUTHORIZED;
            case PLAYER_NOT_FOUND: return StatusCodes.NOT_FOUND;
            default: return StatusCodes.INTERNAL_SERVER_ERROR;
        }
    }
}

package com.game.server.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.headers.Authorization;
import akka.http.javadsl.model.headers.OAuth2BearerToken;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.JUnitRouteTest;
import akka.http.javadsl.testkit.TestRoute;
import com.game.server.application.LoginInput;
import com.game.server.application.LoginResult;
import com.game.server.application.LoginService;
import com.game.server.application.ResourceService;
import com.game.server.application.ServerConfig;
import com.game.server.proto.CommonInitContract.CommonInitRequest;
import com.game.server.proto.CommonInitContract.CommonInitResponse;
import com.game.server.proto.CommonLoginContract.CommonLoginRequest;
import com.game.server.proto.CommonLoginContract.CommonLoginResponse;
import com.game.server.proto.ErrorContract.ApiError;
import com.game.server.proto.ErrorContract.ApiErrorCode;
import com.game.server.proto.PlayerResourceContract.PlayerResourceInitRequest;
import com.game.server.proto.PlayerResourceContract.PlayerResourceInitResponse;
import com.game.server.security.JwtTokenService;
import com.game.server.security.TokenService;
import com.game.server.store.memory.InMemoryAccountStore;
import com.game.server.store.memory.InMemoryDeviceStore;
import com.game.server.store.memory.InMemoryIdempotencyStore;
import com.game.server.store.memory.InMemoryPlayerStore;
import com.game.server.store.memory.InMemorySessionStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;

public class GameRoutesTest extends JUnitRouteTest {
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1_700_000_000), ZoneOffset.UTC);
    private InMemoryAccountStore accounts;
    private InMemoryDeviceStore devices;
    private InMemoryPlayerStore players;
    private InMemorySessionStore sessions;
    private TestRoute route;

    @Before
    public void setUp() {
        accounts = new InMemoryAccountStore();
        devices = new InMemoryDeviceStore();
        players = new InMemoryPlayerStore();
        sessions = new InMemorySessionStore();
        TokenService tokens = new JwtTokenService("01234567890123456789012345678901", CLOCK);
        AtomicInteger sequence = new AtomicInteger();
        LoginService login = new LoginService(accounts, devices, players, sessions, new InMemoryIdempotencyStore(), tokens, CLOCK, () -> "id-" + sequence.incrementAndGet());
        ResourceService resources = new ResourceService(accounts, devices, players, sessions, tokens, CLOCK);
        ServerConfig config = new ServerConfig("1", "1", "1.0.0", false, "0.0.0.0", 8080);
        Route routes = new GameRoutes(config, CLOCK, login, resources).routes();
        route = testRoute(routes);
    }

    @Test
    public void publicInitReturnsMetadataWithoutIdentityCreation() throws Exception {
        byte[] body = route.run(post("/api/001003", CommonInitRequest.getDefaultInstance().toByteArray())).assertStatusCode(StatusCodes.OK).assertContentType(GameRoutes.PROTOBUF).entityBytes().toArray();
        CommonInitResponse response = CommonInitResponse.parseFrom(body);
        assertEquals(1_700_000_000, response.getServerTime());
        assertEquals("1", response.getContractVersion());
        assertEquals("1", response.getConfigurationVersion());
        assertEquals("1.0.0", response.getMinimumClientVersion());
        assertFalse(response.getMaintenance());
        assertEquals(0, accounts.size());
        assertEquals(0, players.size());
        assertEquals(0, devices.size());
        assertEquals(0, sessions.size());
    }

    @Test
    public void loginThenAuthenticatedResourceInitReturnsEmptyInitialState() throws Exception {
        CommonLoginResponse login = login("request", "10000000-0000-0000-0000-000000000001");
        byte[] body = route.run(post("/api/002007", PlayerResourceInitRequest.getDefaultInstance().toByteArray()).addHeader(Authorization.oauth2(login.getAccessToken()))).assertStatusCode(StatusCodes.OK).entityBytes().toArray();
        PlayerResourceInitResponse response = PlayerResourceInitResponse.parseFrom(body);
        assertEquals(login.getPlayerId(), response.getProfile().getPlayerId());
        assertEquals(0, response.getResourcesCount());
        assertEquals(0, response.getInventoryItemsCount());
        assertEquals("1", response.getContractVersion());
        assertEquals("1", response.getConfigurationVersion());
        assertEquals(1, players.size());
    }

    @Test
    public void rejectsMissingAuthenticationAndRequestPlayerIdentity() throws Exception {
        ApiError missing = error(route.run(post("/api/002007", PlayerResourceInitRequest.getDefaultInstance().toByteArray())).assertStatusCode(StatusCodes.UNAUTHORIZED).entityBytes().toArray());
        assertEquals(ApiErrorCode.UNAUTHORIZED, missing.getCode());
        CommonLoginResponse login = login("request", "10000000-0000-0000-0000-000000000001");
        PlayerResourceInitRequest selected = PlayerResourceInitRequest.newBuilder().setPlayerId(login.getPlayerId()).build();
        ApiError invalid = error(route.run(post("/api/002007", selected.toByteArray()).addHeader(Authorization.oauth2(login.getAccessToken()))).assertStatusCode(StatusCodes.BAD_REQUEST).entityBytes().toArray());
        assertEquals(ApiErrorCode.INVALID_REQUEST, invalid.getCode());
    }

    @Test
    public void malformedProtobufAndInvalidLoginReturnSafeStableError() throws Exception {
        ApiError malformed = error(route.run(post("/api/001001", new byte[]{10, 5, 1})).assertStatusCode(StatusCodes.BAD_REQUEST).entityBytes().toArray());
        assertEquals(ApiErrorCode.INVALID_REQUEST, malformed.getCode());
        assertEquals("Invalid request", malformed.getMessage());
        CommonLoginRequest blank = CommonLoginRequest.newBuilder().setRequestId(" ").setDeviceId("10000000-0000-0000-0000-000000000001").setPlatform("ios").setIdentifier("id").setClientVersion("1.0.0").build();
        ApiError invalid = error(route.run(post("/api/001001", blank.toByteArray())).assertStatusCode(StatusCodes.BAD_REQUEST).entityBytes().toArray());
        assertFalse(invalid.getMessage().contains("Exception"));
        assertFalse(invalid.getMessage().contains("JWT"));
    }

    @Test
    public void requestRejectionsReturnStableProtobufErrors() throws Exception {
        assertInvalid(route.run(HttpRequest.GET("/api/001003")).assertStatusCode(StatusCodes.BAD_REQUEST).assertContentType(GameRoutes.PROTOBUF).entityBytes().toArray());
        assertInvalid(route.run(HttpRequest.POST("/api/001003").withEntity(HttpEntities.create(ContentTypes.TEXT_PLAIN_UTF8, "body"))).assertStatusCode(StatusCodes.BAD_REQUEST).entityBytes().toArray());
        assertInvalid(route.run(HttpRequest.POST("/api/001003")).assertStatusCode(StatusCodes.BAD_REQUEST).entityBytes().toArray());
        assertInvalid(route.run(post("/missing", new byte[0])).assertStatusCode(StatusCodes.BAD_REQUEST).entityBytes().toArray());
    }

    @Test
    public void accountAttachmentMismatchAndRequestConflictUseStableHttpErrors() throws Exception {
        CommonLoginResponse first = login("first", "device-1");
        CommonLoginRequest attach = request("attach", "device-2").toBuilder().setAccountId(first.getAccountId()).build();
        CommonLoginResponse attached = CommonLoginResponse.parseFrom(route.run(post("/api/001001", attach.toByteArray())).assertStatusCode(StatusCodes.OK).entityBytes().toArray());
        assertEquals(first.getAccountId(), attached.getAccountId());
        assertEquals(first.getPlayerId(), attached.getPlayerId());

        CommonLoginRequest mismatch = request("mismatch", "device-1").toBuilder().setAccountId("other").build();
        assertEquals(ApiErrorCode.ACCOUNT_ID_MISMATCH, error(route.run(post("/api/001001", mismatch.toByteArray())).assertStatusCode(StatusCodes.CONFLICT).entityBytes().toArray()).getCode());
        CommonLoginRequest conflict = request("first", "different-device");
        assertInvalid(route.run(post("/api/001001", conflict.toByteArray())).assertStatusCode(StatusCodes.BAD_REQUEST).entityBytes().toArray());
    }

    @Test
    public void missingPlayerReturnsStableNotFound() throws Exception {
        CommonLoginResponse login = login("request", "device");
        players.delete(login.getPlayerId());
        ApiError response = error(route.run(post("/api/002007", PlayerResourceInitRequest.getDefaultInstance().toByteArray()).addHeader(Authorization.oauth2(login.getAccessToken()))).assertStatusCode(StatusCodes.NOT_FOUND).entityBytes().toArray());
        assertEquals(ApiErrorCode.PLAYER_NOT_FOUND, response.getCode());
    }

    @Test
    public void unexpectedFailureReturnsInternalError() throws Exception {
        TokenService failing = new TokenService() {
            public String issue(com.game.server.domain.Session session) { return "unused"; }
            public com.game.server.security.AuthIdentity parse(String token) { throw new IllegalStateException("deliberate failure"); }
        };
        ResourceService failingResources = new ResourceService(accounts, devices, players, sessions, failing, CLOCK);
        ServerConfig config = new ServerConfig("1", "1", "1.0.0", false, "0.0.0.0", 8080);
        TestRoute failingRoute = testRoute(new GameRoutes(config, CLOCK, new LoginService(accounts, devices, players, sessions, new InMemoryIdempotencyStore(), failing, CLOCK, () -> "id"), failingResources).routes());
        ApiError response = error(failingRoute.run(post("/api/002007", PlayerResourceInitRequest.getDefaultInstance().toByteArray()).addHeader(Authorization.oauth2("safe-token"))).assertStatusCode(StatusCodes.INTERNAL_SERVER_ERROR).entityBytes().toArray());
        assertEquals(ApiErrorCode.INTERNAL_ERROR, response.getCode());
        assertEquals("Internal server error", response.getMessage());
    }

    @Test
    public void invalidSignatureAndOldSessionReturnUnauthorized() throws Exception {
        CommonLoginResponse first = login("request-1", "10000000-0000-0000-0000-000000000001");
        CommonLoginRequest secondRequest = request("request-2", "10000000-0000-0000-0000-000000000001");
        route.run(post("/api/001001", secondRequest.toByteArray())).assertStatusCode(StatusCodes.OK);
        ApiError old = error(route.run(post("/api/002007", PlayerResourceInitRequest.getDefaultInstance().toByteArray()).addHeader(Authorization.oauth2(first.getAccessToken()))).assertStatusCode(StatusCodes.UNAUTHORIZED).entityBytes().toArray());
        assertEquals(ApiErrorCode.UNAUTHORIZED, old.getCode());
        String tampered = first.getAccessToken().substring(0, first.getAccessToken().length() - 1) + "x";
        ApiError bad = error(route.run(post("/api/002007", PlayerResourceInitRequest.getDefaultInstance().toByteArray()).addHeader(Authorization.oauth2(tampered))).assertStatusCode(StatusCodes.UNAUTHORIZED).entityBytes().toArray());
        assertEquals(ApiErrorCode.UNAUTHORIZED, bad.getCode());
        assertTrue(!bad.getMessage().contains(tampered));
    }

    private CommonLoginResponse login(String requestId, String deviceId) throws Exception {
        return CommonLoginResponse.parseFrom(route.run(post("/api/001001", request(requestId, deviceId).toByteArray())).assertStatusCode(StatusCodes.OK).entityBytes().toArray());
    }

    private static CommonLoginRequest request(String requestId, String deviceId) {
        return CommonLoginRequest.newBuilder().setRequestId(requestId).setDeviceId(deviceId).setPlatform("ios").setIdentifier("vendor").setClientVersion("1.0.0").build();
    }

    private static HttpRequest post(String path, byte[] body) {
        return HttpRequest.POST(path).withEntity(HttpEntities.create(GameRoutes.PROTOBUF, body));
    }

    private static void assertInvalid(byte[] body) throws Exception {
        ApiError error = error(body);
        assertEquals(ApiErrorCode.INVALID_REQUEST, error.getCode());
        assertEquals("Invalid request", error.getMessage());
    }

    private static ApiError error(byte[] body) throws Exception { return ApiError.parseFrom(body); }
}

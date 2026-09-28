package com.game.server.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.game.server.domain.Account;
import com.game.server.domain.Player;
import com.game.server.domain.Session;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1_700_000_000), ZoneOffset.UTC);
    private final InMemoryAccountStore accounts = new InMemoryAccountStore();
    private final InMemoryDeviceStore devices = new InMemoryDeviceStore();
    private final InMemoryPlayerStore players = new InMemoryPlayerStore();
    private final InMemorySessionStore sessions = new InMemorySessionStore();
    private final InMemoryIdempotencyStore idempotency = new InMemoryIdempotencyStore();
    private LoginService service;

    @BeforeEach
    void setUp() {
        AtomicInteger sequence = new AtomicInteger();
        service = new LoginService(accounts, devices, players, sessions, idempotency, tokens(), CLOCK, () -> "00000000-0000-0000-0000-" + String.format("%012d", sequence.incrementAndGet()));
    }

    @Test
    void createsIdentityAndReturnsIdempotentOriginalResult() {
        LoginInput input = input("request-1", "10000000-0000-0000-0000-000000000001", null, false, "ios", "vendor-1");
        LoginResult first = service.login(input);
        LoginResult repeat = service.login(input);

        assertTrue(first.newAccount);
        assertEquals(first.accessToken, repeat.accessToken);
        assertEquals(1, accounts.size());
        assertEquals(1, players.size());
        assertEquals(1, devices.size());
        assertEquals(1, sessions.size());
        assertEquals(1, idempotency.size());
        assertEquals(1_700_003_600, first.expiresAt);
    }

    @Test
    void newRequestReplacesDeviceSessionAndRecoversIdentity() {
        LoginResult first = service.login(input("request-1", "10000000-0000-0000-0000-000000000001", null, false, "ios", "vendor-1"));
        LoginResult second = service.login(input("request-2", "10000000-0000-0000-0000-000000000001", first.accountId, true, "ios", "vendor-1"));

        assertEquals(first.accountId, second.accountId);
        assertEquals(first.playerId, second.playerId);
        assertFalse(second.newAccount);
        assertNotEquals(first.accessToken, second.accessToken);
        assertEquals(1, sessions.size());
    }

    @Test
    void newDeviceCanAttachToKnownAccount() {
        LoginResult first = service.login(input("request-1", "10000000-0000-0000-0000-000000000001", null, false, "ios", "vendor-1"));
        LoginResult attached = service.login(input("request-2", "10000000-0000-0000-0000-000000000002", first.accountId, true, "android", "vendor-2"));

        assertEquals(first.accountId, attached.accountId);
        assertEquals(first.playerId, attached.playerId);
        assertFalse(attached.newAccount);
        assertEquals(2, devices.size());
    }

    @Test
    void rejectsUnknownOrConflictingAccountWithoutChangingIdentity() {
        ApiException unknown = assertThrows(ApiException.class, () -> service.login(input("request-1", "10000000-0000-0000-0000-000000000001", "missing", true, "ios", "vendor-1")));
        assertEquals(ApiErrorCode.ACCOUNT_ID_MISMATCH, unknown.code());
        assertEquals(0, accounts.size());
        LoginResult first = service.login(input("request-2", "10000000-0000-0000-0000-000000000001", null, false, "ios", "vendor-1"));
        ApiException conflict = assertThrows(ApiException.class, () -> service.login(input("request-3", "10000000-0000-0000-0000-000000000001", "other", true, "ios", "vendor-1")));
        assertEquals(ApiErrorCode.ACCOUNT_ID_MISMATCH, conflict.code());
        assertEquals(first.accountId, devices.find("10000000-0000-0000-0000-000000000001").orElseThrow().accountId());
    }

    @Test
    void rejectsChangedDeviceAndChangedIdempotentInput() {
        service.login(input("request-1", "10000000-0000-0000-0000-000000000001", null, false, "ios", "vendor-1"));
        assertEquals(ApiErrorCode.INVALID_REQUEST, assertThrows(ApiException.class, () -> service.login(input("request-2", "10000000-0000-0000-0000-000000000001", null, false, "android", "vendor-1"))).code());
        assertEquals(ApiErrorCode.INVALID_REQUEST, assertThrows(ApiException.class, () -> service.login(input("request-1", "10000000-0000-0000-0000-000000000002", null, false, "ios", "vendor-2"))).code());
    }

    @Test
    void rejectsBlankRequiredAndPresentBlankOptionalFields() {
        assertEquals(ApiErrorCode.INVALID_REQUEST, assertThrows(ApiException.class, () -> service.login(input(" ", "device-3", null, false, "ios", "id"))).code());
        assertEquals(ApiErrorCode.INVALID_REQUEST, assertThrows(ApiException.class, () -> service.login(input("request", "device-3", " ", true, "ios", "id"))).code());
    }

    @Test
    void acceptsOpaqueDeviceIdentifiers() {
        LoginResult result = service.login(input("request", "device-3", null, false, "ios", "id"));
        assertEquals("device-3", result.deviceId);
    }

    @Test
    void structuredIdempotencyDistinguishesAbsentLiteralAndDelimiterFields() {
        String device = "10000000-0000-0000-0000-000000000003";
        service.login(input("same", device, null, false, "ios", "vendor"));
        assertEquals(ApiErrorCode.INVALID_REQUEST, assertThrows(ApiException.class, () -> service.login(input("same", device, "<absent>", true, "ios", "vendor"))).code());

        service.login(input("delimiter", "a\u0000b", null, false, "c", "vendor"));
        assertEquals(ApiErrorCode.INVALID_REQUEST, assertThrows(ApiException.class, () -> service.login(input("delimiter", "a", null, false, "b\u0000c", "vendor"))).code());
    }

    @Test
    void playerDefensivelyCopiesState() {
        Map<String, Long> resources = new HashMap<>();
        Map<String, Long> inventory = new HashMap<>();
        resources.put("gold", 10L);
        inventory.put("sword", 1L);
        Player player = new Player("player", resources, inventory);
        resources.put("gold", 99L);
        inventory.clear();
        assertEquals(10L, player.resources().get("gold"));
        assertEquals(1L, player.inventory().get("sword"));
        assertThrows(UnsupportedOperationException.class, () -> player.resources().put("gold", 5L));
    }

    @Test
    void sessionReplacementIsAtomicUnderConcurrency() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> calls = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                int id = i;
                calls.add(() -> {
                    sessions.replace(new Session("session-" + id, "account", "player", "device", 2_000_000_000));
                    return null;
                });
            }
            for (Future<Void> future : executor.invokeAll(calls)) future.get();
            assertEquals(1, sessions.size());
            assertTrue(sessions.findByDevice("device").isPresent());
            assertTrue(sessions.findById(sessions.findByDevice("device").orElseThrow().id()).isPresent());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentSameRequestCreatesOneResult() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<LoginResult>> calls = new ArrayList<>();
            for (int i = 0; i < 20; i++) calls.add(() -> service.login(input("same", "10000000-0000-0000-0000-000000000003", null, false, "ios", "id")));
            List<Future<LoginResult>> futures = executor.invokeAll(calls);
            String token = futures.get(0).get().accessToken;
            for (Future<LoginResult> future : futures) assertEquals(token, future.get().accessToken);
            assertEquals(1, accounts.size());
            assertEquals(1, players.size());
            assertEquals(1, devices.size());
            assertEquals(1, sessions.size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void validatesCurrentSessionClaimsAndMissingPlayer() {
        LoginResult login = service.login(input("request", "10000000-0000-0000-0000-000000000003", null, false, "ios", "id"));
        ResourceService resources = new ResourceService(accounts, devices, players, sessions, tokens(), CLOCK);
        assertEquals(login.playerId, resources.initialize("Bearer " + login.accessToken).id());
        players.delete(login.playerId);
        assertEquals(ApiErrorCode.PLAYER_NOT_FOUND, assertThrows(ApiException.class, () -> resources.initialize("Bearer " + login.accessToken)).code());
    }

    @Test
    void independentlyExpiredStoredSessionIsRejected() {
        TokenService tokenService = tokens();
        LoginResult login = service.login(input("request", "device", null, false, "ios", "id"));
        Session current = sessions.findByDevice("device").orElseThrow();
        sessions.replace(new Session(current.id(), current.accountId(), current.playerId(), current.deviceId(), CLOCK.instant().getEpochSecond()));
        ResourceService resources = new ResourceService(accounts, devices, players, sessions, tokenService, CLOCK);
        assertEquals(ApiErrorCode.SESSION_EXPIRED, assertThrows(ApiException.class, () -> resources.initialize("Bearer " + login.accessToken)).code());
    }

    @Test
    void repeatedResourceReadsDoNotMutateStores() {
        LoginResult login = service.login(input("request", "device", null, false, "ios", "id"));
        ResourceService resources = new ResourceService(accounts, devices, players, sessions, tokens(), CLOCK);
        resources.initialize("Bearer " + login.accessToken);
        resources.initialize("Bearer " + login.accessToken);
        assertEquals(1, accounts.size());
        assertEquals(1, devices.size());
        assertEquals(1, players.size());
        assertEquals(1, sessions.size());
    }

    @Test
    void oldTokenIsUnauthorizedAfterReplacement() {
        TokenService tokenService = tokens();
        AtomicInteger sequence = new AtomicInteger();
        LoginService loginService = new LoginService(accounts, devices, players, sessions, idempotency, tokenService, CLOCK, () -> "id-" + sequence.incrementAndGet());
        ResourceService resources = new ResourceService(accounts, devices, players, sessions, tokenService, CLOCK);
        LoginResult first = loginService.login(input("one", "10000000-0000-0000-0000-000000000003", null, false, "ios", "id"));
        loginService.login(input("two", "10000000-0000-0000-0000-000000000003", null, false, "ios", "id"));
        assertEquals(ApiErrorCode.UNAUTHORIZED, assertThrows(ApiException.class, () -> resources.initialize("Bearer " + first.accessToken)).code());
    }

    private static LoginInput input(String request, String device, String account, boolean present, String platform, String identifier) {
        return new LoginInput(request, device, account, present, platform, identifier, "1.0.0");
    }

    private static TokenService tokens() { return new JwtTokenService("01234567890123456789012345678901", CLOCK); }
}

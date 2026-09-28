package com.game.server.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.game.server.domain.Account;
import com.game.server.domain.Device;
import com.game.server.domain.Player;
import com.game.server.domain.Session;
import com.game.server.security.JwtTokenService;
import com.game.server.security.TokenService;
import com.game.server.store.memory.InMemoryAccountStore;
import com.game.server.store.memory.InMemoryDeviceStore;
import com.game.server.store.memory.InMemoryPlayerStore;
import com.game.server.store.memory.InMemorySessionStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JwtSecurityTest {
    private static final String SECRET = "01234567890123456789012345678901";
    private static final Clock NOW = Clock.fixed(Instant.ofEpochSecond(1000), ZoneOffset.UTC);

    @Test
    void rejectsMalformedTamperedAndExpiredTokensSafely() {
        TokenService tokens = new JwtTokenService(SECRET, NOW);
        Fixture fixture = fixture(tokens, new Session("session", "account", "player", "device", 1100));
        assertEquals(ApiErrorCode.UNAUTHORIZED, assertThrows(ApiException.class, () -> fixture.service.initialize("Bearer malformed")).code());
        String token = tokens.issue(new Session("session", "account", "player", "device", 1100));
        assertEquals(ApiErrorCode.UNAUTHORIZED, assertThrows(ApiException.class, () -> fixture.service.initialize("Bearer " + token.substring(0, token.length() - 1) + "x")).code());
        TokenService expiredTokens = new JwtTokenService(SECRET, Clock.fixed(Instant.ofEpochSecond(1200), ZoneOffset.UTC));
        Fixture expired = fixture(expiredTokens, new Session("expired", "account", "player", "device", 1100));
        String expiredToken = new JwtTokenService(SECRET, NOW).issue(new Session("expired", "account", "player", "device", 1100));
        assertEquals(ApiErrorCode.SESSION_EXPIRED, assertThrows(ApiException.class, () -> expired.service.initialize("Bearer " + expiredToken)).code());
    }

    @Test
    void rejectsMissingBearerUnknownSessionAndMismatchedClaims() {
        TokenService tokens = new JwtTokenService(SECRET, NOW);
        Fixture fixture = fixture(tokens, new Session("session", "account", "player", "device", 1100));
        assertEquals(ApiErrorCode.UNAUTHORIZED, assertThrows(ApiException.class, () -> fixture.service.initialize(null)).code());
        String unknown = tokens.issue(new Session("unknown", "account", "player", "device", 1100));
        assertEquals(ApiErrorCode.UNAUTHORIZED, assertThrows(ApiException.class, () -> fixture.service.initialize("Bearer " + unknown)).code());
        String alteredClaims = tokens.issue(new Session("session", "other", "player", "device", 1100));
        assertEquals(ApiErrorCode.UNAUTHORIZED, assertThrows(ApiException.class, () -> fixture.service.initialize("Bearer " + alteredClaims)).code());
    }

    private static Fixture fixture(TokenService tokens, Session session) {
        InMemoryAccountStore accounts = new InMemoryAccountStore();
        InMemoryDeviceStore devices = new InMemoryDeviceStore();
        InMemoryPlayerStore players = new InMemoryPlayerStore();
        InMemorySessionStore sessions = new InMemorySessionStore();
        accounts.save(new Account("account", "player"));
        devices.save(new Device("device", "account", "ios", "id"));
        players.save(new Player("player"));
        sessions.replace(session);
        return new Fixture(new ResourceService(accounts, devices, players, sessions, tokens, NOW));
    }

    private static final class Fixture {
        private final ResourceService service;
        private Fixture(ResourceService service) { this.service = service; }
    }
}

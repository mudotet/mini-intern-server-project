package com.game.server;

import com.game_server.general.app.helper.JwtHelper;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class JwtHelperTest {
    @Test
    void signingKeyClaimsAndExactExpiryAreValidated() {
        Instant now = Instant.parse("2026-10-02T06:00:00Z");
        String secret = UUID.randomUUID().toString() + UUID.randomUUID();
        JwtHelper helper = new JwtHelper(secret, Clock.fixed(now, ZoneOffset.UTC));
        assertThrows(IllegalArgumentException.class, () -> new JwtHelper("short"));
        String token = helper.create("account", "player", "device", "session", now.getEpochSecond() + 60);
        assertEquals("player", helper.verify(token).get("player_id", String.class));
        assertThrows(JwtException.class, () -> new JwtHelper(UUID.randomUUID().toString() + UUID.randomUUID(), Clock.fixed(now, ZoneOffset.UTC)).verify(token));
        assertThrows(JwtException.class, () -> new JwtHelper(secret, Clock.fixed(now.plusSeconds(60), ZoneOffset.UTC)).verify(token));
    }
}

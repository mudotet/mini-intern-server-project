package com.game_server.general.app.helper;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

public final class JwtHelper {
    private final Key key;

    public JwtHelper(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 bytes");
        }
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String create(String playerId, String deviceId, String sessionId, long expiresAt) {
        return Jwts.builder().claim("player_id", playerId).claim("device_id", deviceId)
                .claim("session_id", sessionId).setExpiration(new Date(expiresAt * 1000))
                .signWith(key).compact();
    }
}

package com.game_server.general.app.helper;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.time.Clock;

public final class JwtHelper {
    private final Key key;
    private final Clock clock;

    public JwtHelper(String secret) {
        this(secret, Clock.systemUTC());
    }

    public JwtHelper(String secret, Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 bytes");
        }
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public String create(String accountId, String playerId, String deviceId, String sessionId, long expiresAt) {
        return Jwts.builder().claim("player_id", playerId).claim("device_id", deviceId)
                .claim("account_id", accountId)
                .claim("session_id", sessionId).setExpiration(new Date(expiresAt * 1000))
                .signWith(key).compact();
    }

    public Claims verify(String token) {
        Claims claims = Jwts.parserBuilder().setSigningKey(key).setClock(() -> Date.from(clock.instant()))
                .build().parseClaimsJws(token).getBody();
        if (claims.getExpiration() == null || !claims.getExpiration().toInstant().isAfter(clock.instant()))
            throw new io.jsonwebtoken.JwtException("Session JWT must have a future expiry");
        return claims;
    }
}

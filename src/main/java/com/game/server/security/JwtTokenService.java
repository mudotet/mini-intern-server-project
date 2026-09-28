package com.game.server.security;

import com.game.server.application.ApiErrorCode;
import com.game.server.application.ApiException;
import com.game.server.domain.Session;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Clock;
import java.util.Date;

public final class JwtTokenService implements TokenService {
    private final Key key;
    private final Clock clock;

    public JwtTokenService(String secret, Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) throw new IllegalArgumentException("JWT_SECRET must contain at least 32 bytes");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public String issue(Session session) {
        return Jwts.builder().setId(session.id()).claim("account_id", session.accountId()).claim("player_id", session.playerId()).claim("device_id", session.deviceId()).claim("expires_at", session.expiresAt()).setExpiration(new Date(session.expiresAt() * 1000)).signWith(key).compact();
    }

    public AuthIdentity parse(String token) {
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(key).setClock(() -> Date.from(clock.instant())).build().parseClaimsJws(token).getBody();
            String sessionId = claims.getId();
            String accountId = claims.get("account_id", String.class);
            String playerId = claims.get("player_id", String.class);
            String deviceId = claims.get("device_id", String.class);
            Number expiresAt = claims.get("expires_at", Number.class);
            if (blank(sessionId) || blank(accountId) || blank(playerId) || blank(deviceId) || expiresAt == null) throw new ApiException(ApiErrorCode.UNAUTHORIZED);
            return new AuthIdentity(sessionId, accountId, playerId, deviceId, expiresAt.longValue());
        } catch (ExpiredJwtException e) {
            throw new ApiException(ApiErrorCode.SESSION_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ApiException(ApiErrorCode.UNAUTHORIZED);
        }
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}

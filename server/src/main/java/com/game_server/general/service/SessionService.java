package com.game_server.general.service;

import com.game.server.proto.SessionInfoContract.SessionInfoResponseProto;
import com.game_server.general.app.helper.JwtHelper;
import com.game_server.general.dao.GameDao.Identity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.time.Clock;
import redis.clients.jedis.JedisPooled;

public final class SessionService {
    private final JedisPooled redis;
    private final JwtHelper jwt;
    private final String prefix;
    private final Clock clock;

    public SessionService(JedisPooled redis, JwtHelper jwt, String prefix, Clock clock) {
        this.redis = redis; this.jwt = jwt; this.prefix = prefix; this.clock = clock;
    }

    public String key(String kind, String id) { return prefix + ":" + kind + ":" + id; }

    public Identity authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() <= 7)
            throw invalid();
        try {
            Claims claims = jwt.verify(authorization.substring(7));
            Identity identity = new Identity(claims.get("account_id", String.class), claims.get("player_id", String.class),
                    claims.get("device_id", String.class), claims.get("session_id", String.class), claims.getExpiration().getTime() / 1000);
            if (!validId(identity.accountId) || !validId(identity.playerId) || !validId(identity.deviceId) || !validId(identity.sessionId)
                    || !value(identity).equals(redis.get(key("session", identity.deviceId)))) throw invalid();
            return identity;
        } catch (JwtException | IllegalArgumentException error) { throw invalid(); }
    }

    public SessionInfoResponseProto info(Identity identity) {
        long now = clock.instant().getEpochSecond();
        return SessionInfoResponseProto.newBuilder().setAccountId(identity.accountId).setPlayerId(identity.playerId)
                .setExpiresAt(identity.expiresAt).setServerTime(now).setRemainingSeconds(Math.max(0, identity.expiresAt - now)).build();
    }

    static String value(Identity identity) {
        return identity.accountId + ":" + identity.playerId + ":" + identity.sessionId + ":" + identity.expiresAt;
    }
    static boolean validId(String id) { return id != null && id.matches("[A-Za-z0-9_-]{1,128}"); }
    private static GameException invalid() { return new GameException(401, "AUTH_INVALID", "Session is invalid or expired"); }
}

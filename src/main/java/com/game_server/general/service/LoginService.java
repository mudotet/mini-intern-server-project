package com.game_server.general.service;

import com.game.server.proto.CommonLoginContract.CommonLoginRequestProto;
import com.game.server.proto.CommonLoginContract.CommonLoginResponseProto;
import com.game_server.general.app.helper.JwtHelper;
import com.game_server.general.dao.GameDao;
import com.game_server.general.dao.GameDao.Identity;
import com.google.protobuf.InvalidProtocolBufferException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.*;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.SetParams;

public final class LoginService {
    private static final long TTL = 43200;
    private final GameDao dao;
    private final JedisPooled redis;
    private final JwtHelper jwt;
    private final SessionService sessions;
    private final Clock clock;

    public LoginService(GameDao dao, JedisPooled redis, JwtHelper jwt, SessionService sessions, Clock clock) {
        this.dao = dao; this.redis = redis; this.jwt = jwt; this.sessions = sessions; this.clock = clock;
    }

    public CommonLoginResponseProto login(CommonLoginRequestProto request, String requestId) {
        GameException.requestId(requestId);
        for (String id : List.of(request.getDeviceId(), request.getAccountId(), request.getPlayerId()))
            if (!id.isEmpty() && !SessionService.validId(id)) throw new GameException(400, "INVALID_IDENTITY", "Invalid identity");
        for (String value : List.of(request.getClient().getPlatform(), request.getClient().getIdentifier(), request.getClient().getVersion()))
            if (value.length() > 128 || value.chars().anyMatch(Character::isISOControl))
                throw new GameException(400, "INVALID_REQUEST", "Invalid client metadata");
        String input = fingerprint(request);
        String cacheKey = sessions.key("login", requestId);
        CommonLoginResponseProto replay = cached(redis.get(cacheKey), input);
        if (replay != null) return replay;
        String lockKey = sessions.key("login-lock", requestId);
        String lock = UUID.randomUUID().toString();
        if (!"OK".equals(redis.set(lockKey, lock, SetParams.setParams().nx().ex(30)))) {
            replay = cached(redis.get(cacheKey), input);
            if (replay != null) return replay;
            throw new GameException(409, "RETRY", "Retry this request");
        }
        try {
            replay = cached(redis.get(cacheKey), input);
            if (replay != null) return replay;
            String deviceId = request.getDeviceId().isEmpty()
                    ? UUID.nameUUIDFromBytes((dao.table() + ":" + requestId).getBytes(StandardCharsets.UTF_8)).toString() : request.getDeviceId();
            Identity identity = dao.identity(deviceId);
            boolean fresh = identity == null;
            if (fresh && (!request.getAccountId().isEmpty() || !request.getPlayerId().isEmpty()))
                throw new GameException(409, "IDENTITY_MISMATCH", "Unknown device cannot recover this identity");
            if (fresh) identity = dao.createIdentity(deviceId);
            if ((!request.getAccountId().isEmpty() && !request.getAccountId().equals(identity.accountId))
                    || (!request.getPlayerId().isEmpty() && !request.getPlayerId().equals(identity.playerId)))
                throw new GameException(409, "IDENTITY_MISMATCH", "Identity does not belong to this device");
            long expiresAt = clock.instant().getEpochSecond() + TTL;
            Identity session = new Identity(identity.accountId, identity.playerId, deviceId, UUID.randomUUID().toString(), expiresAt);
            CommonLoginResponseProto response = CommonLoginResponseProto.newBuilder().setAccountId(identity.accountId)
                    .setPlayerId(identity.playerId).setDeviceId(deviceId).setNewAccount(fresh).setSessionExpiresAt(expiresAt)
                    .setAccessToken(jwt.create(identity.accountId, identity.playerId, deviceId, session.sessionId, expiresAt)).build();
            String encoded = input + "\n" + Base64.getEncoder().encodeToString(response.toByteArray());
            Object result = redis.eval("local old=redis.call('GET',KEYS[1]); if old then return old end; "
                            + "if redis.call('GET',KEYS[3])~=ARGV[4] then return '' end; "
                            + "redis.call('SET',KEYS[2],ARGV[2],'EX',ARGV[3]); redis.call('SET',KEYS[1],ARGV[1],'EX',ARGV[3]); return ARGV[1]",
                    List.of(cacheKey, sessions.key("session", deviceId), lockKey),
                    List.of(encoded, SessionService.value(session), Long.toString(TTL), lock));
            if (result.toString().isEmpty()) throw new GameException(409, "RETRY", "Retry this request");
            return cached(result.toString(), input);
        } finally {
            redis.eval("if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1]) end; return 0",
                    List.of(lockKey), List.of(lock));
        }
    }

    private static CommonLoginResponseProto cached(String value, String input) {
        if (value == null) return null;
        int separator = value.indexOf('\n');
        if (separator < 0) throw new IllegalStateException("Invalid login cache");
        if (!input.equals(value.substring(0, separator)))
            throw new GameException(409, "REQUEST_ID_REUSED", "Request ID belongs to different input");
        try { return CommonLoginResponseProto.parseFrom(Base64.getDecoder().decode(value.substring(separator + 1))); }
        catch (InvalidProtocolBufferException | IllegalArgumentException malformed) { throw new IllegalStateException("Invalid login cache", malformed); }
    }
    private static String fingerprint(CommonLoginRequestProto request) {
        StringBuilder text = new StringBuilder();
        for (String value : List.of(request.getDeviceId(), request.getAccountId(), request.getPlayerId(),
                request.getClient().getPlatform(), request.getClient().getIdentifier(), request.getClient().getVersion()))
            text.append(value.length()).append(':').append(value);
        try { return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(text.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}

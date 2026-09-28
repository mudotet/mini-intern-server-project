package com.game.server.application;

import com.game.server.domain.Account;
import com.game.server.domain.Device;
import com.game.server.domain.Player;
import com.game.server.domain.Session;
import com.game.server.security.AuthIdentity;
import com.game.server.security.TokenService;
import com.game.server.store.AccountStore;
import com.game.server.store.DeviceStore;
import com.game.server.store.PlayerStore;
import com.game.server.store.SessionStore;
import java.time.Clock;

public final class ResourceService {
    private final AccountStore accounts;
    private final DeviceStore devices;
    private final PlayerStore players;
    private final SessionStore sessions;
    private final TokenService tokens;
    private final Clock clock;

    public ResourceService(AccountStore accounts, DeviceStore devices, PlayerStore players, SessionStore sessions, TokenService tokens, Clock clock) {
        this.accounts = accounts;
        this.devices = devices;
        this.players = players;
        this.sessions = sessions;
        this.tokens = tokens;
        this.clock = clock;
    }

    public Player initialize(String bearerToken) {
        if (bearerToken == null || !bearerToken.startsWith("Bearer ") || bearerToken.substring(7).trim().isEmpty()) throw new ApiException(ApiErrorCode.UNAUTHORIZED);
        AuthIdentity identity = tokens.parse(bearerToken.substring(7));
        Session session = sessions.findById(identity.sessionId).orElseThrow(() -> new ApiException(ApiErrorCode.UNAUTHORIZED));
        if (session.expiresAt() <= clock.instant().getEpochSecond() || identity.expiresAt <= clock.instant().getEpochSecond()) throw new ApiException(ApiErrorCode.SESSION_EXPIRED);
        Account account = accounts.find(identity.accountId).orElseThrow(() -> new ApiException(ApiErrorCode.UNAUTHORIZED));
        Device device = devices.find(identity.deviceId).orElseThrow(() -> new ApiException(ApiErrorCode.UNAUTHORIZED));
        if (!matches(session, identity) || !account.playerId().equals(identity.playerId) || !device.accountId().equals(identity.accountId)) throw new ApiException(ApiErrorCode.UNAUTHORIZED);
        return players.find(identity.playerId).orElseThrow(() -> new ApiException(ApiErrorCode.PLAYER_NOT_FOUND));
    }

    private static boolean matches(Session session, AuthIdentity identity) {
        return session.accountId().equals(identity.accountId) && session.playerId().equals(identity.playerId) && session.deviceId().equals(identity.deviceId) && session.expiresAt() == identity.expiresAt;
    }
}

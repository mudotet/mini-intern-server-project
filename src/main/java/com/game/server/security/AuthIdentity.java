package com.game.server.security;

public final class AuthIdentity {
    public final String sessionId;
    public final String accountId;
    public final String playerId;
    public final String deviceId;
    public final long expiresAt;

    public AuthIdentity(String sessionId, String accountId, String playerId, String deviceId, long expiresAt) {
        this.sessionId = sessionId;
        this.accountId = accountId;
        this.playerId = playerId;
        this.deviceId = deviceId;
        this.expiresAt = expiresAt;
    }
}

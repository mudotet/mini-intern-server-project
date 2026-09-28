package com.game.server.application;

public final class LoginResult {
    public final String accountId;
    public final String playerId;
    public final String deviceId;
    public final String accessToken;
    public final long expiresAt;
    public final boolean newAccount;

    public LoginResult(String accountId, String playerId, String deviceId, String accessToken, long expiresAt, boolean newAccount) {
        this.accountId = accountId;
        this.playerId = playerId;
        this.deviceId = deviceId;
        this.accessToken = accessToken;
        this.expiresAt = expiresAt;
        this.newAccount = newAccount;
    }
}

package com.game.server.domain;

public final class Session {
    private final String id;
    private final String accountId;
    private final String playerId;
    private final String deviceId;
    private final long expiresAt;

    public Session(String id, String accountId, String playerId, String deviceId, long expiresAt) {
        this.id = id;
        this.accountId = accountId;
        this.playerId = playerId;
        this.deviceId = deviceId;
        this.expiresAt = expiresAt;
    }

    public String id() { return id; }
    public String accountId() { return accountId; }
    public String playerId() { return playerId; }
    public String deviceId() { return deviceId; }
    public long expiresAt() { return expiresAt; }
}

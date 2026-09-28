package com.game.server.domain;

import java.util.Objects;

public final class LoginRecord {
    private final String inputDeviceId;
    private final String inputAccountId;
    private final boolean inputAccountIdPresent;
    private final String inputPlatform;
    private final String inputIdentifier;
    private final String inputClientVersion;
    private final String accountId;
    private final String playerId;
    private final String deviceId;
    private final String accessToken;
    private final long expiresAt;
    private final boolean newAccount;

    public LoginRecord(String inputDeviceId, String inputAccountId, boolean inputAccountIdPresent, String inputPlatform, String inputIdentifier, String inputClientVersion, String accountId, String playerId, String deviceId, String accessToken, long expiresAt, boolean newAccount) {
        this.inputDeviceId = inputDeviceId;
        this.inputAccountId = inputAccountId;
        this.inputAccountIdPresent = inputAccountIdPresent;
        this.inputPlatform = inputPlatform;
        this.inputIdentifier = inputIdentifier;
        this.inputClientVersion = inputClientVersion;
        this.accountId = accountId;
        this.playerId = playerId;
        this.deviceId = deviceId;
        this.accessToken = accessToken;
        this.expiresAt = expiresAt;
        this.newAccount = newAccount;
    }

    public boolean matchesInput(String deviceId, String accountId, boolean accountIdPresent, String platform, String identifier, String clientVersion) {
        return inputAccountIdPresent == accountIdPresent && Objects.equals(inputDeviceId, deviceId) && Objects.equals(inputAccountId, accountId) && Objects.equals(inputPlatform, platform) && Objects.equals(inputIdentifier, identifier) && Objects.equals(inputClientVersion, clientVersion);
    }

    public String accountId() { return accountId; }
    public String playerId() { return playerId; }
    public String deviceId() { return deviceId; }
    public String accessToken() { return accessToken; }
    public long expiresAt() { return expiresAt; }
    public boolean newAccount() { return newAccount; }
}

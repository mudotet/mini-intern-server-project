package com.game.server.application;

public final class LoginInput {
    private final String requestId;
    private final String deviceId;
    private final String accountId;
    private final boolean accountIdPresent;
    private final String platform;
    private final String identifier;
    private final String clientVersion;

    public LoginInput(String requestId, String deviceId, String accountId, boolean accountIdPresent, String platform, String identifier, String clientVersion) {
        this.requestId = requestId;
        this.deviceId = deviceId;
        this.accountId = accountId;
        this.accountIdPresent = accountIdPresent;
        this.platform = platform;
        this.identifier = identifier;
        this.clientVersion = clientVersion;
    }

    public String requestId() { return requestId; }
    public String deviceId() { return deviceId; }
    public String accountId() { return accountId; }
    public boolean accountIdPresent() { return accountIdPresent; }
    public String platform() { return platform; }
    public String identifier() { return identifier; }
    public String clientVersion() { return clientVersion; }
}

package com.game.server.domain;

public final class Device {
    private final String id;
    private final String accountId;
    private final String platform;
    private final String identifier;

    public Device(String id, String accountId, String platform, String identifier) {
        this.id = id;
        this.accountId = accountId;
        this.platform = platform;
        this.identifier = identifier;
    }

    public String id() { return id; }
    public String accountId() { return accountId; }
    public String platform() { return platform; }
    public String identifier() { return identifier; }
}

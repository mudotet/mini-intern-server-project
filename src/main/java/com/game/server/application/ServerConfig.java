package com.game.server.application;

import java.time.Clock;

public final class ServerConfig {
    private final String contractVersion;
    private final String configurationVersion;
    private final String minimumClientVersion;
    private final boolean maintenance;
    private final String host;
    private final int port;

    public ServerConfig(String contractVersion, String configurationVersion, String minimumClientVersion, boolean maintenance, String host, int port) {
        this.contractVersion = contractVersion;
        this.configurationVersion = configurationVersion;
        this.minimumClientVersion = minimumClientVersion;
        this.maintenance = maintenance;
        this.host = host;
        this.port = port;
    }

    public static ServerConfig fromEnvironment() {
        return new ServerConfig(env("CONTRACT_VERSION", "1"), env("CONFIGURATION_VERSION", "1"), env("MINIMUM_CLIENT_VERSION", "1.0.0"), Boolean.parseBoolean(env("MAINTENANCE", "false")), env("HOST", "0.0.0.0"), Integer.parseInt(env("PORT", "8080")));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    public InitResult init(Clock clock) { return new InitResult(clock.instant().getEpochSecond(), contractVersion, configurationVersion, minimumClientVersion, maintenance); }
    public String contractVersion() { return contractVersion; }
    public String configurationVersion() { return configurationVersion; }
    public String host() { return host; }
    public int port() { return port; }

    public static final class InitResult {
        public final long serverTime;
        public final String contractVersion;
        public final String configurationVersion;
        public final String minimumClientVersion;
        public final boolean maintenance;
        InitResult(long serverTime, String contractVersion, String configurationVersion, String minimumClientVersion, boolean maintenance) {
            this.serverTime = serverTime;
            this.contractVersion = contractVersion;
            this.configurationVersion = configurationVersion;
            this.minimumClientVersion = minimumClientVersion;
            this.maintenance = maintenance;
        }
    }
}

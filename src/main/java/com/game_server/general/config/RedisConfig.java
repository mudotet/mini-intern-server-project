package com.game_server.general.config;

import redis.clients.jedis.JedisPooled;

public final class RedisConfig {
    private RedisConfig() {}

    public static JedisPooled connect() {
        String host = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("REDIS_PORT must be between 1 and 65535");
        }
        return new JedisPooled(host, port);
    }
}

package com.game_server.general.config;

import redis.clients.jedis.JedisPooled;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public final class GlobalConfig implements AutoCloseable {
    private final JedisPooled redis;
    private final DynamoDbClient dynamodb;

    public GlobalConfig() {
        redis = RedisConfig.connect();
        try {
            dynamodb = DynamoConfig.connect();
        } catch (RuntimeException exception) {
            redis.close();
            throw exception;
        }
    }

    public JedisPooled redis() {
        return redis;
    }

    public DynamoDbClient dynamodb() {
        return dynamodb;
    }

    @Override
    public void close() {
        try {
            redis.close();
        } finally {
            dynamodb.close();
        }
    }
}

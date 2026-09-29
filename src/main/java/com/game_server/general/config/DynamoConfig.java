package com.game_server.general.config;

import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

public final class DynamoConfig {
    private DynamoConfig() {}

    public static DynamoDbClient connect() {
        String endpoint = System.getenv().getOrDefault("DYNAMODB_ENDPOINT", "http://localhost:8000");
        URI uri = URI.create(endpoint);
        if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) {
            throw new IllegalArgumentException("DYNAMODB_ENDPOINT must be an HTTP URL");
        }
        return DynamoDbClient.builder()
                .endpointOverride(uri)
                .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "us-east-1")))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                .build();
    }
}

package com.game_server.general.config;

import java.net.URI;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

public final class DynamoConfig {
    private DynamoConfig() {}

    public static DynamoDbClient connect() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(System.getenv().getOrDefault("AWS_REGION", "us-east-1")));
        String mode = System.getenv().getOrDefault("DYNAMODB_MODE", "local");
        if ("local".equals(mode)) {
            URI endpoint = URI.create(System.getenv().getOrDefault("DYNAMODB_ENDPOINT", "http://localhost:8000"));
            if (!"http".equals(endpoint.getScheme()) && !"https".equals(endpoint.getScheme()))
                throw new IllegalArgumentException("DYNAMODB_ENDPOINT must be an HTTP URL");
            builder.endpointOverride(endpoint).credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("local", "local")));
        } else if ("aws".equals(mode)) {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        } else {
            throw new IllegalArgumentException("DYNAMODB_MODE must be local or aws");
        }
        return builder.build();
    }
}

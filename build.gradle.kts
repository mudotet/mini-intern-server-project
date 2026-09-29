import com.google.protobuf.gradle.*

plugins {
    java
    application
    id("com.google.protobuf") version "0.8.19"
}

group = "com.game.server"
version = "1.0.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

application {
    mainClass.set("com.game.server.Main")
}

repositories {
    mavenCentral()
}

val akkaVersion = "2.6.9"
val akkaHttpVersion = "10.2.0"

configurations.all {
    resolutionStrategy.force("com.google.protobuf:protobuf-java:3.21.3")
}

dependencies {
    implementation("com.typesafe.akka:akka-actor-typed_2.13:$akkaVersion")
    implementation("com.typesafe.akka:akka-stream_2.13:$akkaVersion")
    implementation("com.typesafe.akka:akka-http_2.13:$akkaHttpVersion")
    implementation("com.google.protobuf:protobuf-java:3.21.3")
    implementation("redis.clients:jedis:5.2.0")
    implementation("software.amazon.awssdk:dynamodb:2.25.64")
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("org.apache.commons:commons-lang3:3.17.0")
    runtimeOnly("ch.qos.logback:logback-classic:1.2.13")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine")
    testImplementation("com.typesafe.akka:akka-http-testkit_2.13:$akkaHttpVersion")
    testImplementation("com.typesafe.akka:akka-testkit_2.13:$akkaVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.21.3"
    }
}

tasks.test {
    useJUnitPlatform()
}

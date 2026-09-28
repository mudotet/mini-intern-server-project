package com.game.server;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.Http;
import com.game.server.application.LoginService;
import com.game.server.application.ResourceService;
import com.game.server.application.ServerConfig;
import com.game.server.security.JwtTokenService;
import com.game.server.security.TokenService;
import com.game.server.store.memory.InMemoryAccountStore;
import com.game.server.store.memory.InMemoryDeviceStore;
import com.game.server.store.memory.InMemoryIdempotencyStore;
import com.game.server.store.memory.InMemoryPlayerStore;
import com.game.server.store.memory.InMemorySessionStore;
import com.game.server.transport.GameRoutes;
import java.time.Clock;

public final class Main {
    private Main() {}

    public static void main(String[] args) {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.trim().isEmpty()) throw new IllegalStateException("JWT_SECRET is required");
        Clock clock = Clock.systemUTC();
        ServerConfig config = ServerConfig.fromEnvironment();
        InMemoryAccountStore accounts = new InMemoryAccountStore();
        InMemoryDeviceStore devices = new InMemoryDeviceStore();
        InMemoryPlayerStore players = new InMemoryPlayerStore();
        InMemorySessionStore sessions = new InMemorySessionStore();
        TokenService tokens = new JwtTokenService(secret, clock);
        LoginService login = new LoginService(accounts, devices, players, sessions, new InMemoryIdempotencyStore(), tokens, clock, LoginService.uuidIds());
        ResourceService resources = new ResourceService(accounts, devices, players, sessions, tokens, clock);
        ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "mini-game-server");
        Http.get(system).newServerAt(config.host(), config.port()).bind(new GameRoutes(config, clock, login, resources).routes());
    }
}

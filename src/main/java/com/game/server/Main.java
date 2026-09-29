package com.game.server;

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import com.game_server.general.app.base.AppHandleProvider;
import com.game_server.general.app.handler.common.LoginHandler;
import com.game_server.general.app.helper.JwtHelper;
import com.game_server.general.config.GlobalConfig;

public final class Main {
    public static void main(String[] args) {
        String secret = System.getenv("JWT_SECRET");
        JwtHelper jwt = new JwtHelper(secret);
        GlobalConfig config = new GlobalConfig();
        ActorSystem system = ActorSystem.create("mini-server");
        try {
            LoginHandler login = new LoginHandler(config.dynamodb(), config.redis(), jwt);
            login.prepare();
            Http.get(system).newServerAt("0.0.0.0", 8080).bind(new AppHandleProvider(login).routes())
                    .toCompletableFuture().join();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                config.close();
                system.terminate();
            }));
        } catch (RuntimeException exception) {
            config.close();
            system.terminate();
            throw exception;
        }
    }
}

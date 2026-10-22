package com.game.server;

import akka.actor.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import com.game_server.general.app.base.AppHandlerProvider;
import com.game_server.general.app.handler.common.InitHandler;
import com.game_server.general.app.handler.common.LoginHandler;
import com.game_server.general.app.handler.common.SessionInfoHandler;
import com.game_server.general.app.handler.player.PlayerInitHandler;
import com.game_server.general.app.handler.player.PlayerInfoHandler;
import com.game_server.general.app.handler.player.PlayerResourcesHandler;
import com.game_server.general.app.handler.shop.*;
import com.game_server.general.app.helper.JwtHelper;
import com.game_server.general.config.GlobalConfig;
import com.game_server.general.dao.GameDao;
import com.game_server.general.service.*;
import java.time.Clock;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public final class Main {
    public static AppHandlerProvider handlers(GameDao dao, GlobalConfig config, JwtHelper jwt, Clock clock) {
        SessionService sessions = new SessionService(config.redis(), jwt, dao.table(), clock);
        return handlers(dao, config.redis(), jwt, sessions, clock);
    }

    public static AppHandlerProvider handlers(GameDao dao, redis.clients.jedis.JedisPooled redis, JwtHelper jwt,
                                               SessionService sessions, Clock clock) {
        ShopService shop = new ShopService(dao, ShopCatalog.load(), clock, new Random());
        PlayerService players = new PlayerService(dao);
        return new AppHandlerProvider(List.of(new InitHandler(),
                new LoginHandler(new LoginService(dao, redis, jwt, sessions, clock)),
                new PlayerInitHandler(sessions, players),
                new PlayerInfoHandler(sessions, players), new PlayerResourcesHandler(sessions, players),
                new ShopPackagesHandler(sessions, shop), new SessionInfoHandler(sessions),
                new ShopStaticHandler(sessions, shop), new ShopDailyHandler(sessions, shop),
                new PurchaseHandler(sessions, new PurchaseService(dao, shop))));
    }

    public static void main(String[] args) {
        Clock clock = Clock.systemUTC();
        JwtHelper jwt = new JwtHelper(System.getenv("JWT_SECRET"), clock);
        GlobalConfig config = new GlobalConfig();
        ActorSystem system = ActorSystem.create("mini-server");
        try {
            config.redis().ping();
            GameDao dao = new GameDao(config.dynamodb(), System.getenv().getOrDefault("GAME_TABLE", "mini_game"));
            dao.prepare();
            int port = Integer.parseInt(System.getenv().getOrDefault("HTTP_PORT", "8080"));
            ServerBinding binding = Http.get(system).newServerAt("0.0.0.0", port)
                    .bind(handlers(dao, config, jwt, clock).routes()).toCompletableFuture().join();
            System.out.println("Mini game API ready on port " + port);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    binding.terminate(java.time.Duration.ofSeconds(10)).toCompletableFuture().get(12, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    // Process shutdown still closes clients and actor threads.
                } finally {
                    config.close();
                    system.terminate();
                }
            }));
        } catch (RuntimeException exception) {
            config.close();
            system.terminate();
            throw exception;
        }
    }
}

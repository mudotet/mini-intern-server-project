package com.game_server.general.app.base;

import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import com.game_server.general.app.handler.common.LoginHandler;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import java.util.Map;

public final class AppHandleProvider extends AllDirectives {
    private final Map<String, BaseApiHandler> handlers;

    public AppHandleProvider(LoginHandler login) {
        ApiHandler annotation = login.getClass().getAnnotation(ApiHandler.class);
        handlers = Map.of(annotation.value(), login);
    }

    public Route routes() {
        return path("001002", () -> post(() ->
                entity(Unmarshaller.entityToByteString(), bytes ->
                        complete(handlers.get("001002").processHttp(bytes.toArray())))));
    }
}

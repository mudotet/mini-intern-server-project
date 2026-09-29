package com.game_server.general.app.base;

import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;
import com.game_server.general.app.handler.ApiCodes;
import com.game_server.general.app.handler.common.InitHandler;
import com.game_server.general.app.handler.common.LoginHandler;
import akka.http.javadsl.unmarshalling.Unmarshaller;
import java.util.Map;

public final class AppHandleProvider extends AllDirectives {
    private final Map<String, BaseApiHandler> handlers;

    public AppHandleProvider(LoginHandler login) {
        InitHandler init = new InitHandler();
        handlers = Map.of(
                init.getClass().getAnnotation(ApiHandler.class).value(), init,
                login.getClass().getAnnotation(ApiHandler.class).value(), login);
    }

    public Route routes() {
        return pathPrefix("api", () -> concat(
                path(ApiCodes.COMMON_INIT, () -> post(() ->
                        entity(Unmarshaller.entityToByteString(), bytes ->
                                complete(handlers.get(ApiCodes.COMMON_INIT).processHttp(bytes.toArray()))))),
                path(ApiCodes.COMMON_LOGIN, () -> post(() ->
                        entity(Unmarshaller.entityToByteString(), bytes ->
                                complete(handlers.get(ApiCodes.COMMON_LOGIN).processHttp(bytes.toArray())))))));
    }
}

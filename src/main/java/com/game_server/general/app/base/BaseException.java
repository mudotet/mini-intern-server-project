package com.game_server.general.app.base;

import akka.http.javadsl.model.StatusCodes;

@FunctionalInterface
public interface BaseException {
    ApiResult handle(Throwable exception);

    static BaseException internalServerError() {
        return exception -> ApiResult.response(StatusCodes.INTERNAL_SERVER_ERROR);
    }
}

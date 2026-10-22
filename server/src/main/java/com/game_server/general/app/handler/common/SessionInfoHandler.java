package com.game_server.general.app.handler.common;

import akka.http.javadsl.model.HttpRequest;
import com.game_server.general.app.base.ApiHandler;
import com.game_server.general.app.base.BaseApiHandler;
import com.game_server.general.app.handler.ApiCodes;
import com.game_server.general.dao.GameDao.Identity;
import com.game_server.general.service.SessionService;
import com.google.protobuf.Message;

@ApiHandler(value = ApiCodes.SESSION_INFO, method = "GET")
public final class SessionInfoHandler extends BaseApiHandler {
    private final SessionService sessions;
    public SessionInfoHandler(SessionService sessions) { super(sessions); this.sessions = sessions; }
    @Override
    protected Message process(byte[] body, HttpRequest request, Identity identity) {
        return sessions.info(identity);
    }
}

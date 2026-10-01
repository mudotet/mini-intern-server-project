package com.game_server.general.app.handler.common;

import akka.http.javadsl.model.HttpRequest;
import com.game.server.proto.CommonLoginContract.CommonLoginRequestProto;
import com.game_server.general.app.base.ApiHandler;
import com.game_server.general.app.base.BaseApiHandler;
import com.game_server.general.app.handler.ApiCodes;
import com.game_server.general.dao.GameDao.Identity;
import com.game_server.general.service.LoginService;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

@ApiHandler(value = ApiCodes.COMMON_LOGIN, auth = false)
public final class LoginHandler extends BaseApiHandler {
    private final LoginService login;
    public LoginHandler(LoginService login) { this.login = login; }

    @Override
    protected Message.Builder jsonRequestBuilder() { return CommonLoginRequestProto.newBuilder(); }

    @Override
    protected Message process(byte[] body, HttpRequest request, Identity identity) throws InvalidProtocolBufferException {
        return login.login(CommonLoginRequestProto.parseFrom(body),
                request.getHeader(HEADER_ID).map(h -> h.value()).orElse(null));
    }
}

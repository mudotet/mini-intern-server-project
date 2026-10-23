package com.game_server.general.app.handler.battlepass;

import akka.http.javadsl.model.HttpRequest;
import com.game_server.general.app.base.ApiHandler;
import com.game_server.general.app.base.BaseApiHandler;
import com.game_server.general.app.handler.ApiCodes;
import com.game_server.general.dao.GameDao.Identity;
import com.game_server.general.service.BattlePassService;
import com.game_server.general.service.SessionService;
import com.google.protobuf.Message;

@ApiHandler(value = ApiCodes.BATTLE_PASS_INFO, method = "GET")
public final class BattlePassHandler extends BaseApiHandler {
    private final BattlePassService battlePass;
    public BattlePassHandler(SessionService sessions, BattlePassService battlePass) { super(sessions); this.battlePass = battlePass; }
    @Override protected Message process(byte[] body, HttpRequest request, Identity identity) {
        return battlePass.view(identity.playerId);
    }
}

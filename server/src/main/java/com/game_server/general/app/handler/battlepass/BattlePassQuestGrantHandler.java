package com.game_server.general.app.handler.battlepass;

import akka.http.javadsl.model.HttpRequest;
import com.game.server.proto.BattlePassQuestGrantContract.BattlePassQuestGrantRequestProto;
import com.game_server.general.app.base.ApiHandler;
import com.game_server.general.app.base.BaseApiHandler;
import com.game_server.general.app.handler.ApiCodes;
import com.game_server.general.dao.GameDao.Identity;
import com.game_server.general.service.BattlePassService;
import com.game_server.general.service.SessionService;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

/** Registered only for the explicitly enabled local Quest simulation. */
@ApiHandler(ApiCodes.BATTLE_PASS_QUEST_GRANT)
public final class BattlePassQuestGrantHandler extends BaseApiHandler {
    private final BattlePassService battlePass;
    public BattlePassQuestGrantHandler(SessionService sessions, BattlePassService battlePass) { super(sessions); this.battlePass = battlePass; }
    @Override protected Message.Builder jsonRequestBuilder() { return BattlePassQuestGrantRequestProto.newBuilder(); }
    @Override protected Message process(byte[] body, HttpRequest request, Identity identity) throws InvalidProtocolBufferException {
        return battlePass.grantQuestExp(identity.playerId, BattlePassQuestGrantRequestProto.parseFrom(body),
                request.getHeader(HEADER_ID).map(h -> h.value()).orElse(null));
    }
}

package com.game_server.general.app.handler.battlepass;

import akka.http.javadsl.model.HttpRequest;
import com.game.server.proto.BattlePassContract.BattlePassQuestClaimRequestProto;
import com.game_server.general.app.base.ApiHandler;
import com.game_server.general.app.base.BaseApiHandler;
import com.game_server.general.app.handler.ApiCodes;
import com.game_server.general.dao.GameDao.Identity;
import com.game_server.general.service.BattlePassService;
import com.game_server.general.service.SessionService;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

@ApiHandler(ApiCodes.BATTLE_PASS_QUEST)
public final class BattlePassQuestHandler extends BaseApiHandler {
    private final BattlePassService passes;
    public BattlePassQuestHandler(SessionService sessions, BattlePassService passes) { super(sessions); this.passes = passes; }
    @Override
    protected Message.Builder jsonRequestBuilder() { return BattlePassQuestClaimRequestProto.newBuilder(); }
    @Override
    protected Message process(byte[] body, HttpRequest request, Identity identity) throws InvalidProtocolBufferException {
        return passes.claimQuest(identity.playerId, BattlePassQuestClaimRequestProto.parseFrom(body),
                request.getHeader(HEADER_ID).map(h -> h.value()).orElse(null));
    }
}

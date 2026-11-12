package com.game_server.general.app.handler.battlepass;

import akka.http.javadsl.model.HttpRequest;
import com.game.server.proto.BattlePassContract.BattlePassClaimRequestProto;
import com.game_server.general.app.base.ApiHandler;
import com.game_server.general.app.base.BaseApiHandler;
import com.game_server.general.app.handler.ApiCodes;
import com.game_server.general.dao.GameDao.Identity;
import com.game_server.general.service.BattlePassService;
import com.game_server.general.service.SessionService;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

@ApiHandler(ApiCodes.BATTLE_PASS_CLAIM)
public final class BattlePassClaimHandler extends BaseApiHandler {
    private final BattlePassService passes;
    public BattlePassClaimHandler(SessionService sessions, BattlePassService passes) { super(sessions); this.passes = passes; }
    @Override
    protected Message.Builder jsonRequestBuilder() { return BattlePassClaimRequestProto.newBuilder(); }
    @Override
    protected Message process(byte[] body, HttpRequest request, Identity identity) throws InvalidProtocolBufferException {
        return passes.claim(identity.playerId, BattlePassClaimRequestProto.parseFrom(body),
                request.getHeader(HEADER_ID).map(h -> h.value()).orElse(null));
    }
}

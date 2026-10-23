package com.game_server.general.app.handler.battlepass;

import akka.http.javadsl.model.HttpRequest;
import com.game.server.proto.BattlePassPurchaseContract.BattlePassPurchaseRequestProto;
import com.game_server.general.app.base.ApiHandler;
import com.game_server.general.app.base.BaseApiHandler;
import com.game_server.general.app.handler.ApiCodes;
import com.game_server.general.dao.GameDao.Identity;
import com.game_server.general.service.BattlePassService;
import com.game_server.general.service.SessionService;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

@ApiHandler(ApiCodes.BATTLE_PASS_PURCHASE)
public final class BattlePassPurchaseHandler extends BaseApiHandler {
    private final BattlePassService battlePass;
    public BattlePassPurchaseHandler(SessionService sessions, BattlePassService battlePass) { super(sessions); this.battlePass = battlePass; }
    @Override protected Message.Builder jsonRequestBuilder() { return BattlePassPurchaseRequestProto.newBuilder(); }
    @Override protected Message process(byte[] body, HttpRequest request, Identity identity) throws InvalidProtocolBufferException {
        return battlePass.purchasePremium(identity.playerId, BattlePassPurchaseRequestProto.parseFrom(body),
                request.getHeader(HEADER_ID).map(h -> h.value()).orElse(null));
    }
}

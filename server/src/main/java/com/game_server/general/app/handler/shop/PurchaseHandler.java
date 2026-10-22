package com.game_server.general.app.handler.shop;

import akka.http.javadsl.model.HttpRequest;
import com.game.server.proto.ShopPurchaseRequest.ShopPurchaseRequestProto;
import com.game_server.general.app.base.ApiHandler;
import com.game_server.general.app.base.BaseApiHandler;
import com.game_server.general.app.handler.ApiCodes;
import com.game_server.general.dao.GameDao.Identity;
import com.game_server.general.service.PurchaseService;
import com.game_server.general.service.SessionService;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

@ApiHandler(ApiCodes.SHOP_PURCHASE)
public final class PurchaseHandler extends BaseApiHandler {
    private final PurchaseService purchases;
    public PurchaseHandler(SessionService sessions, PurchaseService purchases) { super(sessions); this.purchases = purchases; }
    @Override
    protected Message.Builder jsonRequestBuilder() { return ShopPurchaseRequestProto.newBuilder(); }
    @Override
    protected Message process(byte[] body, HttpRequest request, Identity identity) throws InvalidProtocolBufferException {
        return purchases.purchase(identity.playerId, ShopPurchaseRequestProto.parseFrom(body),
                request.getHeader(HEADER_ID).map(h -> h.value()).orElse(null));
    }
}

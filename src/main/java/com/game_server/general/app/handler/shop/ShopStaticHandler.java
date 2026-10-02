package com.game_server.general.app.handler.shop;

import akka.http.javadsl.model.HttpRequest;
import com.game_server.general.app.base.ApiHandler;
import com.game_server.general.app.base.BaseApiHandler;
import com.game_server.general.app.handler.ApiCodes;
import com.game_server.general.dao.GameDao.Identity;
import com.game_server.general.service.SessionService;
import com.game_server.general.service.ShopService;
import com.google.protobuf.Empty;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

@ApiHandler(ApiCodes.SHOP_STATIC)
public final class ShopStaticHandler extends BaseApiHandler {
    private final ShopService shop;
    public ShopStaticHandler(SessionService sessions, ShopService shop) { super(sessions); this.shop = shop; }
    @Override
    protected Message process(byte[] body, HttpRequest request, Identity identity) throws InvalidProtocolBufferException {
        Empty.parseFrom(body);
        return shop.staticShop();
    }
}

package com.game_server.general.app.handler.player;

import akka.http.javadsl.model.HttpRequest;
import com.game.server.proto.PlayerInitContract.PlayerInitRequestProto;
import com.game_server.general.app.base.ApiHandler;
import com.game_server.general.app.base.BaseApiHandler;
import com.game_server.general.app.handler.ApiCodes;
import com.game_server.general.dao.GameDao.Identity;
import com.game_server.general.service.PlayerService;
import com.game_server.general.service.SessionService;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

@ApiHandler(ApiCodes.PLAYER_RESOURCE_INIT)
public final class PlayerInitHandler extends BaseApiHandler {
    private final PlayerService players;
    public PlayerInitHandler(SessionService sessions, PlayerService players) { super(sessions); this.players = players; }
    @Override
    protected Message.Builder jsonRequestBuilder() { return PlayerInitRequestProto.newBuilder(); }
    @Override
    protected Message process(byte[] body, HttpRequest request, Identity identity) throws InvalidProtocolBufferException {
        PlayerInitRequestProto.parseFrom(body);
        return players.init(identity.playerId);
    }
}

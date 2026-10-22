package com.game_server.general.app.handler.player;

import akka.http.javadsl.model.HttpRequest;
import com.game_server.general.app.base.ApiHandler;
import com.game_server.general.app.base.BaseApiHandler;
import com.game_server.general.app.handler.ApiCodes;
import com.game_server.general.dao.GameDao.Identity;
import com.game_server.general.service.PlayerService;
import com.game_server.general.service.SessionService;
import com.google.protobuf.Message;

@ApiHandler(value = ApiCodes.PLAYER_RESOURCES, method = "GET")
public final class PlayerResourcesHandler extends BaseApiHandler {
    private final PlayerService players;
    public PlayerResourcesHandler(SessionService sessions, PlayerService players) { super(sessions); this.players = players; }
    @Override
    protected Message process(byte[] body, HttpRequest request, Identity identity) {
        return players.resources(identity.playerId);
    }
}

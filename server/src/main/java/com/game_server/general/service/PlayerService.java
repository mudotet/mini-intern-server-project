package com.game_server.general.service;

import com.game.server.proto.PlayerInitContract.PlayerInitResponseProto;
import com.game.server.proto.PlayerInfoContract.PlayerProfileResponseProto;
import com.game.server.proto.PlayerInfoContract.PlayerResourcesResponseProto;
import com.game.server.proto.ResourceModel.Resource;
import com.game_server.general.dao.GameDao;
import com.game_server.general.dao.GameDao.Player;
import java.util.*;

public final class PlayerService {
    private final GameDao dao;
    public PlayerService(GameDao dao) { this.dao = dao; }

    public PlayerInitResponseProto init(String playerId) {
        Player player = dao.player(playerId);
        return PlayerInitResponseProto.newBuilder().setPlayerId(playerId).setAccountId(player.accountId)
                .addAllResources(resourceList(player.resources)).build();
    }
    public PlayerProfileResponseProto profile(String playerId) {
        Player player = dao.player(playerId);
        return PlayerProfileResponseProto.newBuilder().setAccountId(player.accountId).setPlayerId(playerId).build();
    }
    public PlayerResourcesResponseProto resources(String playerId) {
        return PlayerResourcesResponseProto.newBuilder().setPlayerId(playerId)
                .addAllResources(resourceList(dao.player(playerId).resources)).build();
    }
    public static List<Resource> resourceList(Map<String, ? extends Number> values) {
        List<Resource> result = new ArrayList<>();
        new TreeMap<>(values).forEach((id, quantity) -> result.add(Resource.newBuilder().setResourceId(id).setQuantity(quantity.longValue()).build()));
        return result;
    }
}

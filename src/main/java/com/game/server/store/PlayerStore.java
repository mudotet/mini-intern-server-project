package com.game.server.store;

import com.game.server.domain.Player;
import java.util.Optional;

public interface PlayerStore {
    Optional<Player> find(String id);
    void save(Player player);
    void delete(String id);
    int size();
}

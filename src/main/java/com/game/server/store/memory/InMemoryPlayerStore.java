package com.game.server.store.memory;

import com.game.server.domain.Player;
import com.game.server.store.PlayerStore;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryPlayerStore implements PlayerStore {
    private final ConcurrentHashMap<String, Player> values = new ConcurrentHashMap<>();
    public Optional<Player> find(String id) { return Optional.ofNullable(values.get(id)); }
    public void save(Player player) { values.put(player.id(), player); }
    public void delete(String id) { values.remove(id); }
    public int size() { return values.size(); }
}

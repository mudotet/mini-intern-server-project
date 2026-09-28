package com.game.server.store.memory;

import com.game.server.domain.LoginRecord;
import com.game.server.store.IdempotencyStore;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryIdempotencyStore implements IdempotencyStore {
    private final ConcurrentHashMap<String, LoginRecord> values = new ConcurrentHashMap<>();
    public Optional<LoginRecord> find(String requestId) { return Optional.ofNullable(values.get(requestId)); }
    public void save(String requestId, LoginRecord record) { values.put(requestId, record); }
    public int size() { return values.size(); }
}

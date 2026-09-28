package com.game.server.store.memory;

import com.game.server.domain.Session;
import com.game.server.store.SessionStore;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemorySessionStore implements SessionStore {
    private final ConcurrentHashMap<String, Session> byId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> byDevice = new ConcurrentHashMap<>();

    public synchronized Optional<Session> findById(String id) { return Optional.ofNullable(byId.get(id)); }
    public synchronized Optional<Session> findByDevice(String deviceId) {
        String id = byDevice.get(deviceId);
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }
    public synchronized void replace(Session session) {
        String previous = byDevice.put(session.deviceId(), session.id());
        byId.put(session.id(), session);
        if (previous != null && !previous.equals(session.id())) byId.remove(previous);
    }
    public synchronized int size() { return byId.size(); }
}

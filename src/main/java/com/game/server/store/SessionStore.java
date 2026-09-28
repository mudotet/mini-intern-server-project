package com.game.server.store;

import com.game.server.domain.Session;
import java.util.Optional;

public interface SessionStore {
    Optional<Session> findById(String id);
    Optional<Session> findByDevice(String deviceId);
    void replace(Session session);
    int size();
}

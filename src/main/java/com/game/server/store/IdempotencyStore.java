package com.game.server.store;

import com.game.server.domain.LoginRecord;
import java.util.Optional;

public interface IdempotencyStore {
    Optional<LoginRecord> find(String requestId);
    void save(String requestId, LoginRecord record);
    int size();
}

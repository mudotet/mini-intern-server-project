package com.game.server.store;

import com.game.server.domain.Account;
import java.util.Optional;

public interface AccountStore {
    Optional<Account> find(String id);
    void save(Account account);
    int size();
}

package com.game.server.store.memory;

import com.game.server.domain.Account;
import com.game.server.store.AccountStore;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryAccountStore implements AccountStore {
    private final ConcurrentHashMap<String, Account> values = new ConcurrentHashMap<>();
    public Optional<Account> find(String id) { return Optional.ofNullable(values.get(id)); }
    public void save(Account account) { values.put(account.id(), account); }
    public int size() { return values.size(); }
}

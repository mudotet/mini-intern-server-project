package com.game.server.domain;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class Player {
    private final String id;
    private final Map<String, Long> resources;
    private final Map<String, Long> inventory;

    public Player(String id) {
        this(id, Collections.emptyMap(), Collections.emptyMap());
    }

    public Player(String id, Map<String, Long> resources, Map<String, Long> inventory) {
        this.id = id;
        this.resources = Collections.unmodifiableMap(new HashMap<>(resources));
        this.inventory = Collections.unmodifiableMap(new HashMap<>(inventory));
    }

    public String id() { return id; }
    public Map<String, Long> resources() { return resources; }
    public Map<String, Long> inventory() { return inventory; }
}

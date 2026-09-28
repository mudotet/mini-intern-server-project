package com.game.server.domain;

public final class Account {
    private final String id;
    private final String playerId;

    public Account(String id, String playerId) {
        this.id = id;
        this.playerId = playerId;
    }

    public String id() { return id; }
    public String playerId() { return playerId; }
}

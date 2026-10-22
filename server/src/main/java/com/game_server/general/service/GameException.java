package com.game_server.general.service;

public final class GameException extends RuntimeException {
    public final int status;
    public final String code;

    public GameException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static void requestId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9_-]{16,128}"))
            throw new GameException(400, "REQUEST_ID_REQUIRED", "Use a 16-128 character request ID");
    }
}

package com.game.server.application;

public enum ApiErrorCode {
    INVALID_REQUEST("Invalid request"),
    ACCOUNT_ID_MISMATCH("Account identity does not match"),
    UNAUTHORIZED("Unauthorized"),
    SESSION_EXPIRED("Session expired"),
    PLAYER_NOT_FOUND("Player not found"),
    INTERNAL_ERROR("Internal server error");

    private final String message;

    ApiErrorCode(String message) { this.message = message; }
    public String message() { return message; }
}

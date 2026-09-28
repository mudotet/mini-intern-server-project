package com.game.server.application;

public final class ApiException extends RuntimeException {
    private final ApiErrorCode code;

    public ApiException(ApiErrorCode code) {
        super(code.message());
        this.code = code;
    }

    public ApiErrorCode code() { return code; }
}

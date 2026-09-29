package com.game_server.general.app.base;

public abstract class BaseCheatHandler extends BaseApiHandler {
    protected BaseCheatHandler() {
        super();
    }

    protected BaseCheatHandler(BaseException exceptionHandler) {
        super(exceptionHandler);
    }

    @Override
    protected boolean verifyAuth(ApiHandler api) {
        return true;
    }
}

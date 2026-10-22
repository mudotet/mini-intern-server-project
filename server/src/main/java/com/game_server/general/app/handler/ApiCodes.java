package com.game_server.general.app.handler;

public final class ApiCodes {
    private ApiCodes() {}

    private static final String COMMON = "001";
    public static final String COMMON_LOGIN = COMMON + "002";
    public static final String COMMON_INIT = COMMON + "003";
    public static final String PLAYER_RESOURCE_INIT = "002007";
    public static final String PLAYER_INFO = "info/player";
    public static final String PLAYER_RESOURCES = "info/resources";
    public static final String SHOP_PACKAGES = "info/packages";
    public static final String SESSION_INFO = "info/session";

    private static final String SHOP = "003";
    public static final String SHOP_PURCHASE = SHOP + "002";
    public static final String SHOP_STATIC = SHOP + "004";
    public static final String SHOP_DAILY = SHOP + "005";
}

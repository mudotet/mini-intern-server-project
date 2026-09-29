package com.game_server.general.app.base;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface ApiHandler {

    // request api
    String value();

    boolean auth() default true;

    // request require lock player data
    boolean lock() default true;

    // request need blueprint
    boolean blueprint() default true;

    // api feature category
    String[] features() default {};
}
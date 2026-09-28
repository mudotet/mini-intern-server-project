package com.game.server.security;

import com.game.server.domain.Session;

public interface TokenService {
    String issue(Session session);
    AuthIdentity parse(String token);
}

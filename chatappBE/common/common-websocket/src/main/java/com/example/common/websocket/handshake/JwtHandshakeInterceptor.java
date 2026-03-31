package com.example.common.websocket.handshake;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor
        extends AbstractJwtHandshakeInterceptor {

    private final JwtDecoder jwtDecoder;

    @Override
    public UUID resolveUserId(String token) {

        Jwt jwt = jwtDecoder.decode(token);

        return UUID.fromString(jwt.getSubject());
    }
}



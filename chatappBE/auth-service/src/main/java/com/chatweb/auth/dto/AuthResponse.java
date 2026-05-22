package com.chatweb.auth.dto;


import lombok.*;

@Getter
@AllArgsConstructor
public class AuthResponse {
    private final String accessToken;
    private final String refreshToken;
    private final long accessTokenExpiresIn;
    private final Boolean profileReady;

    public AuthResponse(String accessToken, String refreshToken, long accessTokenExpiresIn) {
        this(accessToken, refreshToken, accessTokenExpiresIn, null);
    }
}


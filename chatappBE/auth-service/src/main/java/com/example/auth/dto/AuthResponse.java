package com.example.auth.dto;


import lombok.*;

@Getter
@AllArgsConstructor
public class AuthResponse {
    private final String accessToken;
    private final String refreshToken;
    private final long accessTokenExpiresIn;
}


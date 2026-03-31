package com.example.auth.jwt;

import java.util.UUID;

public interface IJwtVerifierService {
    UUID verify(String token);
}

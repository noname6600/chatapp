package com.chatweb.auth.service;

import java.util.UUID;

public interface ITokenService {
    String generateAccessToken(UUID accountId);
}

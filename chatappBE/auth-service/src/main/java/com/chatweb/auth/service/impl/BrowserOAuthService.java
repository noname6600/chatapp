package com.chatweb.auth.service.impl;

import com.chatweb.auth.dto.AuthResponse;
import com.chatweb.auth.enums.AuthProvider;
import com.chatweb.auth.service.IOAuthLoginExchangeService;
import com.chatweb.auth.service.IOAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class BrowserOAuthService {

    private final IOAuthService oauthAuthService;
    private final IOAuthLoginExchangeService oauthLoginExchangeService;
    private final AuthSessionService authSessionService;

    public String createGoogleExchangeCode(String googleSub, String email) {
        UUID accountId = oauthAuthService.loginGoogle(googleSub, email);
        return oauthLoginExchangeService.create(accountId, AuthProvider.GOOGLE);
    }

    public AuthResponse exchangeGoogleLogin(String code) {
        UUID accountId = oauthLoginExchangeService.consume(code, AuthProvider.GOOGLE);
        return authSessionService.issueTokens(accountId);
    }
}
package com.example.auth.configuration;

import com.example.auth.service.impl.BrowserOAuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class GoogleOAuthAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final BrowserOAuthService browserOAuthService;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Value("${app.frontend.base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        OAuth2User principal = ((OAuth2AuthenticationToken) authentication).getPrincipal();
        String googleSub = principal.getAttribute("sub");
        String email = principal.getAttribute("email");

        if (googleSub == null || googleSub.isBlank() || email == null || email.isBlank()) {
            redirectStrategy.sendRedirect(request, response, loginErrorUrl("google_login_failed"));
            return;
        }

        String exchangeCode = browserOAuthService.createGoogleExchangeCode(googleSub, email);
        String redirectUrl = UriComponentsBuilder.fromUriString(frontendBaseUrl)
                .path("/auth/oauth/google/callback")
                .queryParam("code", exchangeCode)
                .build()
                .toUriString();
        redirectStrategy.sendRedirect(request, response, redirectUrl);
    }

    private String loginErrorUrl(String errorCode) {
        return UriComponentsBuilder.fromUriString(frontendBaseUrl)
                .path("/login")
                .queryParam("oauth_error", errorCode)
                .build()
                .toUriString();
    }
}
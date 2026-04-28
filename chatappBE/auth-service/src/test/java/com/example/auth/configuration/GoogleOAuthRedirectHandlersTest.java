package com.example.auth.configuration;

import com.example.auth.service.impl.BrowserOAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleOAuthRedirectHandlersTest {

    private BrowserOAuthService browserOAuthService;
    private GoogleOAuthAuthenticationSuccessHandler successHandler;
    private GoogleOAuthAuthenticationFailureHandler failureHandler;

    @BeforeEach
    void setUp() {
        browserOAuthService = mock(BrowserOAuthService.class);
        successHandler = new GoogleOAuthAuthenticationSuccessHandler(browserOAuthService);
        failureHandler = new GoogleOAuthAuthenticationFailureHandler();
        ReflectionTestUtils.setField(successHandler, "frontendBaseUrl", "https://chatweb.nani.id.vn");
        ReflectionTestUtils.setField(failureHandler, "frontendBaseUrl", "https://chatweb.nani.id.vn");
    }

    @Test
    void successHandler_redirectsToFrontendCallbackWithExchangeCode() throws Exception {
        OAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", "google-sub", "email", "user@example.com"),
                "email"
        );
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google");
        when(browserOAuthService.createGoogleExchangeCode("google-sub", "user@example.com")).thenReturn("handoff-code");

        MockHttpServletResponse response = new MockHttpServletResponse();
        successHandler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("https://chatweb.nani.id.vn/auth/oauth/google/callback?code=handoff-code");
    }

    @Test
    void failureHandler_redirectsToLoginWithOauthError() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        failureHandler.onAuthenticationFailure(
                new MockHttpServletRequest(),
                response,
                new BadCredentialsException("failed")
        );

        assertThat(response.getRedirectedUrl())
                .isEqualTo("https://chatweb.nani.id.vn/login?oauth_error=google_login_failed");
    }
}
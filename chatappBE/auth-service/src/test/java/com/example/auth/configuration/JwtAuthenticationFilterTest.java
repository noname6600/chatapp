package com.example.auth.configuration;

import com.example.auth.dto.JwtPrincipal;
import com.example.auth.jwt.IJwtVerifierService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private IJwtVerifierService jwtVerifierService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        jwtVerifierService = mock(IJwtVerifierService.class);
        filter = new JwtAuthenticationFilter(jwtVerifierService);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_setsJwtPrincipal_whenBearerTokenIsValid() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(jwtVerifierService.verify("valid-token")).thenReturn(accountId);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer valid-token");

        filter.doFilterInternal(request, new MockHttpServletResponse(), new MockFilterChain());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal()).isInstanceOf(JwtPrincipal.class);
        JwtPrincipal principal = (JwtPrincipal) authentication.getPrincipal();
        assertThat(principal.getAccountId()).isEqualTo(accountId);
        verify(jwtVerifierService).verify("valid-token");
    }
}

package com.chatweb.presence.configuration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class InternalServiceIngressAuthFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void nonInternalPath_doesNotRequireInternalCredential() throws Exception {
        InternalServiceIngressAuthFilter filter = new InternalServiceIngressAuthFilter("X-Internal-Service-Token", "secret", "test");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/presence/global");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void internalPath_missingConfiguredToken_returnsUnauthorized() throws Exception {
        InternalServiceIngressAuthFilter filter = new InternalServiceIngressAuthFilter("X-Internal-Service-Token", "", "test");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/presence/ws/connect");
        request.addHeader("X-Internal-Service-Token", "secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void internalPath_missingHeader_returnsUnauthorized() throws Exception {
        InternalServiceIngressAuthFilter filter = new InternalServiceIngressAuthFilter("X-Internal-Service-Token", "secret", "test");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/presence/ws/connect");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void internalPath_invalidHeader_returnsForbidden() throws Exception {
        InternalServiceIngressAuthFilter filter = new InternalServiceIngressAuthFilter("X-Internal-Service-Token", "secret", "test");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/presence/ws/connect");
        request.addHeader("X-Internal-Service-Token", "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void internalPath_validHeader_allowsRequestAndPreservesSecurityContext() throws Exception {
        InternalServiceIngressAuthFilter filter = new InternalServiceIngressAuthFilter("X-Internal-Service-Token", "secret", "test");

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/presence/ws/connect");
        request.addHeader("X-Internal-Service-Token", "secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}

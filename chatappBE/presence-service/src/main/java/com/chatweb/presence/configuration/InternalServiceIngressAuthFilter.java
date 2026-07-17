package com.chatweb.presence.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Protects edge-ingress presence command endpoints with a service credential header.
 *
 * <p>This filter validates trusted internal caller credentials and preserves existing
 * JWT principal authentication used by controller methods.
 */
@Slf4j
public class InternalServiceIngressAuthFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX = "/api/v1/presence/ws";

    private final String headerName;
    private final String expectedToken;
    private final String activeProfiles;

    public InternalServiceIngressAuthFilter(String headerName, String expectedToken, String activeProfiles) {
        this.headerName = headerName;
        this.expectedToken = expectedToken;
        this.activeProfiles = activeProfiles;
    }

    @PostConstruct
    void failFastInProductionWhenUnconfigured() {
        if (isProductionProfile() && (expectedToken == null || expectedToken.isBlank())) {
            throw new IllegalStateException("internal.auth.token must be configured in production");
        }
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestUri = request.getRequestURI();
        if (requestUri == null || !isInternalPath(requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (expectedToken == null || expectedToken.isBlank()) {
            log.error("[INTERNAL-INGRESS-AUTH] internal.auth.token is not configured; rejecting path={}", requestUri);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Internal credential is not configured");
            return;
        }

        String providedToken = request.getHeader(headerName);
        if (providedToken == null || providedToken.isBlank()) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing internal service credential");
            return;
        }

        boolean valid = MessageDigest.isEqual(
                providedToken.getBytes(StandardCharsets.UTF_8),
                expectedToken.getBytes(StandardCharsets.UTF_8)
        );

        if (!valid) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid internal service credential");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isInternalPath(String requestUri) {
        return requestUri.equals(INTERNAL_PATH_PREFIX) || requestUri.startsWith(INTERNAL_PATH_PREFIX + "/");
    }

    private boolean isProductionProfile() {
        if (activeProfiles == null || activeProfiles.isBlank()) {
            return false;
        }

        return java.util.Arrays.stream(activeProfiles.split(","))
                .map(profile -> profile == null ? "" : profile.trim().toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("prod") || profile.equals("production"));
    }
}
package com.chatweb.friendship.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;

/**
 * Protects friendship-service internal endpoints with a service credential header.
 */
@Slf4j
public class InternalServiceAuthFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PATH_PREFIX = "/api/v1/internal";
    private static final String PRINCIPAL = "internal-service";
    private static final String INTERNAL_AUTHORITY = "ROLE_INTERNAL_SERVICE";

    private final String headerName;
    private final String expectedToken;
    private final String activeProfiles;

    public InternalServiceAuthFilter(String headerName, String expectedToken, String activeProfiles) {
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
            log.error("[INTERNAL-AUTH] internal.auth.token is not configured; rejecting internal request path={}", requestUri);
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

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                PRINCIPAL,
                null,
                List.of(new SimpleGrantedAuthority(INTERNAL_AUTHORITY))
        );
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);

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
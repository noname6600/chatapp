package com.chatweb.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.UUID;

/**
 * Gateway filter that reads the already-validated JWT principal (via Spring Security
 * OAuth2 Resource Server + JWKS) and forwards the user ID as a header to downstream
 * services, so they don't need to re-validate the token.
 *
 * Referenced in application.yaml route filters as: JwtAuthFilter
 */
@Component
public class JwtAuthFilterGatewayFilterFactory extends AbstractGatewayFilterFactory<Object> {

    public JwtAuthFilterGatewayFilterFactory() {
        super(Object.class);
    }

    @Override
    public String name() {
        return "JwtAuthFilter";
    }

    @Override
    public GatewayFilter apply(Object config) {
        return (exchange, chain) -> ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(auth -> auth != null && auth.isAuthenticated()
                        && auth.getPrincipal() instanceof Jwt)
                .flatMap(auth -> {
                    Jwt jwt = (Jwt) auth.getPrincipal();
                    String subject = jwt.getSubject();

                    try {
                        UUID.fromString(subject);
                    } catch (IllegalArgumentException ex) {
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }

                    ServerWebExchange mutated = exchange.mutate()
                            .request(exchange.getRequest().mutate()
                                    .headers(headers -> headers.remove("X-User-Id"))
                                    .header("X-User-Id", jwt.getSubject())
                                    .build())
                            .build();
                    return chain.filter(mutated);
                })
                .switchIfEmpty(chain.filter(exchange));
    }
}

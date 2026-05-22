package com.chatweb.common.security.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtHelperTest {

    @Test
    void extract_user_id_returns_empty_for_invalid_subject() {
        Jwt jwt = jwt("not-a-uuid", Map.of());
        assertThat(JwtHelper.extractUserId(jwt)).isEmpty();
    }

    @Test
    void extract_authorities_combines_scope_and_authorities_claims() {
        Jwt jwt = jwt(UUID.randomUUID().toString(), Map.of(
                "scope", "chat.read chat.write",
                "authorities", List.of("ROLE_USER", "ROLE_ADMIN")
        ));

        Set<String> authorities = JwtHelper.extractAuthorities(jwt);

        assertThat(authorities).contains("scope:chat.read", "scope:chat.write", "ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void extract_authorities_supports_scope_list_format() {
        Jwt jwt = jwt(UUID.randomUUID().toString(), Map.of("scope", List.of("chat.read", "presence.read")));

        Set<String> authorities = JwtHelper.extractAuthorities(jwt);

        assertThat(authorities).contains("scope:chat.read", "scope:presence.read");
    }

    private static Jwt jwt(String subject, Map<String, Object> claims) {
        return new Jwt(
                "token-value",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                mergeClaims(subject, claims)
        );
    }

    private static Map<String, Object> mergeClaims(String subject, Map<String, Object> claims) {
        java.util.HashMap<String, Object> merged = new java.util.HashMap<>(claims);
        merged.put("sub", subject);
        return merged;
    }
}

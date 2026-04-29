package com.example.auth.configuration;

import com.example.common.web.cors.CorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigCorsTest {

    @Test
    void buildCorsConfiguration_parsesMultiOriginsAndIgnoresBlanks() {
        SecurityConfig config = createConfig("https://chatweb.nani.id.vn, ,https://api.chatweb.nani.id.vn");

        CorsConfiguration cors = config.buildCorsConfiguration();

        assertThat(cors.getAllowedOrigins())
                .containsExactly("https://chatweb.nani.id.vn", "https://api.chatweb.nani.id.vn");
        assertThat(cors.checkOrigin("https://chatweb.nani.id.vn")).isEqualTo("https://chatweb.nani.id.vn");
        assertThat(cors.checkOrigin("https://api.chatweb.nani.id.vn")).isEqualTo("https://api.chatweb.nani.id.vn");
    }

    @Test
    void buildCorsConfiguration_rejectsUnconfiguredOrigin() {
        SecurityConfig config = createConfig("https://chatweb.nani.id.vn,https://api.chatweb.nani.id.vn");

        CorsConfiguration cors = config.buildCorsConfiguration();

        assertThat(cors.checkOrigin("http://localhost:5173")).isNull();
    }

    private SecurityConfig createConfig(String allowedOriginsRaw) {
        CorsProperties properties = new CorsProperties();
        CorsProperties.Cors cors = new CorsProperties.Cors();
        cors.setAllowedOrigins(List.of(allowedOriginsRaw));
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        properties.setCors(cors);

        return new SecurityConfig(
            mock(JwtAuthenticationFilter.class),
            properties,
            mock(GoogleOAuthAuthenticationSuccessHandler.class),
            mock(GoogleOAuthAuthenticationFailureHandler.class)
        );
    }
}

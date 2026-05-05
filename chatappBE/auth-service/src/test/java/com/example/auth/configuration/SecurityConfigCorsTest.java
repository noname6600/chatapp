package com.example.auth.configuration;

import com.example.common.web.cors.CorsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigCorsTest {

    @Test
    void buildCorsConfiguration_parsesMultiOriginsAndIgnoresBlanks() {
        CorsProperties properties = createProperties("https://chatweb.nani.id.vn, ,https://api.chatweb.nani.id.vn");

        CorsConfiguration cors = CorsProperties.buildCorsConfiguration(properties);

        assertThat(cors.getAllowedOrigins())
                .containsExactly("https://chatweb.nani.id.vn", "https://api.chatweb.nani.id.vn");
        assertThat(cors.checkOrigin("https://chatweb.nani.id.vn")).isEqualTo("https://chatweb.nani.id.vn");
        assertThat(cors.checkOrigin("https://api.chatweb.nani.id.vn")).isEqualTo("https://api.chatweb.nani.id.vn");
    }

    @Test
    void buildCorsConfiguration_rejectsUnconfiguredOrigin() {
        CorsProperties properties = createProperties("https://chatweb.nani.id.vn,https://api.chatweb.nani.id.vn");

        CorsConfiguration cors = CorsProperties.buildCorsConfiguration(properties);

        assertThat(cors.checkOrigin("http://localhost:5173")).isNull();
    }

    private CorsProperties createProperties(String allowedOriginsRaw) {
        CorsProperties properties = new CorsProperties();
        CorsProperties.Cors cors = new CorsProperties.Cors();
        cors.setAllowedOrigins(List.of(allowedOriginsRaw));
        cors.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        properties.setCors(cors);
        return properties;
    }
}

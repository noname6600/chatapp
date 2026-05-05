package com.example.common.web.cors;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CorsPropertiesTest {

    @Test
    void prefersCanonicalCommonWebBinding() {
        CorsProperties properties = new CorsProperties();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("common.web.cors.allowed-origins[0]", "https://app.example")
                .withProperty("common.security.cors.allowed-origins[0]", "https://legacy.example");

        properties.setEnvironment(environment);

        assertNotNull(properties.getCors());
        assertEquals(List.of("https://app.example"), properties.getCors().getAllowedOrigins());
    }

    @Test
    void fallsBackToLegacyCommonSecurityBinding() {
        CorsProperties properties = new CorsProperties();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("common.security.cors.allowed-origins[0]", "https://legacy.example")
                .withProperty("common.security.cors.allowed-methods[0]", "GET");

        properties.setEnvironment(environment);

        assertNotNull(properties.getCors());
        assertEquals(List.of("https://legacy.example"), properties.getCors().getAllowedOrigins());
        assertEquals(List.of("GET"), properties.getCors().getAllowedMethods());
    }

    // --- buildCorsConfiguration factory tests ---

    @Test
    void buildCorsConfiguration_setsAllowCredentials() {
        CorsProperties props = propsWithOrigins(List.of("https://a.com"));
        CorsConfiguration config = CorsProperties.buildCorsConfiguration(props);
        assertTrue(config.getAllowCredentials());
    }

    @Test
    void buildCorsConfiguration_parsesMultiOriginCommaSeparatedString() {
        CorsProperties props = propsWithOrigins(List.of("https://a.com, https://b.com"));
        CorsConfiguration config = CorsProperties.buildCorsConfiguration(props);
        assertEquals(List.of("https://a.com", "https://b.com"), config.getAllowedOrigins());
    }

    @Test
    void buildCorsConfiguration_filtersBlankAndNullOrigins() {
        CorsProperties props = propsWithOrigins(List.of(" , https://a.com, , "));
        CorsConfiguration config = CorsProperties.buildCorsConfiguration(props);
        assertEquals(List.of("https://a.com"), config.getAllowedOrigins());
    }

    @Test
    void buildCorsConfiguration_withNullCorsBlock_returnsCredentialsOnly() {
        CorsProperties props = new CorsProperties();
        CorsConfiguration config = CorsProperties.buildCorsConfiguration(props);
        assertTrue(config.getAllowCredentials());
        assertNull(config.getAllowedOrigins());
    }

    @Test
    void buildCorsConfiguration_withNullProps_returnsCredentialsOnly() {
        CorsConfiguration config = CorsProperties.buildCorsConfiguration(null);
        assertTrue(config.getAllowCredentials());
        assertNull(config.getAllowedOrigins());
    }

    @Test
    void buildCorsConfiguration_passesThruAllowedMethodsAndHeaders() {
        CorsProperties props = new CorsProperties();
        CorsProperties.Cors cors = new CorsProperties.Cors();
        cors.setAllowedOrigins(List.of("https://a.com"));
        cors.setAllowedMethods(List.of("GET", "POST"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        props.setCors(cors);

        CorsConfiguration config = CorsProperties.buildCorsConfiguration(props);
        assertEquals(List.of("GET", "POST"), config.getAllowedMethods());
        assertEquals(List.of("Authorization", "Content-Type"), config.getAllowedHeaders());
    }

    // --- helpers ---

    private CorsProperties propsWithOrigins(List<String> origins) {
        CorsProperties props = new CorsProperties();
        CorsProperties.Cors cors = new CorsProperties.Cors();
        cors.setAllowedOrigins(origins);
        props.setCors(cors);
        return props;
    }
}

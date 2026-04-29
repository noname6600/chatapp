package com.example.common.web.cors;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}

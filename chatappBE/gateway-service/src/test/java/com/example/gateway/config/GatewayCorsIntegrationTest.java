package com.example.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(classes = GatewayConfig.class)
@TestPropertySource(properties = {
        "gateway.cors.allowed-origins=http://localhost:5173, ,http://localhost:5174"
})
class GatewayCorsIntegrationTest {

    @Test
    void preflight_allowsConfiguredOrigin() {
        GatewayConfig config = new GatewayConfig();
        ReflectionTestUtils.setField(config, "allowedOriginsRaw", "http://localhost:5173,http://localhost:5174");

        CorsConfigurationSource source = config.corsConfigurationSource();
        CorsConfiguration corsConfiguration = source.getCorsConfiguration(MockServerWebExchange.from(
                MockServerHttpRequest.options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .build()
        ));

        assertThat(corsConfiguration).isNotNull();
        assertThat(corsConfiguration.checkOrigin("http://localhost:5173")).isEqualTo("http://localhost:5173");
        assertThat(corsConfiguration.checkHttpMethod(HttpMethod.POST)).contains(HttpMethod.POST);
    }

    @Test
    void preflight_rejectsUnconfiguredOrigin() {
        GatewayConfig config = new GatewayConfig();
        ReflectionTestUtils.setField(config, "allowedOriginsRaw", "http://localhost:5173,http://localhost:5174");

        CorsConfigurationSource source = config.corsConfigurationSource();
        CorsConfiguration corsConfiguration = source.getCorsConfiguration(MockServerWebExchange.from(
                MockServerHttpRequest.options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .build()
        ));

        assertThat(corsConfiguration).isNotNull();
        assertThat(corsConfiguration.checkOrigin("http://evil.example")).isNull();
    }

    @Test
    void corsOrigins_parsesCommaSeparatedValuesAndIgnoresBlanks() {
        GatewayConfig config = new GatewayConfig();
        ReflectionTestUtils.setField(config, "allowedOriginsRaw", "http://localhost:5173, ,http://localhost:5174");

        CorsConfigurationSource source = config.corsConfigurationSource();
        CorsConfiguration corsConfiguration = source.getCorsConfiguration(MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .build()
        ));

        assertThat(corsConfiguration.getAllowedOrigins())
                .containsExactly("http://localhost:5173", "http://localhost:5174");
    }
}

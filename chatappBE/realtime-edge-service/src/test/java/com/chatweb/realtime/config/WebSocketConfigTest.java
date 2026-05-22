package com.chatweb.realtime.config;

import com.chatweb.common.web.cors.CorsProperties;
import com.chatweb.realtime.adapter.in.websocket.JwtHandshakeInterceptor;
import com.chatweb.realtime.adapter.in.websocket.RealtimeWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    @Mock
    private RealtimeWebSocketHandler realtimeWebSocketHandler;

    @Mock
    private JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Mock
    private WebSocketHandlerRegistry registry;

    @Mock
    private WebSocketHandlerRegistration registration;

    @Test
    void registerWebSocketHandlers_usesConfiguredAllowedOrigins() {
        CorsProperties props = new CorsProperties();
        CorsProperties.Cors cors = new CorsProperties.Cors();
        cors.setAllowedOrigins(List.of("https://app.example.com", "https://admin.example.com"));
        props.setCors(cors);

        WebSocketConfig config = new WebSocketConfig(realtimeWebSocketHandler, jwtHandshakeInterceptor, props);

        when(registry.addHandler(realtimeWebSocketHandler, "/realtime")).thenReturn(registration);
        when(registration.addInterceptors(jwtHandshakeInterceptor)).thenReturn(registration);

        config.registerWebSocketHandlers(registry);

        verify(registration).setAllowedOriginPatterns("https://app.example.com", "https://admin.example.com");
    }

    @Test
    void resolveAllowedOriginPatterns_defaultsToLocalhostWhenUnset() {
        WebSocketConfig config = new WebSocketConfig(
                realtimeWebSocketHandler,
                jwtHandshakeInterceptor,
                new CorsProperties()
        );

        assertThat(config.resolveAllowedOriginPatterns()).containsExactly("http://localhost:5173");
    }
}

package com.chatweb.realtime.config;

import com.chatweb.common.web.cors.CorsProperties;
import com.chatweb.realtime.adapter.in.websocket.JwtHandshakeInterceptor;
import com.chatweb.realtime.adapter.in.websocket.RealtimeWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

/**
 * Spring Websocket configuration for realtime edge.
 *
 * Registers the unified websocket endpoint and auth interceptor.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final RealtimeWebSocketHandler realtimeWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;
    private final CorsProperties corsProperties;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        List<String> allowedOriginPatterns = resolveAllowedOriginPatterns();
        registry.addHandler(realtimeWebSocketHandler, "/realtime")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOriginPatterns.toArray(String[]::new));
    }

    List<String> resolveAllowedOriginPatterns() {
        List<String> configured = CorsProperties.buildCorsConfiguration(corsProperties).getAllowedOrigins();
        if (configured == null || configured.isEmpty()) {
            return List.of("http://localhost:5173");
        }
        return configured;
    }
}

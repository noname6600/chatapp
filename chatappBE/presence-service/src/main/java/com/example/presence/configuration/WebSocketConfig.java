package com.example.presence.configuration;

import com.example.common.websocket.handshake.JwtHandshakeInterceptor;
import com.example.presence.websocket.handler.PresenceWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final PresenceWebSocketHandler presenceWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(
            WebSocketHandlerRegistry registry
    ) {

        registry.addHandler(
                        presenceWebSocketHandler,
                        "/ws/presence"
                )
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}


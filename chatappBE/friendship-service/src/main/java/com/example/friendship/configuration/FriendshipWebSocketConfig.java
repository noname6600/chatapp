package com.example.friendship.configuration;

import com.example.friendship.websocket.FriendshipWebSocketHandler;
import com.example.common.websocket.handshake.JwtHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@Slf4j
public class FriendshipWebSocketConfig implements WebSocketConfigurer {

    private final FriendshipWebSocketHandler friendshipWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(friendshipWebSocketHandler, "/ws/friendship")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins("*");
        log.info("[WS] Registered /ws/friendship");
    }
}

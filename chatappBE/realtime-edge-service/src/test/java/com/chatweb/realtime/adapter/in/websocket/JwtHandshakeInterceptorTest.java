package com.chatweb.realtime.adapter.in.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.socket.WebSocketHandler;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtHandshakeInterceptorTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private WebSocketHandler webSocketHandler;

    @Mock
    private JwtDecoder jwtDecoder;

    private JwtHandshakeInterceptor interceptor;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        interceptor = new JwtHandshakeInterceptor(redisTemplate, jwtDecoder);
    }

    @Test
    void beforeHandshake_acceptsValidTicketAndConsumesIt() {
        UUID userId = UUID.randomUUID();
        String ticket = UUID.randomUUID().toString();
        when(valueOperations.get("ws:ticket:" + ticket)).thenReturn(userId.toString());

        Jwt jwt = Jwt.withTokenValue("access-token")
                .header("alg", "none")
                .subject(userId.toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(jwtDecoder.decode("access-token")).thenReturn(jwt);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("ticket", ticket);
        request.addHeader("Authorization", "Bearer access-token");
        ServerHttpRequest serverRequest = new ServletServerHttpRequest(request);
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(serverRequest, response, webSocketHandler, attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes.get("userId")).isEqualTo(userId);
        assertThat(attributes.get("accessTokenRef")).isInstanceOf(String.class);
        verify(valueOperations).set(eq("ws:session-token:" + attributes.get("accessTokenRef")), eq("access-token"), any());
        verify(redisTemplate).delete("ws:ticket:" + ticket);
    }
}

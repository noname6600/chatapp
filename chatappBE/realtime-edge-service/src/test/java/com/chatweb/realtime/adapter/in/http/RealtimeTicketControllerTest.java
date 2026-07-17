package com.chatweb.realtime.adapter.in.http;

import com.chatweb.common.security.jwt.JwtHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RealtimeTicketControllerTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RealtimeTicketController controller;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        controller = new RealtimeTicketController(redisTemplate);
    }

    @Test
    void issueTicket_storesUserIdAndAccessTokenInRedis() {
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("jwt-token")
                .header("alg", "none")
                .subject(userId.toString())
                .build();

        Map<String, String> response = controller.issueTicket(jwt, "Bearer raw-access-token");

        assertThat(response.get("ticket")).isNotBlank();
        verify(valueOperations).set(
                eq("ws:ticket:" + response.get("ticket")),
                eq(userId + "|raw-access-token"),
                eq(30L),
                eq(TimeUnit.SECONDS)
        );
    }
}

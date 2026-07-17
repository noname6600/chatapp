package com.chatweb.realtime.adapter.in.http;

import com.chatweb.common.security.jwt.JwtHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/realtime")
public class RealtimeTicketController {

    private static final String TICKET_PREFIX = "ws:ticket:";

    private final StringRedisTemplate redisTemplate;

    @PostMapping("/ticket")
    public Map<String, String> issueTicket(@AuthenticationPrincipal Jwt jwt,
                                           @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing JWT principal");
        }

        UUID userId = JwtHelper.extractUserId(jwt)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing userId claim"));

        String accessToken = extractBearerToken(authorization);
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer token");
        }

        String ticket = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(TICKET_PREFIX + ticket, userId + ":" + accessToken, 30, TimeUnit.SECONDS);
        return Map.of("ticket", ticket);
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }

        String prefix = "Bearer ";
        if (authorization.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return authorization.substring(prefix.length()).trim();
        }

        return authorization.trim();
    }
}
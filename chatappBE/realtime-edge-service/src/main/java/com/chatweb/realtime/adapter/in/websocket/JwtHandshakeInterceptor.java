package com.chatweb.realtime.adapter.in.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Authenticates websocket handshakes via short-lived realtime tickets.
 *
 * Validates a single-use ticket from Redis. The ticket embeds userId:accessToken
 * (set at issuance time after JWT verification), so no Authorization header is needed —
 * browsers cannot send custom headers on WebSocket upgrade requests.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private static final String TICKET_PREFIX = "ws:ticket:";
    private static final String SESSION_TOKEN_PREFIX = "ws:session-token:";
    private static final Duration DEFAULT_SESSION_TOKEN_TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        try {
            if (!(request instanceof ServletServerHttpRequest servlet)) {
                log.warn("[HANDSHAKE] Request is not ServletServerHttpRequest");
                return false;
            }

            HttpServletRequest httpRequest = servlet.getServletRequest();
            String ticket = httpRequest.getParameter("ticket");

            if (ticket == null || ticket.isBlank()) {
                log.warn("[HANDSHAKE] No ticket provided");
                return false;
            }

            String ticketValue = redisTemplate.opsForValue().get(TICKET_PREFIX + ticket);
            if (ticketValue == null || ticketValue.isBlank()) {
                log.warn("[HANDSHAKE] Invalid or expired ticket");
                return false;
            }

            // Ticket format: "<userId>:<accessToken>" — stored together at issuance time
            int colonIdx = ticketValue.indexOf(':');
            if (colonIdx < 0) {
                log.warn("[HANDSHAKE] Malformed ticket value (missing separator)");
                return false;
            }
            String userIdStr = ticketValue.substring(0, colonIdx);
            String accessToken = ticketValue.substring(colonIdx + 1);

            if (accessToken.isBlank()) {
                log.warn("[HANDSHAKE] Ticket contains no access token");
                return false;
            }

            UUID ticketUserId = UUID.fromString(userIdStr);

            redisTemplate.delete(TICKET_PREFIX + ticket);

            String tokenRef = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(
                SESSION_TOKEN_PREFIX + tokenRef,
                accessToken,
                DEFAULT_SESSION_TOKEN_TTL
            );

            attributes.put("userId", ticketUserId);
            attributes.put("accessTokenRef", tokenRef);
            log.info("[HANDSHAKE] Authenticated userId={}", ticketUserId);
            return true;
        } catch (Exception ex) {
            log.warn("[HANDSHAKE] Auth failed", ex);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                              WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

}

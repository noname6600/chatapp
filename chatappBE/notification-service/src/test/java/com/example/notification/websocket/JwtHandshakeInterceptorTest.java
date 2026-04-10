package com.example.notification.websocket;

import com.example.common.websocket.handshake.AbstractJwtHandshakeInterceptor;
import com.example.common.websocket.handshake.JwtHandshakeInterceptor;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtHandshakeInterceptorTest {

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter counter;

    @Mock
    private WebSocketHandler wsHandler;

    @InjectMocks
    private JwtHandshakeInterceptor interceptor;

    private MockHttpServletResponse mockHttpResponse;
    private ServletServerHttpResponse serverResponse;
    private Map<String, Object> attributes;

    @BeforeEach
    void setUp() {
        mockHttpResponse = new MockHttpServletResponse();
        serverResponse = new ServletServerHttpResponse(mockHttpResponse);
        attributes = new HashMap<>();

        // Inject optional field from abstract superclass for deterministic metric assertions.
        ReflectionTestUtils.setField(interceptor, "meterRegistry", meterRegistry);

        lenient().when(meterRegistry.counter(anyString())).thenReturn(counter);
        lenient().when(meterRegistry.counter(anyString(), anyString(), anyString())).thenReturn(counter);
    }

    // 5.1 — valid token with well-formed JWT
    @Test
    void beforeHandshake_validToken_returnsTrue_andStoresUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(userId.toString());
        when(jwtDecoder.decode("valid.jwt.token")).thenReturn(jwt);

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.addParameter("token", "valid.jwt.token");
        ServletServerHttpRequest serverRequest = new ServletServerHttpRequest(mockRequest);

        boolean result = interceptor.beforeHandshake(serverRequest, serverResponse, wsHandler, attributes);

        assertThat(result).isTrue();
        assertThat(attributes.get(AbstractJwtHandshakeInterceptor.ATTR_USER_ID)).isEqualTo(userId);
        assertThat(serverResponse.getHeaders().getFirst("X-WebSocket-Token-Present")).isEqualTo("true");
        assertThat(serverResponse.getHeaders().getFirst("X-WebSocket-Rejection-Reason")).isNull();
        verify(counter).increment(); // websocket.handshake.success
    }

    // 5.2 — missing token query parameter
    @Test
    void beforeHandshake_missingTokenParam_returnsFalse() throws Exception {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        // no "token" param
        ServletServerHttpRequest serverRequest = new ServletServerHttpRequest(mockRequest);

        boolean result = interceptor.beforeHandshake(serverRequest, serverResponse, wsHandler, attributes);

        assertThat(result).isFalse();
        assertThat(attributes).doesNotContainKey(AbstractJwtHandshakeInterceptor.ATTR_USER_ID);
        assertThat(serverResponse.getHeaders().getFirst("X-WebSocket-Token-Present")).isEqualTo("false");
        assertThat(serverResponse.getHeaders().getFirst("X-WebSocket-Rejection-Reason")).isEqualTo("MISSING_TOKEN");
        verify(counter).increment(); // websocket.handshake.rejected
    }

    // 5.3 — blank/empty token value
    @Test
    void beforeHandshake_blankToken_returnsFalse() throws Exception {
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.addParameter("token", "   ");
        ServletServerHttpRequest serverRequest = new ServletServerHttpRequest(mockRequest);

        boolean result = interceptor.beforeHandshake(serverRequest, serverResponse, wsHandler, attributes);

        assertThat(result).isFalse();
        assertThat(serverResponse.getHeaders().getFirst("X-WebSocket-Token-Present")).isEqualTo("true");
        assertThat(serverResponse.getHeaders().getFirst("X-WebSocket-Rejection-Reason")).isEqualTo("INVALID_FORMAT");
        verify(counter).increment(); // websocket.handshake.rejected
    }

    // 5.4 — JWT with invalid signature
    @Test
    void beforeHandshake_invalidJwtSignature_returnsFalse_andSetsErrorDetails() throws Exception {
        when(jwtDecoder.decode("tampered.jwt.token")).thenThrow(new JwtException("Invalid signature"));

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.addParameter("token", "tampered.jwt.token");
        ServletServerHttpRequest serverRequest = new ServletServerHttpRequest(mockRequest);

        boolean result = interceptor.beforeHandshake(serverRequest, serverResponse, wsHandler, attributes);

        assertThat(result).isFalse();
        assertThat(serverResponse.getHeaders().getFirst("X-WebSocket-Rejection-Reason")).isEqualTo("DECODE_FAILED");
        assertThat(serverResponse.getHeaders().getFirst("X-WebSocket-Error-Details")).isEqualTo("JwtException");
        verify(counter).increment(); // websocket.handshake.rejected
    }

    // 5.5 — resolveUserId returns null (custom implementation path)
    @Test
    void beforeHandshake_nullUserIdFromResolver_returnsFalse() throws Exception {
        // Create a minimal concrete interceptor whose resolveUserId returns null
        AbstractJwtHandshakeInterceptor nullReturningInterceptor = new AbstractJwtHandshakeInterceptor() {
            @Override
            public UUID resolveUserId(String token) {
                return null;
            }
        };
        ReflectionTestUtils.setField(nullReturningInterceptor, "meterRegistry", meterRegistry);

        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.addParameter("token", "some-token");
        ServletServerHttpRequest serverRequest = new ServletServerHttpRequest(mockRequest);

        boolean result = nullReturningInterceptor.beforeHandshake(serverRequest, serverResponse, wsHandler, attributes);

        assertThat(result).isFalse();
        assertThat(attributes).doesNotContainKey(AbstractJwtHandshakeInterceptor.ATTR_USER_ID);
        assertThat(serverResponse.getHeaders().getFirst("X-WebSocket-Rejection-Reason")).isEqualTo("NULL_USER_ID");
    }
}

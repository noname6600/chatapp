package com.chatweb.presence.controller;

import com.chatweb.common.integration.presence.PresenceEventType;
import com.chatweb.common.integration.presence.PresenceStopTypingPayload;
import com.chatweb.common.integration.presence.PresenceTypingPayload;
import com.chatweb.common.realtime.policy.RealtimeFlowId;
import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import com.chatweb.common.web.exception.GlobalExceptionHandler;
import com.chatweb.presence.authorization.RoomAuthorizationService;
import com.chatweb.presence.realtime.port.PresenceRealtimePort;
import com.chatweb.presence.service.IPresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PresenceEdgeCommandControllerIntegrationTest {

    @Mock
    private IPresenceService presenceService;

    @Mock
    private PresenceRealtimePort presenceRealtimePort;

    @Mock
    private RoomAuthorizationService roomAuthorizationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PresenceEdgeCommandController controller = new PresenceEdgeCommandController(presenceService, presenceRealtimePort, roomAuthorizationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new TestJwtArgumentResolver())
                .addFilters(new PresenceAuthRequiredFilter())
                .build();
    }

    @Test
    void connect_whenAuthenticated_returnsOkAndSignalsOnline() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/presence/ws/connect")
                .with(testJwt(userId)))
                .andExpect(status().isOk());

        verify(presenceService).online(userId);
        verify(presenceService).heartbeat(userId, true);
    }

    @Test
    void disconnect_whenAuthenticated_returnsOkAndSignalsOffline() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/presence/ws/disconnect")
                .with(testJwt(userId)))
                .andExpect(status().isOk());

        verify(presenceService).offline(userId);
    }

    @Test
    void heartbeat_whenAuthenticated_returnsOkAndForwardsActiveFlag() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/presence/ws/heartbeat")
                .with(testJwt(userId))
                        .contentType("application/json")
                        .content("{\"active\":false}"))
                .andExpect(status().isOk());

        verify(presenceService).heartbeat(userId, false);
    }

    @Test
    void joinRoom_whenAuthenticated_returnsOkAndForwardsRoomMembership() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/presence/ws/rooms/{roomId}/join", roomId)
                .with(testJwt(userId)))
                .andExpect(status().isOk());

        verify(roomAuthorizationService).ensureRoomMember(roomId, "token");
        verify(presenceService).joinRoom(roomId, userId);
    }

        @Test
        void joinRoom_whenUserNotRoomMember_returnsForbidden() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        doThrow(new BusinessException(CommonErrorCode.FORBIDDEN, "Not authorized for room"))
            .when(roomAuthorizationService)
            .ensureRoomMember(roomId, "token");

        mockMvc.perform(post("/api/v1/presence/ws/rooms/{roomId}/join", roomId)
                .with(testJwt(userId)))
            .andExpect(status().isForbidden());

        verify(presenceService, never()).joinRoom(roomId, userId);
        }

    @Test
    void leaveRoom_whenAuthenticated_returnsOkAndForwardsRoomMembership() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/presence/ws/rooms/{roomId}/leave", roomId)
                .with(testJwt(userId)))
                .andExpect(status().isOk());

        verify(roomAuthorizationService).ensureRoomMember(roomId, "token");
        verify(presenceService).leaveRoom(roomId, userId);
    }

    @Test
    void typing_whenAuthenticated_returnsOkAndPublishesTypingEvent() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/presence/ws/rooms/{roomId}/typing", roomId)
                .with(testJwt(userId)))
                .andExpect(status().isOk());

        verify(roomAuthorizationService).ensureRoomMember(roomId, "token");
        verify(presenceRealtimePort).publishRoomEvent(
                eq(roomId),
                eq(PresenceEventType.ROOM_TYPING.value()),
                any(PresenceTypingPayload.class),
                eq(RealtimeFlowId.PRESENCE_USER_TYPING)
        );
    }

    @Test
    void stopTyping_whenAuthenticated_returnsOkAndPublishesStopTypingEvent() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/presence/ws/rooms/{roomId}/stop-typing", roomId)
                .with(testJwt(userId)))
                .andExpect(status().isOk());

        verify(roomAuthorizationService).ensureRoomMember(roomId, "token");
        verify(presenceRealtimePort).publishRoomEvent(
                eq(roomId),
                eq(PresenceEventType.ROOM_STOP_TYPING.value()),
                any(PresenceStopTypingPayload.class),
                eq(RealtimeFlowId.PRESENCE_USER_STOP_TYPING)
        );
    }

    @Test
    void connect_whenUnauthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/presence/ws/connect"))
                .andExpect(status().isUnauthorized());

        verify(presenceService, never()).online(any());
    }

    @Test
    void typing_whenUnauthenticated_returnsUnauthorized() throws Exception {
        UUID roomId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/presence/ws/rooms/{roomId}/typing", roomId))
                .andExpect(status().isUnauthorized());

        verify(presenceRealtimePort, never()).publishRoomEvent(any(), any(), any(), any());
    }

    @Test
    void connect_whenJwtSubjectIsInvalid_returnsBadRequest() throws Exception {
        Jwt invalidJwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("not-a-uuid")
                .claim("sub", "not-a-uuid")
                .build();

        mockMvc.perform(post("/api/v1/presence/ws/connect")
                .with(request -> {
                    request.setAttribute(TestJwtArgumentResolver.ATTR_JWT, invalidJwt);
                    return request;
                }))
                .andExpect(status().isBadRequest());

        verify(presenceService, never()).online(any());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor testJwt(UUID userId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("sub", userId.toString())
                .build();
        return request -> {
            request.setAttribute(TestJwtArgumentResolver.ATTR_JWT, jwt);
            return request;
        };
    }

    private static final class PresenceAuthRequiredFilter extends OncePerRequestFilter {

        private final AntPathRequestMatcher matcher = new AntPathRequestMatcher("/api/v1/presence/ws/**");

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            if (!matcher.matches(request)) {
                filterChain.doFilter(request, response);
                return;
            }

            if (request.getAttribute(TestJwtArgumentResolver.ATTR_JWT) == null) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }

            filterChain.doFilter(request, response);
        }
    }

    private static final class TestJwtArgumentResolver implements HandlerMethodArgumentResolver {

        static final String ATTR_JWT = "testJwt";

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                    && Jwt.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(MethodParameter parameter,
                                      ModelAndViewContainer mavContainer,
                                      NativeWebRequest webRequest,
                                      WebDataBinderFactory binderFactory) {
            if (webRequest instanceof ServletWebRequest servletWebRequest) {
                return servletWebRequest.getRequest().getAttribute(ATTR_JWT);
            }
            return null;
        }
    }
}

package com.chatweb.voice.application;

import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import com.chatweb.common.web.response.ApiResponse;
import com.chatweb.voice.adapter.in.web.dto.AcceptCallResponse;
import com.chatweb.voice.adapter.in.web.dto.InitiateCallResponse;
import com.chatweb.voice.adapter.out.feign.UserServiceClient;
import com.chatweb.voice.adapter.out.feign.dto.UserSummaryResponse;
import com.chatweb.voice.adapter.out.kafka.CallEventPublisher;
import com.chatweb.voice.config.LiveKitProperties;
import com.chatweb.voice.domain.model.CallState;
import com.chatweb.voice.domain.model.CallStatus;
import com.chatweb.voice.domain.port.out.CallStatePort;
import com.chatweb.voice.domain.port.out.LiveKitPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CallService {

    private final CallStatePort callStatePort;
    private final CallEventPublisher callEventPublisher;
    private final LiveKitPort liveKitPort;
    private final LiveKitProperties liveKitProps;
    private final UserServiceClient userServiceClient;

    public InitiateCallResponse initiate(UUID callerId, UUID calleeId) {
        if (callerId.equals(calleeId)) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "Cannot call yourself");
        }
        callStatePort.getActiveCall(calleeId).ifPresent(existing -> {
            throw new BusinessException(CommonErrorCode.CONFLICT, "Callee is busy");
        });
        callStatePort.getActiveCall(callerId).ifPresent(existing -> {
            throw new BusinessException(CommonErrorCode.CONFLICT, "You are already in a call");
        });

        UUID callId = UUID.randomUUID();
        String lkRoomName = "call-" + callId;

        liveKitPort.createRoom(lkRoomName);
        String callerToken = liveKitPort.generateToken(lkRoomName, callerId.toString(), true, true);

        long now = System.currentTimeMillis();
        CallState state = new CallState(callId, callerId, calleeId, CallStatus.RINGING, lkRoomName, callerToken, now);
        callStatePort.save(state);
        callStatePort.addToRingingIndex(callId, now);
        callStatePort.setActiveCall(callerId, callId);

        Map<UUID, UserSummaryResponse> users = fetchUsers(List.of(callerId, calleeId));
        UserSummaryResponse caller = users.get(callerId);
        UserSummaryResponse callee = users.get(calleeId);

        callEventPublisher.publishInitiated(state,
                caller != null ? caller.getUsername() : null,
                caller != null ? caller.getAvatarUrl() : null,
                callee != null ? callee.getUsername() : null,
                callee != null ? callee.getAvatarUrl() : null,
                liveKitProps.getUrlExternal());

        log.info("[CALL] Initiated callId={} callerId={} calleeId={}", callId, callerId, calleeId);
        return new InitiateCallResponse(callId, "RINGING");
    }

    public AcceptCallResponse accept(UUID callId, UUID calleeId) {
        CallState state = callStatePort.findById(callId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "Call not found"));
        if (state.status() != CallStatus.RINGING) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "Call is no longer ringing");
        }
        if (!calleeId.equals(state.calleeId())) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "Not the callee of this call");
        }

        String calleeToken = liveKitPort.generateToken(state.lkRoomName(), calleeId.toString(), true, true);

        CallState updated = new CallState(callId, state.callerId(), state.calleeId(),
                CallStatus.ACTIVE, state.lkRoomName(), state.callerLkToken(), state.createdAt());
        callStatePort.save(updated);
        callStatePort.removeFromRingingIndex(callId);
        callStatePort.setActiveCall(calleeId, callId);

        Map<UUID, UserSummaryResponse> users = fetchUsers(List.of(state.callerId(), calleeId));
        UserSummaryResponse caller = users.get(state.callerId());
        UserSummaryResponse callee = users.get(calleeId);

        callEventPublisher.publishAccepted(updated,
                caller != null ? caller.getUsername() : null,
                caller != null ? caller.getAvatarUrl() : null,
                callee != null ? callee.getUsername() : null,
                callee != null ? callee.getAvatarUrl() : null,
                state.callerLkToken(), calleeToken,
                liveKitProps.getUrlExternal());

        log.info("[CALL] Accepted callId={} calleeId={}", callId, calleeId);
        return new AcceptCallResponse(calleeToken, liveKitProps.getUrlExternal());
    }

    public void decline(UUID callId, UUID calleeId) {
        CallState state = callStatePort.findById(callId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "Call not found"));
        if (!calleeId.equals(state.calleeId())) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "Not the callee of this call");
        }
        cleanupCall(state);
        callEventPublisher.publishDeclined(state);
        log.info("[CALL] Declined callId={} calleeId={}", callId, calleeId);
    }

    public void cancel(UUID callId, UUID callerId) {
        CallState state = callStatePort.findById(callId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "Call not found"));
        if (!callerId.equals(state.callerId())) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "Not the caller of this call");
        }
        cleanupCall(state);
        callEventPublisher.publishCancelled(state);
        log.info("[CALL] Cancelled callId={} callerId={}", callId, callerId);
    }

    public void end(UUID callId, UUID requesterId) {
        CallState state = callStatePort.findById(callId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "Call not found"));
        if (!requesterId.equals(state.callerId()) && !requesterId.equals(state.calleeId())) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED, "Not a participant of this call");
        }
        int durationSeconds = (int) ((System.currentTimeMillis() - state.createdAt()) / 1000);
        cleanupCall(state);
        callEventPublisher.publishEnded(state, durationSeconds);
        log.info("[CALL] Ended callId={} requesterId={} durationSeconds={}", callId, requesterId, durationSeconds);
    }

    @Scheduled(fixedDelay = 10_000)
    public void pollMissedCalls() {
        long cutoff = System.currentTimeMillis() - 30_000;
        List<String> expiredIds = callStatePort.findRingingCallIdsBefore(cutoff);
        for (String callIdStr : expiredIds) {
            try {
                UUID callId = UUID.fromString(callIdStr);
                if (!callStatePort.tryLockMissed(callId)) continue;
                callStatePort.findById(callId).ifPresent(state -> {
                    callStatePort.removeFromRingingIndex(callId);
                    callStatePort.clearActiveCall(state.callerId());
                    callStatePort.clearActiveCall(state.calleeId());
                    callStatePort.delete(callId);
                    liveKitPort.deleteRoom(state.lkRoomName());
                    callEventPublisher.publishMissed(state);
                    log.info("[CALL] Missed callId={}", callId);
                });
            } catch (Exception ex) {
                log.warn("[CALL-POLLER] Failed to process expired call {}", callIdStr, ex);
            }
        }
    }

    private void cleanupCall(CallState state) {
        callStatePort.removeFromRingingIndex(state.callId());
        callStatePort.clearActiveCall(state.callerId());
        callStatePort.clearActiveCall(state.calleeId());
        callStatePort.delete(state.callId());
        liveKitPort.deleteRoom(state.lkRoomName());
    }

    private Map<UUID, UserSummaryResponse> fetchUsers(List<UUID> ids) {
        try {
            ApiResponse<List<UserSummaryResponse>> resp = userServiceClient.getUsersBatch(ids);
            List<UserSummaryResponse> users = (resp != null && resp.getData() != null) ? resp.getData() : List.of();
            return users.stream().collect(Collectors.toMap(UserSummaryResponse::getAccountId, Function.identity()));
        } catch (Exception ex) {
            log.warn("[CALL] Failed to fetch user info for ids={}", ids, ex);
            return Map.of();
        }
    }
}

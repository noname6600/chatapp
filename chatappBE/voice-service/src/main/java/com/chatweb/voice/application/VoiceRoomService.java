package com.chatweb.voice.application;

import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import com.chatweb.voice.adapter.in.web.dto.JoinVoiceRoomResponse;
import com.chatweb.voice.adapter.in.web.dto.VoiceParticipantDto;
import com.chatweb.voice.adapter.out.feign.UserServiceClient;
import com.chatweb.voice.adapter.out.feign.dto.UserSummaryResponse;
import com.chatweb.voice.adapter.out.kafka.VoiceRoomEventPublisher;
import com.chatweb.voice.config.LiveKitProperties;
import com.chatweb.voice.domain.port.out.LiveKitPort;
import com.chatweb.voice.domain.port.out.VoiceRoomStatePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceRoomService {

    private final LiveKitPort liveKitPort;
    private final VoiceRoomStatePort voiceRoomStatePort;
    private final VoiceRoomEventPublisher eventPublisher;
    private final UserServiceClient userServiceClient;
    private final LiveKitProperties liveKitProps;

    public JoinVoiceRoomResponse join(UUID chatRoomId, UUID userId) {
        String lkRoomName = "voice-" + chatRoomId;

        liveKitPort.createRoom(lkRoomName);

        String token = liveKitPort.generateToken(lkRoomName, userId.toString(), true, true);

        voiceRoomStatePort.addParticipant(chatRoomId, userId);
        voiceRoomStatePort.addActiveRoom(userId, chatRoomId);

        int count = (int) voiceRoomStatePort.getParticipantCount(chatRoomId);

        Set<String> participantIds = voiceRoomStatePort.getParticipantIds(chatRoomId);
        List<VoiceParticipantDto> participants = enrichParticipants(participantIds);

        UserSummaryResponse joiner = findUser(participantIds, userId);
        String username = joiner != null ? joiner.getUsername() : null;
        String avatarUrl = joiner != null ? joiner.getAvatarUrl() : null;

        eventPublisher.publishJoined(chatRoomId, userId, username, avatarUrl, count, lkRoomName);

        log.info("[VOICE-ROOM] User {} joined room {} (participants={})", userId, chatRoomId, count);

        return JoinVoiceRoomResponse.builder()
                .token(token)
                .liveKitUrl(liveKitProps.getUrlExternal())
                .participants(participants)
                .build();
    }

    public void leave(UUID chatRoomId, UUID userId) {
        String lkRoomName = "voice-" + chatRoomId;

        voiceRoomStatePort.removeParticipant(chatRoomId, userId);
        voiceRoomStatePort.removeActiveRoom(userId, chatRoomId);

        long remaining = voiceRoomStatePort.getParticipantCount(chatRoomId);

        eventPublisher.publishLeft(chatRoomId, userId, null, null, (int) remaining, lkRoomName);

        if (remaining == 0) {
            liveKitPort.deleteRoom(lkRoomName);
            eventPublisher.publishClosed(chatRoomId, lkRoomName);
            log.info("[VOICE-ROOM] Room closed (empty) chatRoomId={}", chatRoomId);
        }

        log.info("[VOICE-ROOM] User {} left room {} (remaining={})", userId, chatRoomId, remaining);
    }

    public List<VoiceParticipantDto> getParticipants(UUID chatRoomId) {
        Set<String> ids = voiceRoomStatePort.getParticipantIds(chatRoomId);
        return enrichParticipants(ids);
    }

    public void handleWebhookLeave(UUID chatRoomId, UUID userId) {
        boolean wasMember = voiceRoomStatePort.getParticipantIds(chatRoomId).contains(userId.toString());
        if (wasMember) {
            leave(chatRoomId, userId);
        }
    }

    private List<VoiceParticipantDto> enrichParticipants(Set<String> participantIds) {
        if (participantIds.isEmpty()) return List.of();

        List<UUID> ids = participantIds.stream().map(UUID::fromString).collect(Collectors.toList());
        List<UserSummaryResponse> users;
        try {
            users = userServiceClient.getUsersBatch(ids);
        } catch (Exception ex) {
            log.warn("[VOICE-ROOM] Failed to enrich participants from user-service", ex);
            users = List.of();
        }

        Map<UUID, UserSummaryResponse> userMap = users.stream()
                .collect(Collectors.toMap(UserSummaryResponse::getId, Function.identity()));

        List<VoiceParticipantDto> result = new ArrayList<>();
        for (UUID id : ids) {
            UserSummaryResponse user = userMap.get(id);
            result.add(VoiceParticipantDto.builder()
                    .userId(id)
                    .username(user != null ? user.getUsername() : null)
                    .avatarUrl(user != null ? user.getAvatarUrl() : null)
                    .joinedAt(System.currentTimeMillis())
                    .build());
        }
        return result;
    }

    private UserSummaryResponse findUser(Set<String> participantIds, UUID userId) {
        if (participantIds.isEmpty()) return null;
        try {
            List<UserSummaryResponse> users = userServiceClient.getUsersBatch(List.of(userId));
            return users.isEmpty() ? null : users.get(0);
        } catch (Exception ex) {
            log.warn("[VOICE-ROOM] Failed to fetch user info userId={}", userId, ex);
            return null;
        }
    }
}

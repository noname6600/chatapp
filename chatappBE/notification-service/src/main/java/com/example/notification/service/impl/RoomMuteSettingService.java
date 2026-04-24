package com.example.notification.service.impl;

import com.example.notification.entity.RoomMuteSetting;
import com.example.notification.entity.RoomMuteSettingId;
import com.example.notification.entity.RoomNotificationMode;
import com.example.notification.repository.RoomMuteSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoomMuteSettingService {

    private final RoomMuteSettingRepository repository;

    @Transactional
    public void mute(UUID userId, UUID roomId) {
        setMode(userId, roomId, RoomNotificationMode.NOTHING);
    }

    @Transactional
    public void unmute(UUID userId, UUID roomId) {
        setMode(userId, roomId, RoomNotificationMode.NO_RESTRICT);
    }

    public boolean isMuted(UUID userId, UUID roomId) {
        return getMode(userId, roomId) == RoomNotificationMode.NOTHING;
    }

    public RoomNotificationMode getMode(UUID userId, UUID roomId) {
        return repository.findByIdUserIdAndIdRoomId(userId, roomId)
                .map(this::resolveMode)
                .orElse(RoomNotificationMode.NO_RESTRICT);
    }

    @Transactional
    public RoomNotificationMode setMode(UUID userId, UUID roomId, RoomNotificationMode mode) {
        RoomNotificationMode normalizedMode = mode == null ? RoomNotificationMode.NO_RESTRICT : mode;
        RoomMuteSettingId id = new RoomMuteSettingId(userId, roomId);

        if (normalizedMode == RoomNotificationMode.NO_RESTRICT) {
            repository.deleteById(id);
            return RoomNotificationMode.NO_RESTRICT;
        }

        Instant now = Instant.now();
        RoomMuteSetting existing = repository.findById(id).orElseGet(() -> RoomMuteSetting.builder().id(id).build());
        existing.setMode(normalizedMode);
        existing.setUpdatedAt(now);
        if (normalizedMode == RoomNotificationMode.NOTHING) {
            existing.setMutedAt(now);
        }

        repository.save(existing);
        return normalizedMode;
    }

    public RoomNotificationMode resolveMode(RoomMuteSetting setting) {
        if (setting == null) {
            return RoomNotificationMode.NO_RESTRICT;
        }

        RoomNotificationMode mode = setting.getMode();
        // Compatibility for legacy rows that only had mutedAt.
        if (mode == null) {
            return RoomNotificationMode.NOTHING;
        }

        return mode;
    }
}

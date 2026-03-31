package com.example.notification.service.impl;

import com.example.notification.entity.RoomMuteSetting;
import com.example.notification.entity.RoomMuteSettingId;
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
        RoomMuteSettingId id = new RoomMuteSettingId(userId, roomId);
        if (repository.existsById(id)) {
            return;
        }

        repository.save(
                RoomMuteSetting.builder()
                        .id(id)
                        .mutedAt(Instant.now())
                        .build()
        );
    }

    @Transactional
    public void unmute(UUID userId, UUID roomId) {
        repository.deleteById(new RoomMuteSettingId(userId, roomId));
    }

    public boolean isMuted(UUID userId, UUID roomId) {
        return repository.existsById(new RoomMuteSettingId(userId, roomId));
    }
}

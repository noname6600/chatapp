package com.example.notification.service.impl;

import com.example.notification.dto.NotificationListResponse;
import com.example.notification.dto.NotificationResponse;
import com.example.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private final NotificationRepository repository;

        public NotificationListResponse getNotificationsForUser(UUID userId) {
        List<NotificationResponse> notifications = repository
            .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 50))
                .stream()
                .map(NotificationResponse::from)
                .toList();

        long unreadCount = repository.countByUserIdAndIsReadFalse(userId);

        return NotificationListResponse.builder()
            .notifications(notifications)
            .unreadCount(unreadCount)
            .build();
        }

        public List<NotificationResponse> getUserNotifications(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(NotificationResponse::from)
            .toList();
    }

    public List<NotificationResponse> getUnreadNotifications(UUID userId) {
        return repository.findByUserIdAndIsReadFalse(userId)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    public long countUnread(UUID userId) {
        return repository.countByUserIdAndIsReadFalse(userId);
    }
}

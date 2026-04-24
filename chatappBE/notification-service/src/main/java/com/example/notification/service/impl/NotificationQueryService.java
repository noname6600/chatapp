package com.example.notification.service.impl;

import com.example.notification.dto.NotificationListResponse;
import com.example.notification.dto.NotificationResponse;
import com.example.notification.entity.Notification;
import com.example.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationQueryService {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 50;
    private static final int MAX_SIZE = 100;

    private final NotificationRepository repository;

    public NotificationListResponse getNotificationsForUser(UUID userId) {
        return getNotificationsForUser(userId, DEFAULT_PAGE, DEFAULT_SIZE, null);
    }

    public NotificationListResponse getNotificationsForUser(
            UUID userId,
            int page,
            int size,
            Instant beforeCreatedAt
    ) {
        int normalizedPage = Math.max(0, page);
        int normalizedSize = Math.max(1, Math.min(size, MAX_SIZE));

        List<Notification> rows = beforeCreatedAt == null
                ? repository.findInboxOrdered(userId, PageRequest.of(normalizedPage, normalizedSize + 1))
                : repository.findInboxOrderedBefore(userId, beforeCreatedAt, PageRequest.of(normalizedPage, normalizedSize + 1));

        boolean hasMore = rows.size() > normalizedSize;
        List<Notification> pageRows = hasMore ? rows.subList(0, normalizedSize) : rows;

        List<NotificationResponse> notifications = pageRows.stream()
                .map(NotificationResponse::from)
                .toList();

        long unreadCount = repository.countByUserIdAndIsReadFalse(userId);

        Instant windowStart = beforeCreatedAt;
        if (windowStart == null && !pageRows.isEmpty()) {
            windowStart = pageRows.get(0).getCreatedAt();
        }

        return NotificationListResponse.builder()
                .notifications(notifications)
                .unreadCount(unreadCount)
                .page(normalizedPage)
                .size(normalizedSize)
                .hasMore(hasMore)
                .nextPage(hasMore ? normalizedPage + 1 : null)
                .windowCreatedAt(windowStart == null ? null : windowStart.toString())
                .build();
    }

    public List<NotificationResponse> getUserNotifications(UUID userId) {
        return repository.findInboxOrdered(userId)
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

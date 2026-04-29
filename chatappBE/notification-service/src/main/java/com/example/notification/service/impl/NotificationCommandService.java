package com.example.notification.service.impl;

import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
import com.example.notification.entity.Notification;
import com.example.notification.entity.NotificationType;
import com.example.notification.repository.NotificationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class NotificationCommandService {

    private final NotificationRepository repository;
    private final NotificationPushService pushService;

    @Transactional
    public Notification createNotification(
            UUID userId,
            NotificationType type,
            UUID referenceId,
            UUID roomId,
            UUID actorId,
            String actorDisplayName,
            String senderName,
            String preview,
            Instant eventCreatedAt
    ) {
        return createNotification(
                userId,
                type,
                referenceId,
                roomId,
                actorId,
                actorDisplayName,
                senderName,
                preview,
                eventCreatedAt,
                false
        );
    }

    @Transactional
    public Notification createNotification(
            UUID userId,
            NotificationType type,
            UUID referenceId,
            UUID roomId,
            UUID actorId,
            String actorDisplayName,
            String senderName,
            String preview,
            Instant eventCreatedAt,
            boolean actionRequired
    ) {

        Instant createdAt = eventCreatedAt == null ? Instant.now() : eventCreatedAt;
        String normalizedSenderName = actorDisplayName != null && !actorDisplayName.isBlank()
                ? actorDisplayName
                : senderName;

        Notification created = repository.save(
                Notification.builder()
                        .userId(userId)
                        .type(type)
                        .referenceId(referenceId)
                        .roomId(roomId)
                        .actorId(actorId)
                        .actorDisplayName(actorDisplayName)
                        .senderName(normalizedSenderName)
                        .preview(preview)
                        .isRead(false)
                        .actionRequired(actionRequired)
                        .createdAt(createdAt)
                        .build()
        );

        trimToLimit(userId, 200);
        pushService.pushToUser(userId, created);
        return created;
    }

    @Transactional
    public Notification markRead(UUID notificationId, UUID userId) {
        Notification noti = repository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "Notification not found"));

        if (!noti.getUserId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "Forbidden");
        }

        if (noti.isActionRequired()) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "This notification can only be resolved by action");
        }

        noti.setRead(true);

        pushService.pushUnreadCount(userId);
        return noti;
    }

    @Transactional
    public Notification resolveActionRequired(UUID notificationId, UUID userId) {
        Notification noti = repository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "Notification not found"));

        if (!noti.getUserId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "Forbidden");
        }

        noti.setRead(true);
        pushService.pushUnreadCount(userId);
        return noti;
    }

    @Transactional
    public void markAllRead(UUID userId) {
        repository.markAllReadByUserId(userId);
        pushService.pushUnreadCount(userId);
    }

    @Transactional
    public void clearRoom(UUID userId, UUID roomId) {
        markReadByRoom(userId, roomId);
    }

    @Transactional
    public void markReadByRoom(UUID userId, UUID roomId) {
        repository.markReadByUserIdAndRoomId(userId, roomId);
        pushService.pushUnreadCount(userId);
    }

    @Transactional
    public void trimToLimit(UUID userId, int max) {
        if (max <= 0) {
            return;
        }

        List<Notification> oldestFirst = repository.findByUserIdOrderByCreatedAtAsc(userId);
        if (oldestFirst.size() <= max) {
            return;
        }

        int removeCount = oldestFirst.size() - max;
        List<Notification> toDelete = new ArrayList<>(oldestFirst.subList(0, removeCount));
        repository.deleteAllInBatch(toDelete);
    }

    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        markRead(notificationId, userId);
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        markAllRead(userId);
    }
}





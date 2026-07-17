package com.chatweb.notification.service.impl;

import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import com.chatweb.notification.entity.Notification;
import com.chatweb.notification.entity.NotificationType;
import com.chatweb.notification.repository.NotificationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Service
@Slf4j
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
        runAfterCommit(() -> pushService.pushToUser(userId, created));
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

        runAfterCommit(() -> pushService.pushUnreadCount(userId));
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
        runAfterCommit(() -> pushService.pushUnreadCount(userId));
        return noti;
    }

    @Transactional
    public void markAllRead(UUID userId) {
        repository.markAllReadByUserId(userId);
        runAfterCommit(() -> pushService.pushUnreadCount(userId));
    }

    @Transactional
    public void clearRoom(UUID userId, UUID roomId) {
        markReadByRoom(userId, roomId);
    }

    @Transactional
    public void markReadByRoom(UUID userId, UUID roomId) {
        repository.markReadByUserIdAndRoomId(userId, roomId);
        runAfterCommit(() -> pushService.pushUnreadCount(userId));
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

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runSafely(action);
                }
            });
            return;
        }

        runSafely(action);
    }

    private void runSafely(Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            log.warn("notification_side_effect_failed reason={}", ex.getMessage());
        }
    }
}





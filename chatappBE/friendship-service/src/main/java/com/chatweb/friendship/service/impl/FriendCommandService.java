package com.chatweb.friendship.service.impl;

import com.chatweb.common.integration.friendship.FriendshipEventType;
import com.chatweb.common.web.response.ApiResponse;
import com.chatweb.friendship.client.UserClient;
import com.chatweb.friendship.dto.UserProfileResponse;
import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import com.chatweb.friendship.entity.Friendship;
import com.chatweb.friendship.enums.FriendshipStatus;
import com.chatweb.friendship.infrastructure.kafka.FriendshipEventProducer;
import com.chatweb.friendship.repository.FriendshipRepository;
import com.chatweb.friendship.service.IFriendCommandService;
import feign.FeignException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class FriendCommandService implements IFriendCommandService {

    private final FriendshipRepository repository;
    private final FriendshipEventProducer producer;
    private final UserClient userClient;

    private UUID low(UUID a, UUID b) { return a.compareTo(b) < 0 ? a : b; }
    private UUID high(UUID a, UUID b) { return a.compareTo(b) < 0 ? b : a; }

    private Friendship getExisting(UUID u1, UUID u2) {
        return repository.findBetweenUsers(u1, u2)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "Relationship not found"));
    }

    public void sendRequest(UUID sender, UUID receiver) {
        if (sender.equals(receiver))
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "Cannot friend yourself");

        var existing = repository.findBetweenUsers(sender, receiver);
        if (existing.isPresent()) {
            Friendship f = existing.get();
            switch (f.getStatus()) {
                case ACCEPTED -> throw new BusinessException(CommonErrorCode.BAD_REQUEST, "Already friends");
                case PENDING -> {
                    // If the other person already sent a request to us, auto-accept it
                    if (!f.getActionUserId().equals(sender)) {
                        f.setStatus(FriendshipStatus.ACCEPTED);
                        f.setActionUserId(sender);
                        f.setUpdatedAt(Instant.now());
                        publishAfterCommit(FriendshipEventType.FRIEND_REQUEST_ACCEPTED, f);
                        return;
                    }
                    // Otherwise it's our own duplicate request
                    throw new BusinessException(CommonErrorCode.BAD_REQUEST, "Request already pending");
                }
                case BLOCKED -> throw new BusinessException(CommonErrorCode.FORBIDDEN, "User is blocked");
            }
            return;
        }

        Friendship f = Friendship.builder()
                .userLow(low(sender, receiver))
                .userHigh(high(sender, receiver))
                .status(FriendshipStatus.PENDING)
                .actionUserId(sender)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        try {
            repository.saveAndFlush(f);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "Request already pending");
        }

        publishAfterCommit(FriendshipEventType.FRIEND_REQUEST_SENT, f);
    }

    @Override
    public void sendRequestByUsername(UUID sender, String username) {
        if (username == null || username.isBlank()) {
            log.debug("[FRIEND] sendRequestByUsername skipped: blank username sender={}", sender);
            return;
        }

        try {
            ApiResponse<List<UserProfileResponse>> response = userClient.searchByUsername(username.trim());
            List<UserProfileResponse> matches = response.getData();
            if (matches == null || matches.isEmpty()) {
                log.debug("[FRIEND] sendRequestByUsername skipped: no user found username={}", username);
                return;
            }

            String normalized = username.trim().toLowerCase();
            UserProfileResponse target = matches.stream()
                    .filter(user -> user.getUsername() != null
                            && user.getUsername().trim().toLowerCase().equals(normalized))
                    .findFirst()
                    .orElse(null);
            if (target == null || target.getAccountId() == null || sender.equals(target.getAccountId())) {
                log.debug("[FRIEND] sendRequestByUsername skipped: invalid target sender={} username={}", sender, username);
                return;
            }

            sendRequest(sender, target.getAccountId());
        } catch (FeignException.NotFound ignored) {
            // Keep username probing response generic: missing users are a no-op.
        }
    }

    public void accept(UUID me, UUID other) {
        Friendship f = getExisting(me, other);

        if (f.getStatus() != FriendshipStatus.PENDING)
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "Not in pending state");

        if (f.getActionUserId().equals(me))
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "Sender cannot accept own request");

        f.setStatus(FriendshipStatus.ACCEPTED);
        f.setActionUserId(me);
        f.setUpdatedAt(Instant.now());

        publishAfterCommit(FriendshipEventType.FRIEND_REQUEST_ACCEPTED, f);
    }

    public void decline(UUID me, UUID other) {
        Friendship f = getExisting(me, other);

        if (f.getStatus() != FriendshipStatus.PENDING)
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "Not in pending state");

        if (f.getActionUserId().equals(me))
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "Sender cannot decline own request");

        repository.delete(f);

        publishAfterCommit(FriendshipEventType.FRIEND_REQUEST_DECLINED, f);
    }

    public void cancel(UUID sender, UUID receiver) {
        Friendship f = getExisting(sender, receiver);

        if (f.getStatus() != FriendshipStatus.PENDING)
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "Not in pending state");

        if (!f.getActionUserId().equals(sender))
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "Only sender can cancel");

        repository.delete(f);

        publishAfterCommit(FriendshipEventType.FRIEND_REQUEST_CANCELLED, f);
    }

    public void unfriend(UUID me, UUID other) {
        Friendship f = getExisting(me, other);

        if (f.getStatus() != FriendshipStatus.ACCEPTED)
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "Not friends");

        repository.delete(f);

        publishAfterCommit(FriendshipEventType.FRIEND_UNFRIENDED, f);
    }

    public void block(UUID blocker, UUID target) {
        Friendship f = repository.findBetweenUsers(blocker, target)
                .orElse(Friendship.builder()
                        .userLow(low(blocker, target))
                        .userHigh(high(blocker, target))
                        .createdAt(Instant.now())
                        .build());

        f.setStatus(FriendshipStatus.BLOCKED);
        f.setActionUserId(blocker);
        f.setUpdatedAt(Instant.now());

        repository.save(f);

        publishAfterCommit(FriendshipEventType.FRIEND_BLOCKED, f);
    }

    public void unblock(UUID me, UUID other) {
        Friendship f = getExisting(me, other);

        if (f.getStatus() != FriendshipStatus.BLOCKED)
            throw new BusinessException(CommonErrorCode.BAD_REQUEST, "Not blocked");

        if (!f.getActionUserId().equals(me))
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "Only blocker can unblock");

        repository.delete(f);

        publishAfterCommit(FriendshipEventType.FRIEND_UNBLOCKED, f);
    }

    private void publishAfterCommit(FriendshipEventType eventType, Friendship friendship) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishSafely(eventType, friendship);
                }
            });
            return;
        }

        publishSafely(eventType, friendship);
    }

    private void publishSafely(FriendshipEventType eventType, Friendship friendship) {
        try {
            producer.publish(eventType, friendship);
        } catch (Exception ex) {
            log.warn(
                    "friendship_event_publish_failed type={} friendshipId={} reason={}",
                    eventType.value(),
                    friendship.getId(),
                    ex.getMessage()
            );
        }
    }
}







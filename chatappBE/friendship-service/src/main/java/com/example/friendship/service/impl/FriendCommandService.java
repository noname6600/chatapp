package com.example.friendship.service.impl;

import com.example.common.integration.friendship.FriendshipEventType;
import com.example.common.integration.friendship.FriendRequestEvent;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import com.example.friendship.entity.Friendship;
import com.example.friendship.enums.FriendshipStatus;
import com.example.friendship.kafka.FriendshipEventProducer;
import com.example.friendship.repository.FriendshipRepository;
import com.example.friendship.service.IFriendCommandService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class FriendCommandService implements IFriendCommandService {

    private final FriendshipRepository repository;
    private final FriendshipEventProducer producer;

    private UUID low(UUID a, UUID b) { return a.compareTo(b) < 0 ? a : b; }
    private UUID high(UUID a, UUID b) { return a.compareTo(b) < 0 ? b : a; }

    private Friendship getExisting(UUID u1, UUID u2) {
        return repository.findBetweenUsers(u1, u2)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Relationship not found"));
    }

    public void sendRequest(UUID sender, UUID receiver) {
        if (sender.equals(receiver))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Cannot friend yourself");

        repository.findBetweenUsers(sender, receiver).ifPresent(f -> {
            switch (f.getStatus()) {
                case ACCEPTED -> throw new BusinessException(ErrorCode.BAD_REQUEST, "Already friends");
                case PENDING -> throw new BusinessException(ErrorCode.BAD_REQUEST, "Request already pending");
                case BLOCKED -> throw new BusinessException(ErrorCode.FORBIDDEN, "User is blocked");
            }
        });

        Friendship f = Friendship.builder()
                .userLow(low(sender, receiver))
                .userHigh(high(sender, receiver))
                .status(FriendshipStatus.PENDING)
                .actionUserId(sender)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        repository.save(f);

        producer.publish(FriendshipEventType.FRIEND_REQUEST_SENT, f);
        producer.publishFriendRequestEvent(
            sender,
            receiver,
            f.getId(),
            FriendRequestEvent.Type.SENT
        );
    }

    public void accept(UUID me, UUID other) {
        Friendship f = getExisting(me, other);

        if (f.getStatus() != FriendshipStatus.PENDING)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Not in pending state");

        if (f.getActionUserId().equals(me))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Sender cannot accept own request");

        f.setStatus(FriendshipStatus.ACCEPTED);
        f.setActionUserId(me);
        f.setUpdatedAt(Instant.now());

        producer.publish(FriendshipEventType.FRIEND_REQUEST_ACCEPTED, f);
        producer.publishFriendRequestEvent(
            me,
            other,
            f.getId(),
            FriendRequestEvent.Type.ACCEPTED
        );
    }

    public void decline(UUID me, UUID other) {
        Friendship f = getExisting(me, other);

        if (f.getStatus() != FriendshipStatus.PENDING)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Not in pending state");

        if (f.getActionUserId().equals(me))
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Sender cannot decline own request");

        repository.delete(f);

        producer.publish(FriendshipEventType.FRIEND_REQUEST_DECLINED, f);
    }

    public void cancel(UUID sender, UUID receiver) {
        Friendship f = getExisting(sender, receiver);

        if (f.getStatus() != FriendshipStatus.PENDING)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Not in pending state");

        if (!f.getActionUserId().equals(sender))
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only sender can cancel");

        repository.delete(f);

        producer.publish(FriendshipEventType.FRIEND_REQUEST_CANCELLED, f);
    }

    public void unfriend(UUID me, UUID other) {
        Friendship f = getExisting(me, other);

        if (f.getStatus() != FriendshipStatus.ACCEPTED)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Not friends");

        repository.delete(f);

        producer.publish(FriendshipEventType.FRIEND_UNFRIENDED, f);
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

        producer.publish(FriendshipEventType.FRIEND_BLOCKED, f);
    }

    public void unblock(UUID me, UUID other) {
        Friendship f = getExisting(me, other);

        if (f.getStatus() != FriendshipStatus.BLOCKED)
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Not blocked");

        if (!f.getActionUserId().equals(me))
            throw new BusinessException(ErrorCode.FORBIDDEN, "Only blocker can unblock");

        repository.delete(f);

        producer.publish(FriendshipEventType.FRIEND_UNBLOCKED, f);
    }
}





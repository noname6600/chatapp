package com.example.friendship.service.impl;

import com.example.friendship.entity.Friendship;
import com.example.friendship.enums.FriendshipStatus;
import com.example.friendship.repository.FriendshipRepository;
import com.example.friendship.service.IFriendQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FriendQueryService implements IFriendQueryService {

    private final FriendshipRepository repository;

    private UUID other(Friendship f, UUID me) {
        return f.getUserLow().equals(me) ? f.getUserHigh() : f.getUserLow();
    }

    public List<UUID> getFriends(UUID userId) {
        return repository.findAllFriendsOf(userId).stream()
                .map(f -> other(f, userId))
                .toList();
    }

    public List<UUID> getIncomingRequests(UUID userId) {
        return repository.findAllPendingOf(userId).stream()
                .filter(f -> !f.getActionUserId().equals(userId))
                .map(f -> other(f, userId))
                .toList();
    }

    public List<UUID> getOutgoingRequests(UUID userId) {
        return repository.findAllPendingOf(userId).stream()
                .filter(f -> f.getActionUserId().equals(userId))
                .map(f -> other(f, userId))
                .toList();
    }

    public List<UUID> getBlockedByMe(UUID userId) {
        return repository.findAllBlockedOf(userId).stream()
                .filter(f -> f.getActionUserId().equals(userId))
                .map(f -> other(f, userId))
                .toList();
    }

    public List<UUID> getBlockedMe(UUID userId) {
        return repository.findAllBlockedOf(userId).stream()
                .filter(f -> !f.getActionUserId().equals(userId))
                .map(f -> other(f, userId))
                .toList();
    }

    public FriendshipStatus getStatus(UUID user1, UUID user2) {
        return repository.findBetweenUsers(user1, user2)
                .map(Friendship::getStatus)
                .orElse(null);
    }

    public long getUnreadFriendRequestCount(UUID userId) {
        return repository.countUnreadFriendRequests(userId);
    }
}


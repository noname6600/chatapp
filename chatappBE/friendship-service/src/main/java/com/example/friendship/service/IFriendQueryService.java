package com.example.friendship.service;

import com.example.friendship.enums.FriendshipStatus;

import java.util.List;
import java.util.UUID;

public interface IFriendQueryService {
    List<UUID> getFriends(UUID userId);
    List<UUID> getIncomingRequests(UUID userId);
    List<UUID> getOutgoingRequests(UUID userId);
    List<UUID> getBlockedByMe(UUID userId);
    List<UUID> getBlockedMe(UUID userId);
    FriendshipStatus getStatus(UUID user1, UUID user2);
}

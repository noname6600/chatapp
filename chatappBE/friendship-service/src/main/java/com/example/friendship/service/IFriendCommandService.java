package com.example.friendship.service;

import java.util.UUID;

public interface IFriendCommandService {
    void sendRequest(UUID sender, UUID receiver);
    void accept(UUID me, UUID other);
    void decline(UUID me, UUID other);
    void cancel(UUID sender, UUID receiver);
    void unfriend(UUID me, UUID other);
    void block(UUID blocker, UUID target);
    void unblock(UUID me, UUID other);

}

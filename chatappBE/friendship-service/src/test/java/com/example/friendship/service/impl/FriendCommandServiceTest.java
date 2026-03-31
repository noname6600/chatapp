package com.example.friendship.service.impl;

import com.example.common.integration.friendship.FriendRequestEvent;
import com.example.friendship.entity.Friendship;
import com.example.friendship.enums.FriendshipStatus;
import com.example.friendship.kafka.FriendshipEventProducer;
import com.example.friendship.repository.FriendshipRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendCommandServiceTest {

    @Mock
    private FriendshipRepository repository;

    @Mock
    private FriendshipEventProducer producer;

    @InjectMocks
    private FriendCommandService service;

    @Test
    void sendRequest_publishesFriendRequestEvent() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        when(repository.findBetweenUsers(sender, receiver)).thenReturn(Optional.empty());
        when(repository.save(any(Friendship.class))).thenAnswer(invocation -> {
            Friendship friendship = invocation.getArgument(0);
            friendship.setId(requestId);
            return friendship;
        });

        service.sendRequest(sender, receiver);

        verify(producer).publishFriendRequestEvent(
                eq(sender),
                eq(receiver),
                eq(requestId),
                eq(FriendRequestEvent.Type.SENT)
        );
    }

    @Test
    void acceptRequest_publishesFriendRequestAcceptedEvent() {
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        Friendship existing = Friendship.builder()
                .id(requestId)
                .userLow(sender.compareTo(receiver) < 0 ? sender : receiver)
                .userHigh(sender.compareTo(receiver) < 0 ? receiver : sender)
                .status(FriendshipStatus.PENDING)
                .actionUserId(sender)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(repository.findBetweenUsers(receiver, sender)).thenReturn(Optional.of(existing));

        service.accept(receiver, sender);

        verify(producer).publishFriendRequestEvent(
                eq(receiver),
                eq(sender),
                eq(requestId),
                eq(FriendRequestEvent.Type.ACCEPTED)
        );
    }
}

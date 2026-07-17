package com.chatweb.friendship.service.impl;

import com.chatweb.common.integration.friendship.FriendshipEventType;
import com.chatweb.friendship.client.UserClient;
import com.chatweb.friendship.entity.Friendship;
import com.chatweb.friendship.enums.FriendshipStatus;
import com.chatweb.friendship.infrastructure.kafka.FriendshipEventProducer;
import com.chatweb.friendship.repository.FriendshipRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Mock
    private UserClient userClient;

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

        ArgumentCaptor<Friendship> friendshipCaptor = ArgumentCaptor.forClass(Friendship.class);
        verify(producer).publish(eq(FriendshipEventType.FRIEND_REQUEST_SENT), friendshipCaptor.capture());
        Friendship published = friendshipCaptor.getValue();
        assertThat(published.getId()).isEqualTo(requestId);
        assertThat(published.getActionUserId()).isEqualTo(sender);
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

        verify(producer).publish(eq(FriendshipEventType.FRIEND_REQUEST_ACCEPTED), eq(existing));
    }
}

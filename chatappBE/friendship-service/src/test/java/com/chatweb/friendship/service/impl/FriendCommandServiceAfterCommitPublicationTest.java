package com.chatweb.friendship.service.impl;

import com.chatweb.common.integration.friendship.FriendshipEventType;
import com.chatweb.friendship.client.UserClient;
import com.chatweb.friendship.entity.Friendship;
import com.chatweb.friendship.infrastructure.kafka.FriendshipEventProducer;
import com.chatweb.friendship.repository.FriendshipRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendCommandServiceAfterCommitPublicationTest {

    @Mock
    private FriendshipRepository repository;

    @Mock
    private FriendshipEventProducer producer;

    @Mock
    private UserClient userClient;

    @AfterEach
    void cleanupTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void sendRequest_publishesAfterCommit_whenTransactionSynchronizationActive() {
        FriendCommandService service = new FriendCommandService(repository, producer, userClient);

        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();

        when(repository.findBetweenUsers(sender, receiver)).thenReturn(Optional.empty());
        when(repository.save(any(Friendship.class))).thenAnswer(invocation -> {
            Friendship saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        service.sendRequest(sender, receiver);

        verify(producer, never()).publish(eq(FriendshipEventType.FRIEND_REQUEST_SENT), any(Friendship.class));

        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(producer).publish(eq(FriendshipEventType.FRIEND_REQUEST_SENT), any(Friendship.class));
    }

    @Test
    void sendRequest_publishesImmediately_whenNoTransactionSynchronization() {
        FriendCommandService service = new FriendCommandService(repository, producer, userClient);

        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();

        when(repository.findBetweenUsers(sender, receiver)).thenReturn(Optional.empty());
        when(repository.save(any(Friendship.class))).thenAnswer(invocation -> {
            Friendship saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        service.sendRequest(sender, receiver);

        verify(producer).publish(eq(FriendshipEventType.FRIEND_REQUEST_SENT), any(Friendship.class));
    }
}

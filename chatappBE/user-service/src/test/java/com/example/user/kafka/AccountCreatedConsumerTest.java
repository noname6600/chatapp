package com.example.user.kafka;

import com.example.common.integration.kafka.event.AccountCreatedEvent;
import com.example.common.integration.account.AccountCreatedPayload;
import com.example.user.entity.UserProfile;
import com.example.user.repository.UserProfileRepository;
import com.example.user.utils.AvatarGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountCreatedConsumerTest {

    private UserProfileRepository repository;
    private AvatarGenerator avatarGenerator;
    private AccountCreatedConsumer consumer;

    @BeforeEach
    void setUp() {
        repository = mock(UserProfileRepository.class);
        avatarGenerator = mock(AvatarGenerator.class);
        consumer = new AccountCreatedConsumer(repository, avatarGenerator);
    }

    @Test
    void listen_returnsWithoutSaving_whenProfileAlreadyExists() {
        UUID accountId = UUID.randomUUID();
        AccountCreatedEvent event = buildEvent(accountId, "existing@example.com");

        when(repository.existsById(accountId)).thenReturn(true);

        consumer.listen(event);

        verify(repository, never()).save(any(UserProfile.class));
    }

    @Test
    void listen_handlesConcurrentDuplicateInsert_byReturningCanonicalProfile() {
        UUID accountId = UUID.randomUUID();
        AccountCreatedEvent event = buildEvent(accountId, "race@example.com");

        when(repository.existsById(accountId))
                .thenReturn(false)
                .thenReturn(true);
        when(repository.existsByUsername("race")).thenReturn(false);
        when(avatarGenerator.generate(accountId, "race")).thenReturn("avatar://race");
        when(repository.save(any(UserProfile.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatCode(() -> consumer.listen(event)).doesNotThrowAnyException();
    }

    @Test
    void listen_retriesWithNextUsernameSuffix_whenUsernameConflicts() {
        UUID accountId = UUID.randomUUID();
        AccountCreatedEvent event = buildEvent(accountId, "john@example.com");

        when(repository.existsById(accountId)).thenReturn(false);
        when(repository.existsByUsername("john")).thenReturn(false);
        when(repository.existsByUsername("john1")).thenReturn(false);
        when(avatarGenerator.generate(accountId, "john")).thenReturn("avatar://john");
        when(avatarGenerator.generate(accountId, "john1")).thenReturn("avatar://john1");
        when(repository.save(any(UserProfile.class)))
                .thenThrow(new DataIntegrityViolationException("username conflict"))
                .thenReturn(UserProfile.builder()
                        .accountId(accountId)
                        .username("john1")
                        .displayName("john1")
                        .avatarUrl("avatar://john1")
                        .avatarPublicId("generated:" + accountId)
                        .aboutMe("")
                        .backgroundColor("#ffffff")
                        .build());

        assertThatCode(() -> consumer.listen(event)).doesNotThrowAnyException();
        verify(repository, times(2)).save(any(UserProfile.class));
    }

    private AccountCreatedEvent buildEvent(UUID accountId, String email) {
        AccountCreatedPayload payload = new AccountCreatedPayload(accountId, email);
        return new AccountCreatedEvent("auth-service", UUID.randomUUID(), java.time.Instant.now(), payload);
    }
}

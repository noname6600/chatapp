package com.chatweb.user.application;

import com.chatweb.common.integration.account.AccountCreatedPayload;
import com.chatweb.user.entity.UserProfile;
import com.chatweb.user.repository.UserProfileRepository;
import com.chatweb.user.utils.AvatarGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserKafkaAccountCreatedApplicationServiceTest {

    @Mock
    private UserProfileRepository repository;

    @Mock
    private AvatarGenerator avatarGenerator;

    @InjectMocks
    private UserKafkaAccountCreatedApplicationService service;

    @Test
    void handleAccountCreated_createsProfileFromEventPayload() {
        UUID accountId = UUID.randomUUID();
        AccountCreatedPayload payload = new AccountCreatedPayload(accountId, "John.Doe+1@example.com");

        when(repository.existsById(accountId)).thenReturn(false);
        when(repository.existsByUsername("john.doe1")).thenReturn(false);
        when(avatarGenerator.generate(accountId, "john.doe1"))
                .thenReturn("https://res.cloudinary.com/demo/image/upload/v1/generated/john.doe1.png");

        service.handleAccountCreated(payload);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(repository).save(captor.capture());
        UserProfile saved = captor.getValue();

        assertThat(saved.getAccountId()).isEqualTo(accountId);
        assertThat(saved.getUsername()).isEqualTo("john.doe1");
        assertThat(saved.getDisplayName()).isEqualTo("john.doe1");
        assertThat(saved.getAvatarPublicId()).isEqualTo("generated:" + accountId);
    }

    @Test
    void handleAccountCreated_retriesWithSuffixWhenBaseUsernameTaken() {
        UUID accountId = UUID.randomUUID();
        AccountCreatedPayload payload = new AccountCreatedPayload(accountId, "alice@example.com");

        when(repository.existsById(accountId)).thenReturn(false);
        when(repository.existsByUsername("alice")).thenReturn(true);
        when(repository.existsByUsername("alice1")).thenReturn(false);
        when(avatarGenerator.generate(accountId, "alice1"))
                .thenReturn("https://res.cloudinary.com/demo/image/upload/v1/generated/alice1.png");

        service.handleAccountCreated(payload);

        ArgumentCaptor<UserProfile> captor = ArgumentCaptor.forClass(UserProfile.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("alice1");
        verify(avatarGenerator).generate(accountId, "alice1");
    }

    @Test
    void handleAccountCreated_skipsWhenProfileAlreadyExists() {
        UUID accountId = UUID.randomUUID();
        AccountCreatedPayload payload = new AccountCreatedPayload(accountId, "existing@example.com");

        when(repository.existsById(accountId)).thenReturn(true);

        service.handleAccountCreated(payload);

        verify(repository, never()).save(any(UserProfile.class));
        verify(avatarGenerator, never()).generate(eq(accountId), any());
    }
}

package com.example.user.service.impl;

import com.example.common.redis.api.ITimeRedisCache;
import com.example.common.redis.api.ITimeRedisCacheManager;
import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
import com.example.user.dto.UpdateProfileRequest;
import com.example.user.entity.UserProfile;
import com.example.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class UserProfileServiceUpdateProfileTest {

    private UserProfileRepository repo;
    private ITimeRedisCacheManager cacheManager;
    private ITimeRedisCache cache;
    private UserProfileService service;

    @BeforeEach
    void setUp() {
        repo = Mockito.mock(UserProfileRepository.class);
        cacheManager = Mockito.mock(ITimeRedisCacheManager.class);
        cache = Mockito.mock(ITimeRedisCache.class);

        when(cacheManager.getCache("user-profile")).thenReturn(cache);

        service = new UserProfileService(repo, cacheManager);
        service.init();
    }

    @Test
    void updateProfile_updatesDisplayNameAndUsername_whenValid() {
        UUID accountId = UUID.randomUUID();
        UserProfile profile = baseProfile(accountId, "old_name", "Old Name");
        when(repo.findById(accountId)).thenReturn(Optional.of(profile));
        when(repo.findByUsernameIgnoreCase("new_name")).thenReturn(Optional.empty());

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setUsername("new_name");
        request.setDisplayName("New Name");

        service.updateProfile(accountId, request);

        assertThat(profile.getUsername()).isEqualTo("new_name");
        assertThat(profile.getDisplayName()).isEqualTo("New Name");
    }

    @Test
    void updateProfile_rejectsInvalidUsernameFormat() {
        UUID accountId = UUID.randomUUID();
        UserProfile profile = baseProfile(accountId, "old_name", "Old Name");
        when(repo.findById(accountId)).thenReturn(Optional.of(profile));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setUsername("bad username");

        assertThatThrownBy(() -> service.updateProfile(accountId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.VALIDATION_ERROR);
    }

    @Test
    void updateProfile_rejectsUsernameConflict() {
        UUID accountId = UUID.randomUUID();
        UUID otherAccountId = UUID.randomUUID();
        UserProfile profile = baseProfile(accountId, "old_name", "Old Name");
        UserProfile otherProfile = baseProfile(otherAccountId, "taken_name", "Other Name");

        when(repo.findById(accountId)).thenReturn(Optional.of(profile));
        when(repo.findByUsernameIgnoreCase("taken_name")).thenReturn(Optional.of(otherProfile));

        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setUsername("taken_name");

        assertThatThrownBy(() -> service.updateProfile(accountId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.CONFLICT);
    }

    private UserProfile baseProfile(UUID accountId, String username, String displayName) {
        return UserProfile.builder()
                .accountId(accountId)
                .username(username)
                .displayName(displayName)
                .build();
    }
}


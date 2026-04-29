package com.example.user.service.impl;

import com.example.common.redis.api.ITimeRedisCache;
import com.example.common.redis.api.ITimeRedisCacheManager;
import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
import com.example.user.dto.UserProfileResponse;
import com.example.user.entity.UserProfile;
import com.example.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserProfileServiceGetSelfTest {

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
    void getSelf_returnsCachedProfile_whenCacheHit() {
        UUID accountId = UUID.randomUUID();
        UserProfileResponse cached = new UserProfileResponse(
                accountId,
                "u_cache",
                "Cached User",
                "https://example.com/avatar.png",
                "about",
                "#AABBCC"
        );

        when(cache.get(accountId, UserProfileResponse.class)).thenReturn(cached);

        UserProfileResponse response = service.getSelf(accountId);

        assertThat(response).isEqualTo(cached);
        verify(repo, never()).findById(any());
    }

    @Test
    void getSelf_loadsFromRepositoryAndCaches_whenCacheMiss() {
        UUID accountId = UUID.randomUUID();
        UserProfile profile = baseProfile(accountId);

        when(cache.get(accountId, UserProfileResponse.class)).thenReturn(null);
        when(repo.findById(accountId)).thenReturn(Optional.of(profile));

        UserProfileResponse response = service.getSelf(accountId);

        assertThat(response.getAccountId()).isEqualTo(accountId);
        assertThat(response.getUsername()).isEqualTo("user_name");
        verify(repo).findById(accountId);
        verify(cache).put(eq(accountId), any(UserProfileResponse.class), any(Duration.class));
    }

    @Test
    void getSelf_fallsBackToRepository_whenCacheGetThrows() {
        UUID accountId = UUID.randomUUID();
        UserProfile profile = baseProfile(accountId);

        when(cache.get(accountId, UserProfileResponse.class))
                .thenThrow(new RuntimeException("Unable to connect to Redis"));
        when(repo.findById(accountId)).thenReturn(Optional.of(profile));

        UserProfileResponse response = service.getSelf(accountId);

        assertThat(response.getAccountId()).isEqualTo(accountId);
        verify(repo).findById(accountId);
        verify(cache).put(eq(accountId), any(UserProfileResponse.class), any(Duration.class));
    }

    @Test
    void getSelf_preservesNotFoundSemantics_whenProfileMissingAfterCacheFailure() {
        UUID accountId = UUID.randomUUID();

        when(cache.get(accountId, UserProfileResponse.class))
                .thenThrow(new RuntimeException("Unable to connect to Redis"));
        when(repo.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSelf(accountId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND);
    }

    private UserProfile baseProfile(UUID accountId) {
        return UserProfile.builder()
                .accountId(accountId)
                .username("user_name")
                .displayName("User Name")
                .avatarUrl("https://example.com/avatar.png")
                .aboutMe("about")
                .backgroundColor("#FFFFFF")
                .build();
    }
}



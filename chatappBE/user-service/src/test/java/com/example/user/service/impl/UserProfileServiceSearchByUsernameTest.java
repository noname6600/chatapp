package com.example.user.service.impl;

import com.example.common.redis.api.ITimeRedisCache;
import com.example.common.redis.api.ITimeRedisCacheManager;
import com.example.user.dto.UserBasicProfile;
import com.example.user.entity.UserProfile;
import com.example.user.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class UserProfileServiceSearchByUsernameTest {

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
    void searchByUsername_returnsList_whenUserExists() {
        UUID accountId = UUID.randomUUID();
        UserProfile profile = UserProfile.builder()
                .accountId(accountId)
                .username("alice")
                .displayName("Alice")
                .avatarUrl("https://example.com/alice.png")
                .build();

        when(repo.findTop10ByUsernameContainingIgnoreCaseOrderByUsernameAsc("Alice"))
                .thenReturn(List.of(profile));

        List<UserBasicProfile> response = service.searchByUsername("Alice");

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getAccountId()).isEqualTo(accountId);
        assertThat(response.get(0).getUsername()).isEqualTo("alice");
        assertThat(response.get(0).getDisplayName()).isEqualTo("Alice");
        assertThat(response.get(0).getAvatarUrl()).isEqualTo("https://example.com/alice.png");
    }

    @Test
    void searchByUsername_returnsEmptyList_whenUserMissing() {
        when(repo.findTop10ByUsernameContainingIgnoreCaseOrderByUsernameAsc("missing"))
                .thenReturn(List.of());

        List<UserBasicProfile> response = service.searchByUsername("missing");

        assertThat(response).isEmpty();
    }
}

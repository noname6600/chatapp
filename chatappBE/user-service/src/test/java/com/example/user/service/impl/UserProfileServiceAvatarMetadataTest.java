package com.example.user.service.impl;

import com.example.common.redis.api.ITimeRedisCache;
import com.example.common.redis.api.ITimeRedisCacheManager;
import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
import com.example.user.dto.AvatarMetadataRequest;
import com.example.user.dto.AvatarUploadResponse;
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

class UserProfileServiceAvatarMetadataTest {

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
    void applyAvatarMetadata_rejectsInvalidFormat() {
        UUID accountId = UUID.randomUUID();
        when(repo.findById(accountId)).thenReturn(Optional.of(baseProfile(accountId)));

        AvatarMetadataRequest request = validRequest();
        request.setFormat("gif");

        assertThatThrownBy(() -> service.applyAvatarMetadata(accountId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.VALIDATION_ERROR);
    }

    @Test
    void applyAvatarMetadata_updatesProfileForValidPayload() {
        UUID accountId = UUID.randomUUID();
        UserProfile profile = baseProfile(accountId);
        when(repo.findById(accountId)).thenReturn(Optional.of(profile));

        AvatarMetadataRequest request = validRequest();

        AvatarUploadResponse response = service.applyAvatarMetadata(accountId, request);

        assertThat(profile.getAvatarPublicId()).isEqualTo("user/avatar/new");
        assertThat(profile.getAvatarUrl()).isEqualTo("https://res.cloudinary.com/demo/image/upload/v1/user/avatar/new.png");
        assertThat(response.getAvatarUrl()).isEqualTo("https://res.cloudinary.com/demo/image/upload/v1/user/avatar/new.png");
    }

    private UserProfile baseProfile(UUID accountId) {
        return UserProfile.builder()
                .accountId(accountId)
                .username("u1")
                .displayName("User")
                .avatarUrl("https://res.cloudinary.com/demo/image/upload/v1/user/avatar/old.png")
                .avatarPublicId("user/avatar/old")
                .build();
    }

    private AvatarMetadataRequest validRequest() {
        AvatarMetadataRequest request = new AvatarMetadataRequest();
        request.setPublicId("user/avatar/new");
        request.setSecureUrl("https://res.cloudinary.com/demo/image/upload/v1/user/avatar/new.png");
        request.setResourceType("image");
        request.setFormat("png");
        request.setBytes(2048L);
        request.setWidth(200);
        request.setHeight(200);
        return request;
    }
}



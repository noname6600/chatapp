package com.example.user.service;

import com.example.user.dto.AvatarUploadResponse;
import com.example.user.dto.AvatarMetadataRequest;
import com.example.user.dto.UpdateProfileRequest;
import com.example.user.dto.UserBasicProfile;
import com.example.user.dto.UserProfileResponse;
import java.util.List;
import java.util.UUID;

public interface IUserProfileService {
    UserProfileResponse getSelf(UUID accountId);
    UserProfileResponse getOther(UUID targetId);
    boolean existsByAccountId(UUID accountId);
    void updateProfile(UUID accountId, UpdateProfileRequest req);
    List<UserBasicProfile> getMany(List<UUID> ids);
    UserBasicProfile searchByUsername(String username);
    AvatarUploadResponse applyAvatarMetadata(UUID accountId, AvatarMetadataRequest request);
}

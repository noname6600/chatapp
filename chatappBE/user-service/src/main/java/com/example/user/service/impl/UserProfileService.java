package com.example.user.service.impl;

import com.example.upload.contract.UploadAssetMetadata;
import com.example.common.redis.api.ITimeRedisCache;
import com.example.common.redis.api.ITimeRedisCacheManager;
import com.example.common.core.exception.BusinessException;
import com.example.common.core.exception.CommonErrorCode;
import com.example.user.exception.UserErrorCode;
import com.example.user.dto.*;
import com.example.user.entity.UserProfile;
import com.example.user.repository.UserProfileRepository;
import com.example.user.service.IUserProfileService;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserProfileService implements IUserProfileService {

    private static final Duration PROFILE_TTL = Duration.ofMinutes(5);

    private static final Pattern HEX_COLOR =
            Pattern.compile("^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$");

        private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[a-zA-Z0-9.,_]{3,30}$");

    private static final long AVATAR_MAX_BYTES = 5L * 1024 * 1024;

    private final UserProfileRepository repo;
    private final ITimeRedisCacheManager cacheManager;

    private ITimeRedisCache profileCache;

    @PostConstruct
    public void init() {

        Cache raw = cacheManager.getCache("user-profile");

        if (!(raw instanceof ITimeRedisCache cache)) {
            throw new IllegalStateException("Cache 'user-profile' is not ITimeRedisCache");
        }

        this.profileCache = cache;
    }

    @Override
    public UserProfileResponse getSelf(UUID accountId) {
        return getProfile(accountId);
    }

    @Override
    public UserProfileResponse getOther(UUID targetId) {
        return getProfile(targetId);
    }

    @Override
    public boolean existsByAccountId(UUID accountId) {
        return repo.existsById(accountId);
    }

    private UserProfileResponse getProfile(UUID id) {

        UserProfileResponse cached = safeGetProfileFromCache(id);
        if (cached != null) {
            return cached;
        }

        UserProfile profile = repo.findById(id)
                .orElseThrow(() ->
                        new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "User not found")
                );

        UserProfileResponse response = toResponse(profile);

        safePutProfileToCache(id, response);

        return response;
    }

    private UserProfileResponse safeGetProfileFromCache(UUID accountId) {
        try {
            return profileCache.get(accountId, UserProfileResponse.class);
        } catch (RuntimeException ex) {
            log.warn(
                    "[USER-PROFILE-CACHE] operation=cache_get_failed accountId={} errorType={} message={}",
                    accountId,
                    ex.getClass().getSimpleName(),
                    ex.getMessage()
            );
            return null;
        }
    }

    private void safePutProfileToCache(UUID accountId, UserProfileResponse response) {
        try {
            profileCache.put(accountId, response, PROFILE_TTL);
        } catch (RuntimeException ex) {
            log.warn(
                    "[USER-PROFILE-CACHE] operation=cache_put_failed accountId={} errorType={} message={}",
                    accountId,
                    ex.getClass().getSimpleName(),
                    ex.getMessage()
            );
        }
    }

    @Override
    public void updateProfile(UUID accountId, UpdateProfileRequest req) {

        UserProfile profile = repo.findById(accountId)
                .orElseThrow(() ->
                        new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "User not found")
                );

        if (req.getUsername() != null) {

            String newUsername = req.getUsername().trim();

            if (!newUsername.isEmpty() && !newUsername.equals(profile.getUsername())) {

                if (!USERNAME_PATTERN.matcher(newUsername).matches()) {
                    throw new BusinessException(
                            CommonErrorCode.VALIDATION_ERROR,
                            "Username must be 3-30 chars and only include letters, numbers, dot(.), underscore(_), comma(,)"
                    );
                }

                boolean takenByAnother = repo.findByUsernameIgnoreCase(newUsername)
                        .filter(existing -> !existing.getAccountId().equals(accountId))
                        .isPresent();

                if (takenByAnother) {
                    throw new BusinessException(CommonErrorCode.CONFLICT, "Username already taken");
                }

                profile.setUsername(newUsername);
            }
        }

        if (req.getDisplayName() != null) {

            String dn = req.getDisplayName().trim();

            if (!dn.isEmpty()) {
                profile.setDisplayName(dn);
            }
        }

        if (req.getAboutMe() != null) {

            String about = req.getAboutMe().trim();

            profile.setAboutMe(about.isEmpty() ? null : about);
        }

        if (req.getBackgroundColor() != null) {

            String color = req.getBackgroundColor().trim();

            if (!HEX_COLOR.matcher(color).matches()) {
                throw new BusinessException(CommonErrorCode.BAD_REQUEST, "Invalid background color");
            }

            profile.setBackgroundColor(color);
        }

        profileCache.evict(accountId);
    }

    @Override
    public AvatarUploadResponse applyAvatarMetadata(UUID accountId, AvatarMetadataRequest request) {

        UserProfile profile = repo.findById(accountId)
                .orElseThrow(() ->
                        new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND, "User not found")
                );

        UploadAssetMetadata metadata = toUploadAssetMetadata(request);

        validateAvatarMetadata(metadata);

        profile.setAvatarUrl(metadata.getSecureUrl().trim());
        profile.setAvatarPublicId(metadata.getPublicId().trim());

        profileCache.evict(accountId);

        return new AvatarUploadResponse(metadata.getSecureUrl().trim());
    }

    @Override
    public List<UserBasicProfile> getMany(List<UUID> ids) {
        List<UserProfile> profiles = repo.findAllById(ids);

        if (log.isDebugEnabled()) {
            profiles.forEach(profile ->
                log.debug(
                    "[USER-BULK] DB accountId={} username={} displayName={}",
                    profile.getAccountId(),
                    profile.getUsername(),
                    profile.getDisplayName()
                )
            );
        }

        return profiles.stream()
            .map(this::toBasicResponse)
            .toList();
    }

    @Override
    public List<UserBasicProfile> searchByUsername(String username) {
        List<UserProfile> profiles =
                repo.findTop10ByUsernameContainingIgnoreCaseOrderByUsernameAsc(username);

        return profiles.stream()
                .map(this::toBasicResponse)
                .toList();
    }

    private UploadAssetMetadata toUploadAssetMetadata(AvatarMetadataRequest request) {
        return UploadAssetMetadata.builder()
                .publicId(request.getPublicId())
                .secureUrl(request.getSecureUrl())
                .resourceType(request.getResourceType())
                .format(request.getFormat())
                .bytes(request.getBytes())
                .width(request.getWidth())
                .height(request.getHeight())
                .build();
    }

    private void validateAvatarMetadata(UploadAssetMetadata request) {

        String resourceType = request.getResourceType() == null
                ? ""
                : request.getResourceType().trim().toLowerCase(Locale.ROOT);

        if (!"image".equals(resourceType)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Avatar resourceType must be image");
        }

        if (request.getBytes() > AVATAR_MAX_BYTES) {
            throw new BusinessException(UserErrorCode.ATTACHMENT_TOO_LARGE, "Avatar too large (max 5MB)");
        }

        String format = request.getFormat() == null
                ? ""
                : request.getFormat().trim().toLowerCase(Locale.ROOT);

        if (!("jpg".equals(format)
                || "jpeg".equals(format)
                || "png".equals(format)
                || "webp".equals(format))) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Unsupported avatar format");
        }

        if (!request.getSecureUrl().startsWith("https://res.cloudinary.com/")) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Avatar secureUrl is invalid");
        }
    }

    private UserProfileResponse toResponse(UserProfile p) {

        return new UserProfileResponse(
                p.getAccountId(),
                p.getUsername(),
                p.getDisplayName(),
                p.getAvatarUrl(),
                p.getAboutMe(),
                p.getBackgroundColor()
        );
    }

    private UserBasicProfile toBasicResponse(UserProfile p) {

        return new UserBasicProfile(
                p.getAccountId(),
                p.getUsername(),
                p.getDisplayName(),
                p.getAvatarUrl()
        );
    }
}






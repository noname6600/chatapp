package com.example.user.controller;

import com.example.common.web.controller.BaseController;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import com.example.common.web.response.ApiResponse;
import com.example.user.dto.AvatarMetadataRequest;
import com.example.user.dto.AvatarUploadResponse;
import com.example.user.dto.UpdateProfileRequest;
import com.example.user.dto.UserBasicProfile;
import com.example.user.dto.UserProfileResponse;
import com.example.user.service.IUserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class UserProfileController extends BaseController {

    private final IUserProfileService service;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> me(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(service.getSelf(currentUserId(jwt)))
        );
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<UserBasicProfile>>> search(
            @RequestParam(required = false) String username
    ) {
        if (username == null || username.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Username parameter is required and cannot be blank");
        }

        List<UserBasicProfile> profiles = service.searchByUsername(username.trim());
        return ResponseEntity.ok(ApiResponse.success(profiles));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUser(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(service.getOther(id))
        );
    }

    @GetMapping("/internal/{accountId}/exists")
    public ResponseEntity<ApiResponse<Boolean>> existsByAccountId(
            @PathVariable UUID accountId
    ) {
        return ResponseEntity.ok(ApiResponse.success(service.existsByAccountId(accountId)));
    }

    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<Void>> update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProfileRequest req
    ) {
        service.updateProfile(currentUserId(jwt), req);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/bulk")
    public ResponseEntity<ApiResponse<List<UserBasicProfile>>> getUsersBulk(
            @RequestBody List<UUID> ids,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (log.isDebugEnabled()) {
            log.debug("[USER-BULK] Request idsCount={}", ids == null ? 0 : ids.size());
        }

        List<UserBasicProfile> profiles = service.getMany(ids);

        if (log.isDebugEnabled()) {
            profiles.forEach(profile ->
                    log.debug(
                            "[USER-BULK] Response accountId={} username={} displayName={}",
                            profile.getAccountId(),
                            profile.getUsername(),
                            profile.getDisplayName()
                    )
            );
        }

        return ResponseEntity.ok(
                ApiResponse.success(profiles)
        );
    }

    @PostMapping("/me/avatar")
    public ResponseEntity<ApiResponse<AvatarUploadResponse>> applyAvatarMetadata(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AvatarMetadataRequest request
    ) {
        AvatarUploadResponse response =
                service.applyAvatarMetadata(currentUserId(jwt), request);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

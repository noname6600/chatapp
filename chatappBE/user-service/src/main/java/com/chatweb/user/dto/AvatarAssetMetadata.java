package com.chatweb.user.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * User-service-owned boundary model for avatar metadata confirmation.
 *
 * This intentionally mirrors only the fields user-service needs for
 * avatar validation and persistence, without importing upload-service classes.
 */
@Getter
@Builder
public class AvatarAssetMetadata {
    private final String publicId;
    private final String secureUrl;
    private final String resourceType;
    private final String format;
    private final Long bytes;
    private final Integer width;
    private final Integer height;
}

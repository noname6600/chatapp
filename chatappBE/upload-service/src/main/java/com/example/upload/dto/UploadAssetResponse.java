package com.example.upload.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UploadAssetResponse {
    private final String purpose;
    private final String publicId;
    private final String secureUrl;
    private final String resourceType;
    private final String format;
    private final Long bytes;
    private final Integer width;
    private final Integer height;
    private final Integer duration;
    private final String originalFilename;
}

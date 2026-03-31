package com.example.upload.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PrepareUploadResponse {
    private final String purpose;
    private final String cloudName;
    private final String apiKey;
    private final String uploadUrl;
    private final long timestamp;
    private final String signature;
    private final String folder;
    private final String publicId;
    private final long maxBytes;
    private final List<String> allowedFormats;
}

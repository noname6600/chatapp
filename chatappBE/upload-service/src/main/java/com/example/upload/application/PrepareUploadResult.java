package com.example.upload.application;

import java.util.List;

public record PrepareUploadResult(
        String purpose,
        String uploadUrl,
        String publicUrl,
        String assetKey,
        String cloudName,
        String apiKey,
        long timestamp,
        String signature,
        String folder,
        long maxBytes,
        List<String> allowedFormats
) {
}

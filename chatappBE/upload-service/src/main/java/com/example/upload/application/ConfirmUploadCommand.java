package com.example.upload.application;

import com.example.upload.domain.UploadPurpose;

public record ConfirmUploadCommand(
        String userId,
        String assetKey,
        String publicUrl,
        UploadPurpose purpose,
        String resourceType,
        String format,
        Long bytes,
        Integer width,
        Integer height,
        Integer duration,
        String originalFilename
) {
}

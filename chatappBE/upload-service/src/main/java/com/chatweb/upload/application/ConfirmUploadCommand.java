package com.chatweb.upload.application;

import com.chatweb.upload.domain.UploadPurpose;

public record ConfirmUploadCommand(
        String userId,
        String assetKey,
        String prepareToken,
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

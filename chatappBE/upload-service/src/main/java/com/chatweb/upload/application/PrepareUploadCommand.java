package com.chatweb.upload.application;

import com.chatweb.upload.domain.UploadPurpose;

public record PrepareUploadCommand(
        String userId,
        UploadPurpose purpose,
        String filename,
        long sizeBytes
) {
}

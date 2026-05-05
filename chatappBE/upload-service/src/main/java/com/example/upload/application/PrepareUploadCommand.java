package com.example.upload.application;

import com.example.upload.domain.UploadPurpose;

public record PrepareUploadCommand(
        String userId,
        UploadPurpose purpose,
        String filename,
        long sizeBytes
) {
}

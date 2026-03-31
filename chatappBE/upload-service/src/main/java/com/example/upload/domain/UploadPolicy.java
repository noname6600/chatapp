package com.example.upload.domain;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Set;

@Getter
@Builder
public class UploadPolicy {
    private final String folder;
    private final long maxBytes;
    private final Set<String> allowedFormats;
    private final Set<String> allowedResourceTypes;
    private final List<String> allowedFormatsForClient;
}

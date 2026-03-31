package com.example.upload.service;

import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import com.example.upload.config.UploadPolicyProperties;
import com.example.upload.domain.UploadPolicy;
import com.example.upload.domain.UploadPurpose;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UploadPolicyRegistry {

    private final UploadPolicyProperties properties;
    private final Map<UploadPurpose, UploadPolicy> policies = new EnumMap<>(UploadPurpose.class);

    @PostConstruct
    public void init() {
        policies.put(UploadPurpose.CHAT_ATTACHMENT, fromProperties(properties.getChatAttachment(), UploadPurpose.CHAT_ATTACHMENT));
        policies.put(UploadPurpose.USER_AVATAR, fromProperties(properties.getUserAvatar(), UploadPurpose.USER_AVATAR));
    }

    public UploadPolicy getOrThrow(UploadPurpose purpose) {
        if (purpose == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "purpose is required");
        }

        UploadPolicy policy = policies.get(purpose);
        if (policy == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Unsupported upload purpose");
        }

        return policy;
    }

    private UploadPolicy fromProperties(UploadPolicyProperties.Purpose source, UploadPurpose purpose) {
        if (source == null || source.getFolder() == null || source.getFolder().isBlank()) {
            throw new IllegalStateException("Missing folder policy for " + purpose.value());
        }

        if (source.getMaxBytes() == null || source.getMaxBytes() <= 0) {
            throw new IllegalStateException("Missing max-bytes policy for " + purpose.value());
        }

        Set<String> formats = normalize(source.getAllowedFormats());
        Set<String> resourceTypes = normalize(source.getAllowedResourceTypes());

        if (formats.isEmpty()) {
            throw new IllegalStateException("Missing allowed-formats policy for " + purpose.value());
        }

        if (resourceTypes.isEmpty()) {
            throw new IllegalStateException("Missing allowed-resource-types policy for " + purpose.value());
        }

        return UploadPolicy.builder()
                .folder(source.getFolder())
                .maxBytes(source.getMaxBytes())
                .allowedFormats(formats)
                .allowedResourceTypes(resourceTypes)
                .allowedFormatsForClient(source.getAllowedFormats())
                .build();
    }

    private Set<String> normalize(List<String> values) {
        if (values == null) {
            return Set.of();
        }

        return values.stream()
                .map(v -> v == null ? "" : v.trim().toLowerCase())
                .filter(v -> !v.isBlank())
                .collect(Collectors.toSet());
    }
}

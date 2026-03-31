package com.example.upload.service;

import com.cloudinary.Cloudinary;
import com.example.common.core.upload.UploadAssetMetadata;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import com.example.upload.dto.ConfirmUploadRequest;
import com.example.upload.dto.PrepareUploadRequest;
import com.example.upload.dto.PrepareUploadResponse;
import com.example.upload.dto.UploadAssetResponse;
import com.example.upload.domain.UploadPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadSigningService {

    private final Cloudinary cloudinary;
    private final UploadPolicyRegistry policyRegistry;

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    @Value("${cloudinary.api-key}")
    private String apiKey;

    @Value("${cloudinary.api-secret}")
    private String apiSecret;

    public PrepareUploadResponse prepare(PrepareUploadRequest request) {
        UploadPolicy policy = policyRegistry.getOrThrow(request.getPurpose());

        long timestamp = Instant.now().getEpochSecond();
        String publicId = policy.getFolder() + "/" + UUID.randomUUID();

        Map<String, Object> paramsToSign = new HashMap<>();
        paramsToSign.put("folder", policy.getFolder());
        paramsToSign.put("public_id", publicId);
        paramsToSign.put("timestamp", timestamp);

        String signature = cloudinary.apiSignRequest(paramsToSign, apiSecret);

        return PrepareUploadResponse.builder()
                .purpose(request.getPurpose().value())
                .cloudName(cloudName)
                .apiKey(apiKey)
                .uploadUrl("https://api.cloudinary.com/v1_1/" + cloudName + "/auto/upload")
                .timestamp(timestamp)
                .signature(signature)
                .folder(policy.getFolder())
                .publicId(publicId)
                .maxBytes(policy.getMaxBytes())
                .allowedFormats(policy.getAllowedFormatsForClient())
                .build();
    }

    public UploadAssetResponse confirm(ConfirmUploadRequest request) {
        UploadPolicy policy = policyRegistry.getOrThrow(request.getPurpose());

        String format = lower(request.getFormat());
        String resourceType = lower(request.getResourceType());
        String publicId = request.getPublicId() == null ? "" : request.getPublicId().trim();
        String secureUrl = request.getSecureUrl() == null ? "" : request.getSecureUrl().trim();

        if (!publicId.startsWith(policy.getFolder() + "/")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "publicId does not match purpose folder policy");
        }

        if (!secureUrl.startsWith("https://res.cloudinary.com/" + cloudName + "/")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "secureUrl is not from configured cloud");
        }

        if (!policy.getAllowedResourceTypes().contains(resourceType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "resourceType is not allowed for this purpose");
        }

        if (!policy.getAllowedFormats().contains(format)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "format is not allowed for this purpose");
        }

        if (request.getBytes() > policy.getMaxBytes()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "file exceeds maxBytes policy");
        }

        if ("image".equals(resourceType) && (request.getWidth() == null || request.getHeight() == null)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "image requires width and height");
        }

        UploadAssetMetadata metadata = UploadAssetMetadata.builder()
            .publicId(publicId)
            .secureUrl(secureUrl)
            .resourceType(resourceType)
            .format(format)
            .bytes(request.getBytes())
            .width(request.getWidth())
            .height(request.getHeight())
            .duration(request.getDuration())
            .originalFilename(request.getOriginalFilename())
            .build();

        return UploadAssetResponse.builder()
                .purpose(request.getPurpose().value())
            .publicId(metadata.getPublicId())
            .secureUrl(metadata.getSecureUrl())
            .resourceType(metadata.getResourceType())
            .format(metadata.getFormat())
            .bytes(metadata.getBytes())
            .width(metadata.getWidth())
            .height(metadata.getHeight())
            .duration(metadata.getDuration())
            .originalFilename(metadata.getOriginalFilename())
                .build();
    }

    private String lower(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
}

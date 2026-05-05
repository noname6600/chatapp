package com.example.upload.service;

import com.cloudinary.Cloudinary;
import com.example.upload.application.ConfirmUploadCommand;
import com.example.upload.application.ConfirmUploadResult;
import com.example.upload.application.PrepareUploadCommand;
import com.example.upload.application.PrepareUploadResult;
import com.example.common.core.exception.BusinessException;
import com.example.upload.contract.UploadAssetMetadata;
import com.example.common.core.exception.CommonErrorCode;
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

    public PrepareUploadResult prepare(PrepareUploadCommand command) {
        UploadPolicy policy = policyRegistry.getOrThrow(command.purpose());

        long timestamp = Instant.now().getEpochSecond();
        String publicId = policy.getFolder() + "/" + UUID.randomUUID();
        String uploadUrl = "https://api.cloudinary.com/v1_1/" + cloudName + "/auto/upload";
        String publicUrl = "https://res.cloudinary.com/" + cloudName + "/" + publicId;

        Map<String, Object> paramsToSign = new HashMap<>();
        paramsToSign.put("folder", policy.getFolder());
        paramsToSign.put("public_id", publicId);
        paramsToSign.put("timestamp", timestamp);

        String signature = cloudinary.apiSignRequest(paramsToSign, apiSecret);

        return new PrepareUploadResult(
                command.purpose().value(),
                uploadUrl,
                publicUrl,
                publicId,
                cloudName,
                apiKey,
                timestamp,
                signature,
                policy.getFolder(),
                policy.getMaxBytes(),
                policy.getAllowedFormatsForClient()
        );
    }

    public ConfirmUploadResult confirm(ConfirmUploadCommand command) {
        UploadPolicy policy = policyRegistry.getOrThrow(command.purpose());

        String format = lower(command.format());
        String resourceType = lower(command.resourceType());
        String publicId = command.assetKey() == null ? "" : command.assetKey().trim();
        String secureUrl = command.publicUrl() == null ? "" : command.publicUrl().trim();

        if (!publicId.startsWith(policy.getFolder() + "/")) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "publicId does not match purpose folder policy");
        }

        if (!secureUrl.startsWith("https://res.cloudinary.com/" + cloudName + "/")) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "secureUrl is not from configured cloud");
        }

        if (!policy.getAllowedResourceTypes().contains(resourceType)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "resourceType is not allowed for this purpose");
        }

        if (!policy.getAllowedFormats().contains(format)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "format is not allowed for this purpose");
        }

        if (command.bytes() > policy.getMaxBytes()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "file exceeds maxBytes policy");
        }

        if ("image".equals(resourceType) && (command.width() == null || command.height() == null)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "image requires width and height");
        }

        UploadAssetMetadata metadata = UploadAssetMetadata.builder()
            .publicId(publicId)
            .secureUrl(secureUrl)
            .resourceType(resourceType)
            .format(format)
            .bytes(command.bytes())
            .width(command.width())
            .height(command.height())
            .duration(command.duration())
            .originalFilename(command.originalFilename())
            .build();

        return new ConfirmUploadResult(metadata);
    }

    private String lower(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }
}



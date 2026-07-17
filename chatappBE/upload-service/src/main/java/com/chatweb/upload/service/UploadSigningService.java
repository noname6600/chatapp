package com.chatweb.upload.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.chatweb.upload.application.ConfirmUploadCommand;
import com.chatweb.upload.application.ConfirmUploadResult;
import com.chatweb.upload.application.PrepareUploadCommand;
import com.chatweb.upload.application.PrepareUploadResult;
import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.upload.contract.UploadAssetMetadata;
import com.chatweb.common.core.exception.CommonErrorCode;
import com.chatweb.upload.domain.UploadPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UploadSigningService {

    private static final String TOKEN_HMAC_ALG = "HmacSHA256";
    private static final long DEFAULT_PREPARE_TOKEN_TTL_SECONDS = 600L;

    private static final java.util.Set<String> NON_PROD_PROFILES = java.util.Set.of("local", "test", "validation");

    private final Cloudinary cloudinary;
    private final UploadPolicyRegistry policyRegistry;
    private final Environment environment;

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    @Value("${cloudinary.api-key}")
    private String apiKey;

    @Value("${cloudinary.api-secret}")
    private String apiSecret;

    @Value("${upload.confirm.require-prepare-token:true}")
    private boolean requirePrepareToken;

    @Value("${upload.confirm.prepare-token-secret:}")
    private String prepareTokenSecret;

    @Value("${upload.confirm.prepare-token-ttl-seconds:600}")
    private long prepareTokenTtlSeconds;

    @PostConstruct
    void validatePrepareTokenSecret() {
        if (!requirePrepareToken) {
            return;
        }

        boolean hasNonProdProfile = java.util.Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase)
                .anyMatch(NON_PROD_PROFILES::contains);

        if (!hasNonProdProfile && (prepareTokenSecret == null || prepareTokenSecret.isBlank())) {
            throw new IllegalStateException("upload.confirm.prepare-token-secret must be set in non-local profiles");
        }
    }

    public PrepareUploadResult prepare(PrepareUploadCommand command) {
        UploadPolicy policy = policyRegistry.getOrThrow(command.purpose());

        long timestamp = Instant.now().getEpochSecond();
        String publicId = policy.getFolder() + "/" + UUID.randomUUID();
        String uploadUrl = "https://api.cloudinary.com/v1_1/" + cloudName + "/auto/upload";
        String publicUrl = "https://res.cloudinary.com/" + cloudName + "/" + publicId;
        String userId = normalized(command.userId());
        String prepareToken = signPrepareToken(userId, command.purpose().value(), publicId, policy.getFolder(), timestamp);

        // public_id already contains the folder prefix — including folder separately
        // causes Cloudinary to prepend it again (double-folder), so only sign public_id.
        Map<String, Object> paramsToSign = new HashMap<>();
        paramsToSign.put("public_id", publicId);
        paramsToSign.put("timestamp", timestamp);

        String signature = cloudinary.apiSignRequest(paramsToSign, apiSecret);

        return new PrepareUploadResult(
                command.purpose().value(),
                uploadUrl,
                publicUrl,
                publicId,
                prepareToken,
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
        String publicId = command.assetKey() == null ? "" : command.assetKey().trim();
        String userId = normalized(command.userId());

        if (!publicId.startsWith(policy.getFolder() + "/")) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "publicId does not match purpose folder policy");
        }

        if (requirePrepareToken) {
            verifyPrepareToken(command.prepareToken(), userId, command.purpose().value(), publicId, policy.getFolder());
        } else if (command.prepareToken() == null || command.prepareToken().isBlank()) {
            log.warn("[UPLOAD] confirm accepted without prepare token because upload.confirm.require-prepare-token=false purpose={} publicId={}",
                    command.purpose().value(), publicId);
        }

        Map<String, Object> verifiedAsset = fetchVerifiedCloudinaryAsset(publicId, policy);
        String verifiedPublicId = normalized(asString(verifiedAsset.get("public_id")));
        String verifiedSecureUrl = normalized(asString(verifiedAsset.get("secure_url")));
        String verifiedResourceType = lower(asString(verifiedAsset.get("resource_type")));
        String verifiedFormat = lower(asString(verifiedAsset.get("format")));
        long verifiedBytes = asLong(verifiedAsset.get("bytes"));
        Integer verifiedWidth = asInteger(verifiedAsset.get("width"));
        Integer verifiedHeight = asInteger(verifiedAsset.get("height"));
        Integer verifiedDuration = asInteger(verifiedAsset.get("duration"));
        String verifiedOriginalFilename = normalized(asString(verifiedAsset.get("original_filename")));

        if (!Objects.equals(publicId, verifiedPublicId)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "confirmed asset publicId does not match prepared asset");
        }

        if (!verifiedSecureUrl.startsWith("https://res.cloudinary.com/" + cloudName + "/")) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "verified secureUrl is not from configured cloud");
        }

        if (verifiedBytes <= 0L) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "verified asset size is invalid");
        }

        if (!policy.getAllowedResourceTypes().contains(verifiedResourceType)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "resourceType is not allowed for this purpose");
        }

        if (!policy.getAllowedFormats().contains(verifiedFormat)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "format is not allowed for this purpose");
        }

        if (verifiedBytes > policy.getMaxBytes()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "file exceeds maxBytes policy");
        }

        if ("image".equals(verifiedResourceType) && (verifiedWidth == null || verifiedHeight == null)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "image requires width and height");
        }

        UploadAssetMetadata metadata = UploadAssetMetadata.builder()
            .publicId(publicId)
            .secureUrl(verifiedSecureUrl)
            .resourceType(verifiedResourceType)
            .format(verifiedFormat)
            .bytes(verifiedBytes)
            .width(verifiedWidth)
            .height(verifiedHeight)
            .duration(verifiedDuration)
            .originalFilename(verifiedOriginalFilename != null && !verifiedOriginalFilename.isBlank()
                    ? verifiedOriginalFilename
                    : command.originalFilename())
            .build();

        return new ConfirmUploadResult(metadata);
    }

    private String signPrepareToken(String userId, String purpose, String publicId, String folder, long timestamp) {
        long ttlSeconds = prepareTokenTtlSeconds > 0 ? prepareTokenTtlSeconds : DEFAULT_PREPARE_TOKEN_TTL_SECONDS;
        long expiresAt = timestamp + ttlSeconds;

        String payload = String.join("|",
                "v1",
                nullToEmpty(userId),
                nullToEmpty(purpose),
                nullToEmpty(publicId),
                nullToEmpty(folder),
                String.valueOf(timestamp),
                String.valueOf(expiresAt));

        byte[] signature = hmacSha256(payload);
        return base64Url(payload.getBytes(StandardCharsets.UTF_8)) + "." + base64Url(signature);
    }

    private void verifyPrepareToken(String prepareToken, String userId, String purpose, String publicId, String folder) {
        if (prepareToken == null || prepareToken.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "prepareToken is required");
        }

        String[] parts = prepareToken.split("\\.", 2);
        if (parts.length != 2) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "prepareToken is malformed");
        }

        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "prepareToken payload is invalid");
        }

        byte[] providedSignature;
        try {
            providedSignature = Base64.getUrlDecoder().decode(parts[1]);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "prepareToken signature is invalid");
        }

        byte[] expectedSignature = hmacSha256(payload);
        if (!java.security.MessageDigest.isEqual(providedSignature, expectedSignature)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "prepareToken signature mismatch");
        }

        String[] claims = payload.split("\\|", -1);
        if (claims.length != 7 || !"v1".equals(claims[0])) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "prepareToken payload version is invalid");
        }

        if (!Objects.equals(nullToEmpty(userId), claims[1])) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "prepareToken user does not match current user");
        }

        if (!Objects.equals(nullToEmpty(purpose), claims[2])) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "prepareToken purpose mismatch");
        }

        if (!Objects.equals(nullToEmpty(publicId), claims[3])) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "prepareToken publicId mismatch");
        }

        if (!Objects.equals(nullToEmpty(folder), claims[4])) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "prepareToken folder mismatch");
        }

        long expiresAt;
        try {
            expiresAt = Long.parseLong(claims[6]);
        } catch (NumberFormatException ex) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "prepareToken expiration is invalid");
        }

        long now = Instant.now().getEpochSecond();
        if (expiresAt < now) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "prepareToken is expired");
        }
    }

    private Map<String, Object> fetchVerifiedCloudinaryAsset(String publicId, UploadPolicy policy) {
        java.util.Set<String> candidates = policy.getAllowedResourceTypes().isEmpty()
            ? java.util.Set.of("image", "video", "raw")
                : policy.getAllowedResourceTypes();

        for (String candidate : candidates) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> resource = cloudinary.api().resource(publicId, ObjectUtils.asMap("resource_type", candidate));
                if (resource != null && !resource.isEmpty()) {
                    return resource;
                }
            } catch (Exception ex) {
                log.debug("[UPLOAD] cloudinary resource lookup miss publicId={} resourceType={}", publicId, candidate);
            }
        }

        throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "uploaded asset not found in cloud provider");
    }

    private String lower(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private String normalized(String raw) {
        return raw == null ? null : raw.trim();
    }

    private String nullToEmpty(String raw) {
        return raw == null ? "" : raw;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private byte[] hmacSha256(String payload) {
        try {
            Mac mac = Mac.getInstance(TOKEN_HMAC_ALG);
            mac.init(new SecretKeySpec(resolvePrepareTokenSecret().getBytes(StandardCharsets.UTF_8), TOKEN_HMAC_ALG));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to produce prepare token signature", ex);
        }
    }

    private String base64Url(byte[] raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private String resolvePrepareTokenSecret() {
        if (prepareTokenSecret != null && !prepareTokenSecret.isBlank()) {
            return prepareTokenSecret;
        }
        return apiSecret;
    }
}



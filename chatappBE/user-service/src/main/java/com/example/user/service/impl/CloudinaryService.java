package com.example.user.service.impl;

import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import com.example.user.dto.CloudinaryUploadResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final Cloudinary cloudinary;

    public CloudinaryUploadResult uploadAvatar(MultipartFile file, UUID userId) {

        try {

            if (file == null || file.isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Avatar file is empty");
            }

            if (file.getSize() > MAX_FILE_SIZE) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Avatar file too large (max 5MB)");
            }

            String contentType = file.getContentType();

            if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "Unsupported image format");
            }

            byte[] bytes = file.getBytes();

            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().upload(
                    bytes,
                    ObjectUtils.asMap(
                            "folder", "avatars",
                            "public_id", "user_" + userId,
                            "overwrite", true,
                            "resource_type", "image",
                            "transformation", "c_fill,w_256,h_256"
                    )
            );

            String secureUrl = (String) result.get("secure_url");
            String publicId = (String) result.get("public_id");

            return new CloudinaryUploadResult(secureUrl, publicId);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {

            throw new BusinessException(
                    ErrorCode.INTERNAL_ERROR,
                    "Cloudinary upload failed"
            );
        }
    }

    public void deleteByPublicId(String publicId) {

        if (publicId == null) {
            return;
        }

        try {

            cloudinary.uploader().destroy(
                    publicId,
                    ObjectUtils.emptyMap()
            );

        } catch (Exception ignored) {
        }
    }
}
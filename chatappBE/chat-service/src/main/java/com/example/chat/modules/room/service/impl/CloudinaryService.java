package com.example.chat.modules.room.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.chat.modules.room.dto.CloudinaryUploadResult;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryUploadResult uploadAvatar(MultipartFile file, String publicId) {

        try {

            byte[] bytes = file.getBytes();

            Map<String, Object> result = cloudinary.uploader().upload(
                    bytes,
                    ObjectUtils.asMap(
                            "folder", "room_avatars",
                            "public_id", publicId,
                            "overwrite", true,
                            "resource_type", "image",
                            "transformation", "c_fill,w_256,h_256"
                    )
            );

            return new CloudinaryUploadResult(
                    (String) result.get("secure_url"),
                    (String) result.get("public_id")
            );

        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Upload failed");
        }
    }

    public void delete(String publicId) {
        if (publicId == null) return;
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (Exception ignored) {}
    }
}
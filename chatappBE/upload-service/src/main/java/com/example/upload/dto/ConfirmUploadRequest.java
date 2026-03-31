package com.example.upload.dto;

import com.example.upload.domain.UploadPurpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfirmUploadRequest {

    @NotNull(message = "purpose is required")
    private UploadPurpose purpose;

    @NotBlank(message = "publicId is required")
    private String publicId;

    @NotBlank(message = "secureUrl is required")
    private String secureUrl;

    @NotBlank(message = "resourceType is required")
    private String resourceType;

    @NotBlank(message = "format is required")
    private String format;

    @NotNull(message = "bytes is required")
    @Positive(message = "bytes must be positive")
    private Long bytes;

    private Integer width;
    private Integer height;
    private Integer duration;
    private String originalFilename;
}

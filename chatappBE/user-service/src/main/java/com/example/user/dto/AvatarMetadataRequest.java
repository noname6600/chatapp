package com.example.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AvatarMetadataRequest {

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

    @NotNull(message = "width is required")
    @Positive(message = "width must be positive")
    private Integer width;

    @NotNull(message = "height is required")
    @Positive(message = "height must be positive")
    private Integer height;
}

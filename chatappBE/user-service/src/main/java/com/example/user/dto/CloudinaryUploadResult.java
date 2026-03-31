package com.example.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CloudinaryUploadResult {
    private String secureUrl;
    private String publicId;
}

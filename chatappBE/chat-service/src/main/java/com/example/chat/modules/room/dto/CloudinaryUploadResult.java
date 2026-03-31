package com.example.chat.modules.room.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CloudinaryUploadResult {
    private String secureUrl;
    private String publicId;
}

package com.example.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {

    @Size(max = 64, message = "Display name must be at most 64 characters")
    private String displayName;

    @Pattern(
            regexp = "^[a-zA-Z0-9.,_]+$",
            message = "Username can only contain letters, numbers, dot(.), underscore(_), comma(,)"
    )
    @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
    private String username;

    @Size(max = 160, message = "About me must be at most 160 characters")
    private String aboutMe;

    @Pattern(
            regexp = "^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$",
            message = "Background color must be valid hex color"
    )
    private String backgroundColor;
}




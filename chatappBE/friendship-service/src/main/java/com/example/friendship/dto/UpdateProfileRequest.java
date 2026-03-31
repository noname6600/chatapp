package com.example.friendship.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {

    @Size(max = 64)
    private String displayName;

    @Size(max = 255)
    private String avatarUrl;

    @Pattern(
            regexp = "^[a-zA-Z0-9.,_]+$",
            message = "Username can only contain letters, numbers, dot(.), underscore(_), comma(,)"
    )
    @Size(min = 3, max = 30)
    private String username;
}




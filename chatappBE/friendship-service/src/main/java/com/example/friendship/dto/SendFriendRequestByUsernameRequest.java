package com.example.friendship.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendFriendRequestByUsernameRequest {

    @NotBlank
    @Size(min = 3, max = 30)
    @Pattern(
            regexp = "^[a-zA-Z0-9.,_]+$",
            message = "Username can only contain letters, numbers, dot(.), underscore(_), comma(,)"
    )
    private String username;
}

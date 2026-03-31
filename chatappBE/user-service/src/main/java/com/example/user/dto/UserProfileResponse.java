package com.example.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileResponse {
    private UUID accountId;
    private String username;
    private String displayName;
    private String avatarUrl;
    private String aboutMe;
    private String backgroundColor;
}


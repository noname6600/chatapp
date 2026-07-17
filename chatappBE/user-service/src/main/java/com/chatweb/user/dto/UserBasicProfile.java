package com.chatweb.user.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserBasicProfile {

    private UUID accountId;
    private String username;
    private String displayName;
    private String avatarUrl;

}

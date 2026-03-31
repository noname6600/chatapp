package com.example.chat.modules.message.infrastructure.client;

import lombok.Data;

import java.util.UUID;

@Data
public class UserBasicProfile  {
    private UUID accountId;
    private String displayName;
    private String avatarUrl;
}

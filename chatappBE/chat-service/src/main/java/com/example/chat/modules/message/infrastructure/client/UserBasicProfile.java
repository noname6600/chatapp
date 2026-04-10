package com.example.chat.modules.message.infrastructure.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.UUID;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserBasicProfile  {
    private UUID accountId;
    private String displayName;
    private String avatarUrl;
}

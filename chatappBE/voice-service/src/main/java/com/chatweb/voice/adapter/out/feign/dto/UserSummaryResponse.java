package com.chatweb.voice.adapter.out.feign.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserSummaryResponse {
    private UUID accountId;
    private String username;
    private String displayName;
    private String avatarUrl;
}

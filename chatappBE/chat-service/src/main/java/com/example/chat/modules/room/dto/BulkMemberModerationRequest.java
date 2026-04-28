package com.example.chat.modules.room.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BulkMemberModerationRequest {

    @NotEmpty
    private List<UUID> userIds;
}

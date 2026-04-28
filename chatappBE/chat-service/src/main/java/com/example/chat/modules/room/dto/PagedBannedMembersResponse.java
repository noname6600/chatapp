package com.example.chat.modules.room.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedBannedMembersResponse {

    private List<UUID> userIds;

    private int page;

    private int size;

    private int shown;

    private long total;

    private int totalPages;
}

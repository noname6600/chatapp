package com.example.chat.modules.room.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PagedRoomMembersResponse {

    private List<RoomMemberResponse> members;

    private int page;

    private int size;

    private int shown;

    private long total;

    private int totalPages;
}

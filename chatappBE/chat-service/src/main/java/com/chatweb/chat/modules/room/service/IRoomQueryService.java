package com.chatweb.chat.modules.room.service;

import com.chatweb.chat.modules.room.dto.PagedRoomMembersResponse;
import com.chatweb.chat.modules.room.dto.PagedBannedMembersResponse;
import com.chatweb.chat.modules.room.dto.RoomMemberResponse;
import com.chatweb.chat.modules.room.dto.RoomResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface IRoomQueryService {

    List<RoomResponse> roomsOfUser(UUID userId);

    PagedRoomMembersResponse membersOfRoom(UUID roomId, int page, int size, String query);

    PagedBannedMembersResponse bannedMembersOfRoom(UUID roomId, int page, int size);

    List<RoomMemberResponse> membersOfRoom(UUID roomId);

    Map<UUID, List<RoomMemberResponse>> membersOfRooms(List<UUID> roomIds);

    long memberCount(UUID roomId);
}

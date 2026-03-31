package com.example.chat.modules.room.service;

import com.example.chat.modules.room.dto.RoomMemberResponse;
import com.example.chat.modules.room.dto.RoomResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface IRoomQueryService {

    List<RoomResponse> roomsOfUser(UUID userId);

    List<RoomMemberResponse> membersOfRoom(UUID roomId);

    Map<UUID, List<RoomMemberResponse>> membersOfRooms(List<UUID> roomIds);

    long memberCount(UUID roomId);
}

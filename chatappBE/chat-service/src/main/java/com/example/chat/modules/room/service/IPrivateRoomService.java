package com.example.chat.modules.room.service;

import com.example.chat.modules.room.dto.RoomResponse;

import java.util.UUID;

public interface IPrivateRoomService {

    RoomResponse getOrCreatePrivateRoom(
            UUID user1Id,
            UUID user2Id
    );

}
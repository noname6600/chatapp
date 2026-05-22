package com.chatweb.chat.modules.room.repository.projection;

import com.chatweb.chat.modules.room.entity.Room;
import com.chatweb.chat.modules.room.enums.Role;

import java.util.UUID;

public interface RoomRow {

    Room getRoom();

    Role getRole();

    Long getUnreadCount();

    UUID getUser1Id();
    String getUser1Name();
    String getUser1Avatar();

    UUID getUser2Id();
    String getUser2Name();
    String getUser2Avatar();
}

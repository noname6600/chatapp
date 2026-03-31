package com.example.notification.repository;

import com.example.notification.entity.RoomMuteSetting;
import com.example.notification.entity.RoomMuteSettingId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomMuteSettingRepository extends JpaRepository<RoomMuteSetting, RoomMuteSettingId> {
    
    @Query("SELECT rms FROM RoomMuteSetting rms WHERE rms.id.userId = :userId AND rms.id.roomId = :roomId")
    Optional<RoomMuteSetting> findByIdUserIdAndIdRoomId(@Param("userId") UUID userId, @Param("roomId") UUID roomId);
}
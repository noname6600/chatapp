package com.chatweb.notification.repository;

import com.chatweb.notification.entity.RoomMuteSetting;
import com.chatweb.notification.entity.RoomMuteSettingId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoomMuteSettingRepository extends JpaRepository<RoomMuteSetting, RoomMuteSettingId> {

    // Returns List so Spring Data uses getResultList() instead of getSingleResult(),
    // avoiding NoResultException span events in OTel when no row exists.
    List<RoomMuteSetting> findByIdUserIdAndIdRoomId(UUID userId, UUID roomId);

    @Query("SELECT rms FROM RoomMuteSetting rms WHERE rms.id.roomId = :roomId")
    List<RoomMuteSetting> findAllByIdRoomId(@Param("roomId") UUID roomId);
}
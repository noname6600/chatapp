package com.example.chat.modules.room.service.impl;

import com.example.chat.config.InviteCodeGenerator;
import com.example.chat.modules.room.entity.Room;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import com.example.chat.modules.room.repository.RoomRepository;
import com.example.chat.modules.room.enums.RoomType;
import com.example.common.redis.api.ITimeRedisCacheManager;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@DataJpaTest
@ContextConfiguration(classes = RoomServiceInviteJoinIntegrationTest.TestApplication.class)
class RoomServiceInviteJoinIntegrationTest {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomMemberRepository roomMemberRepository;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(
                roomRepository,
                roomMemberRepository,
                mock(InviteCodeGenerator.class),
                mock(GroupAvatarGenerator.class),
                mock(CloudinaryService.class),
                mock(ITimeRedisCacheManager.class)
        );
    }

    @Test
    void joinByInviteRoomId_authorizedJoin_succeeds() {
        UUID userId = UUID.randomUUID();

        Room room = roomRepository.save(Room.builder()
                .type(RoomType.GROUP)
                .name("Engineering")
                .createdBy(UUID.randomUUID())
                .build());

        roomService.joinByInviteRoomId(userId, room.getId());

        assertThat(roomMemberRepository.existsByRoomIdAndUserId(room.getId(), userId)).isTrue();
    }

    @Test
    void joinByInviteRoomId_deniedJoinForPrivateRoom_throwsBadRequest() {
        UUID userId = UUID.randomUUID();

        Room room = roomRepository.save(Room.builder()
                .type(RoomType.PRIVATE)
                .name("Direct")
                .createdBy(UUID.randomUUID())
                .build());

        assertThatThrownBy(() -> roomService.joinByInviteRoomId(userId, room.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    void joinByInviteRoomId_repeatedJoin_isIdempotent() {
        UUID userId = UUID.randomUUID();

        Room room = roomRepository.save(Room.builder()
                .type(RoomType.GROUP)
                .name("Design")
                .createdBy(UUID.randomUUID())
                .build());

        roomService.joinByInviteRoomId(userId, room.getId());
        roomService.joinByInviteRoomId(userId, room.getId());

        assertThat(roomMemberRepository.findByRoomId(room.getId()))
                .filteredOn(member -> userId.equals(member.getUserId()))
                .hasSize(1);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Room.class)
    @EnableJpaRepositories(basePackageClasses = RoomRepository.class)
    static class TestApplication {
    }
}

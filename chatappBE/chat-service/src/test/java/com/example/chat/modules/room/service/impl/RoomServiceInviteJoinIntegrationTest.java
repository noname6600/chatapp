package com.example.chat.modules.room.service.impl;

import com.example.chat.config.InviteCodeGenerator;
import com.example.chat.modules.message.application.service.ISystemMessageService;
import com.example.chat.modules.message.infrastructure.client.UserClient;
import com.example.chat.modules.room.entity.Room;
import com.example.chat.modules.room.entity.RoomBan;
import com.example.chat.modules.room.entity.RoomMember;
import com.example.chat.modules.room.enums.Role;
import com.example.chat.modules.room.repository.RoomBanRepository;
import com.example.chat.modules.room.repository.RoomMemberRepository;
import com.example.chat.modules.room.repository.RoomRepository;
import com.example.chat.modules.room.enums.RoomType;
import com.example.common.redis.api.ITimeRedisCacheManager;
import com.example.common.websocket.session.IRoomBroadcaster;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

        @Autowired
        private RoomBanRepository roomBanRepository;

    private RoomService roomService;

    @BeforeEach
    void setUp() {
        roomService = new RoomService(
                roomRepository,
                roomMemberRepository,
                roomBanRepository,
                mock(UserClient.class),
                mock(InviteCodeGenerator.class),
                mock(GroupAvatarGenerator.class),
                mock(CloudinaryService.class),
                                mock(ITimeRedisCacheManager.class),
                                mock(IRoomBroadcaster.class),
                                mock(ISystemMessageService.class)
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

    @Test
    void joinByInviteRoomId_rejectsBannedUser() {
        UUID userId = UUID.randomUUID();

        Room room = roomRepository.save(Room.builder()
                .type(RoomType.GROUP)
                .name("Moderation")
                .createdBy(UUID.randomUUID())
                .build());

        roomBanRepository.save(RoomBan.builder()
                .roomId(room.getId())
                .userId(userId)
                .bannedBy(UUID.randomUUID())
                .build());

        assertThatThrownBy(() -> roomService.joinByInviteRoomId(userId, room.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void moderationBanUnbanFlow_supportsRejoinAfterUnban() {
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        Room room = roomRepository.save(Room.builder()
                .type(RoomType.GROUP)
                .name("Moderation Flow")
                .createdBy(ownerId)
                .build());

        roomMemberRepository.save(RoomMember.builder()
                .roomId(room.getId())
                .userId(ownerId)
                .role(Role.OWNER)
                .build());

        roomService.joinByInviteRoomId(targetId, room.getId());
        assertThat(roomMemberRepository.existsByRoomIdAndUserId(room.getId(), targetId)).isTrue();

        roomService.banMember(room.getId(), ownerId, targetId);

        assertThat(roomMemberRepository.existsByRoomIdAndUserId(room.getId(), targetId)).isFalse();
        assertThat(roomBanRepository.existsByRoomIdAndUserId(room.getId(), targetId)).isTrue();

        roomService.unbanMember(room.getId(), ownerId, targetId);
        assertThat(roomBanRepository.existsByRoomIdAndUserId(room.getId(), targetId)).isFalse();

        roomService.joinByInviteRoomId(targetId, room.getId());
        assertThat(roomMemberRepository.existsByRoomIdAndUserId(room.getId(), targetId)).isTrue();
    }

        @Test
        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        void joinByInviteRoomId_concurrentJoin_isIdempotent() throws Exception {
                UUID userId = UUID.randomUUID();

                Room room = roomRepository.save(Room.builder()
                                .type(RoomType.GROUP)
                                .name("Concurrency")
                                .createdBy(UUID.randomUUID())
                                .build());

                int workerCount = 6;
                ExecutorService pool = Executors.newFixedThreadPool(workerCount);
                CountDownLatch ready = new CountDownLatch(workerCount);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> futures = new ArrayList<>();

                try {
                        for (int i = 0; i < workerCount; i++) {
                                futures.add(pool.submit(() -> {
                                        ready.countDown();
                                        start.await(5, TimeUnit.SECONDS);
                                        roomService.joinByInviteRoomId(userId, room.getId());
                                        return null;
                                }));
                        }

                        ready.await(5, TimeUnit.SECONDS);
                        start.countDown();

                        for (Future<?> future : futures) {
                                future.get(10, TimeUnit.SECONDS);
                        }
                } finally {
                        pool.shutdownNow();
                }

                assertThat(roomMemberRepository.findByRoomId(room.getId()))
                                .filteredOn(member -> userId.equals(member.getUserId()))
                                .hasSize(1);
        }

    @SpringBootConfiguration
    @EntityScan(basePackageClasses = Room.class)
    @EnableJpaRepositories(basePackageClasses = RoomRepository.class)
    static class TestApplication {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> Jwt.withTokenValue(token)
                    .header("alg", "none")
                    .claim("sub", "test-user")
                    .build();
        }
    }
}

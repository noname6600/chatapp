package com.example.chat.modules.room.repository;

import com.example.chat.modules.room.entity.Room;
import com.example.chat.modules.room.entity.RoomMember;
import com.example.chat.modules.room.enums.Role;
import com.example.chat.modules.room.enums.RoomType;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ContextConfiguration(classes = RoomRepositoryRoomsOfUserAdvancedIntegrationTest.TestApplication.class)
class RoomRepositoryRoomsOfUserAdvancedIntegrationTest {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomMemberRepository roomMemberRepository;

    @Test
    void findRoomsOfUserAdvanced_groupRoomWithManyMembers_returnsSingleRowForCurrentUser() {
        UUID me = UUID.randomUUID();

        Room room = roomRepository.save(Room.builder()
                .type(RoomType.GROUP)
                .name("Backend")
                .createdBy(me)
                .build());

        roomMemberRepository.save(RoomMember.builder()
                .roomId(room.getId())
                .userId(me)
                .role(Role.OWNER)
                .build());

        roomMemberRepository.save(RoomMember.builder()
                .roomId(room.getId())
                .userId(UUID.randomUUID())
                .role(Role.MEMBER)
                .build());

        roomMemberRepository.save(RoomMember.builder()
                .roomId(room.getId())
                .userId(UUID.randomUUID())
                .role(Role.MEMBER)
                .build());

        assertThat(roomRepository.findRoomsOfUserAdvanced(me))
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

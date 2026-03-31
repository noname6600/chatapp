package com.example.notification.controller;

import com.example.notification.service.impl.RoomMuteSettingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomMuteControllerTest {

    @Mock
    private RoomMuteSettingService roomMuteSettingService;

    @InjectMocks
    private RoomMuteController controller;

    @Test
    void muteAndUnmute_callService() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .subject(userId.toString())
                .headers(h -> h.put("alg", "none"))
                .claims(c -> c.put("sub", userId.toString()))
                .build();

        controller.mute(roomId, jwt);
        controller.unmute(roomId, jwt);

        verify(roomMuteSettingService).mute(userId, roomId);
        verify(roomMuteSettingService).unmute(userId, roomId);
    }

    @Test
    void settings_returnsMutedFlag() {
        UUID userId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .subject(userId.toString())
                .headers(h -> h.put("alg", "none"))
                .claims(c -> c.put("sub", userId.toString()))
                .build();

        when(roomMuteSettingService.isMuted(userId, roomId)).thenReturn(true);

        var response = controller.settings(roomId, jwt);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().isMuted()).isTrue();
    }
}

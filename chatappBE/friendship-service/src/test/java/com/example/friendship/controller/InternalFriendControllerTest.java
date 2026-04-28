package com.example.friendship.controller;

import com.example.common.web.response.ApiResponse;
import com.example.friendship.service.IFriendQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InternalFriendControllerTest {

    private IFriendQueryService queryService;
    private InternalFriendController controller;

    @BeforeEach
    void setUp() {
        queryService = mock(IFriendQueryService.class);
        controller = new InternalFriendController(queryService);
    }

    @Test
    void blockedBetween_returnsTrue_whenBlockExists() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        when(queryService.existsBlockBetween(user1, user2)).thenReturn(true);

        ResponseEntity<ApiResponse<Boolean>> response = controller.blockedBetween(user1, user2);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isTrue();
    }

    @Test
    void blockedBetween_returnsFalse_whenNoBlock() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        when(queryService.existsBlockBetween(user1, user2)).thenReturn(false);

        ResponseEntity<ApiResponse<Boolean>> response = controller.blockedBetween(user1, user2);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isFalse();
    }
}

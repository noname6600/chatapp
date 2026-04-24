package com.example.notification.service;

import com.example.notification.entity.RoomNotificationMode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationModePolicyTest {

    private final NotificationModePolicy policy = new NotificationModePolicy();

    @Test
    void shouldDeliverRoomEvent_appliesMatrix() {
        assertThat(policy.shouldDeliverRoomEvent(RoomNotificationMode.NO_RESTRICT, false)).isTrue();
        assertThat(policy.shouldDeliverRoomEvent(RoomNotificationMode.ONLY_MENTION, false)).isFalse();
        assertThat(policy.shouldDeliverRoomEvent(RoomNotificationMode.ONLY_MENTION, true)).isTrue();
        assertThat(policy.shouldDeliverRoomEvent(RoomNotificationMode.NOTHING, true)).isFalse();
    }
}

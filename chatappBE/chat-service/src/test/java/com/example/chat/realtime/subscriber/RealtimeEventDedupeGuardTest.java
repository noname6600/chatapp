package com.example.chat.realtime.subscriber;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RealtimeEventDedupeGuardTest {

    @Test
    void isDuplicate_returnsTrueForSecondDeliveryOfSameEventId() {
        RealtimeEventDedupeGuard guard = new RealtimeEventDedupeGuard();
        UUID eventId = UUID.randomUUID();

        boolean first = guard.isDuplicate(eventId);
        boolean second = guard.isDuplicate(eventId);

        assertThat(first).isFalse();
        assertThat(second).isTrue();
    }

    @Test
    void isDuplicate_allowsNullEventId() {
        RealtimeEventDedupeGuard guard = new RealtimeEventDedupeGuard();

        assertThat(guard.isDuplicate(null)).isFalse();
        assertThat(guard.isDuplicate(null)).isFalse();
    }
}

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

    @Test
    void isDuplicateKey_returnsTrueForSecondDeliveryOfSameMessageId() {
        RealtimeEventDedupeGuard guard = new RealtimeEventDedupeGuard();

        boolean first = guard.isDuplicateKey("msg-1");
        boolean second = guard.isDuplicateKey("msg-1");

        assertThat(first).isFalse();
        assertThat(second).isTrue();
    }
}

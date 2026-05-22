package com.chatweb.presence.configuration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PresenceRedisKeyspaceNotificationStartupCheckTest {

    @Test
    void hasRequiredExpiredKeyeventFlags_returnsTrue_forEx() {
        assertThat(PresenceRedisKeyspaceNotificationStartupCheck.hasRequiredExpiredKeyeventFlags("Ex")).isTrue();
    }

    @Test
    void hasRequiredExpiredKeyeventFlags_returnsTrue_forKEA() {
        assertThat(PresenceRedisKeyspaceNotificationStartupCheck.hasRequiredExpiredKeyeventFlags("KEA")).isTrue();
    }

    @Test
    void hasRequiredExpiredKeyeventFlags_returnsFalse_whenMissingKeyeventChannel() {
        assertThat(PresenceRedisKeyspaceNotificationStartupCheck.hasRequiredExpiredKeyeventFlags("Kx")).isFalse();
    }

    @Test
    void hasRequiredExpiredKeyeventFlags_returnsFalse_whenMissingExpiredEventFlag() {
        assertThat(PresenceRedisKeyspaceNotificationStartupCheck.hasRequiredExpiredKeyeventFlags("Eg")).isFalse();
    }

    @Test
    void hasRequiredExpiredKeyeventFlags_returnsFalse_forBlank() {
        assertThat(PresenceRedisKeyspaceNotificationStartupCheck.hasRequiredExpiredKeyeventFlags(" ")).isFalse();
    }
}

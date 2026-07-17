package com.chatweb.presence.contract;

import com.chatweb.common.integration.presence.PresenceEventType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PresenceRealtimeContractBaselineTest {

    @Test
    void presenceEventTypeValues_matchCurrentContract() {
        assertThat(PresenceEventType.USER_ONLINE.value()).isEqualTo("presence.user.online");
        assertThat(PresenceEventType.ROOM_TYPING.value()).isEqualTo("presence.room.typing");
        assertThat(PresenceEventType.fromValue("presence.room.stop-typing")).isEqualTo(PresenceEventType.ROOM_STOP_TYPING);
    }

}

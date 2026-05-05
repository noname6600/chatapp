package com.example.presence.contract;

import com.example.common.integration.presence.PresenceEventType;
import com.example.common.integration.realtime.RealtimeContractVersions;
import com.example.common.redis.channel.RedisChannels;
import com.example.presence.constants.PresenceRedisChannels;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PresenceRealtimeContractBaselineTest {

    @Test
    void presenceEventTypeValues_matchCurrentContract() {
        assertThat(PresenceEventType.USER_ONLINE.value()).isEqualTo("presence.user.online");
        assertThat(PresenceEventType.ROOM_TYPING.value()).isEqualTo("presence.room.typing");
        assertThat(PresenceEventType.fromValue("presence.room.stop_typing")).isEqualTo(PresenceEventType.ROOM_STOP_TYPING);
    }

    @Test
    void presenceRedisChannels_matchCurrentContract() {
        assertThat(PresenceRedisChannels.PRESENCE_USER).isEqualTo(RedisChannels.PRESENCE_USER);
        assertThat(PresenceRedisChannels.PRESENCE_ROOM).isEqualTo(RedisChannels.PRESENCE_ROOM_PREFIX);
        assertThat(PresenceRedisChannels.PRESENCE_GLOBAL).isEqualTo(RedisChannels.PRESENCE_GLOBAL);
        assertThat(PresenceRedisChannels.PRESENCE_PATTERN).isEqualTo(RedisChannels.PRESENCE_PATTERN);
        assertThat(RealtimeContractVersions.PRESENCE_REDIS_FANOUT).isEqualTo("v1");
    }
}

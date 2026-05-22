package com.chatweb.friendship.contract;

import com.chatweb.common.integration.friendship.FriendshipEventType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FriendshipRealtimeContractBaselineTest {

    @Test
    void friendshipEventTypeValues_matchCurrentContract() {
        assertThat(FriendshipEventType.FRIEND_REQUEST_SENT.value()).isEqualTo("friend.request.sent");
        assertThat(FriendshipEventType.FRIEND_REQUEST_ACCEPTED.value()).isEqualTo("friend.request.accepted");
        assertThat(FriendshipEventType.FRIEND_UNBLOCKED.value()).isEqualTo("friend.unblocked");
    }
}

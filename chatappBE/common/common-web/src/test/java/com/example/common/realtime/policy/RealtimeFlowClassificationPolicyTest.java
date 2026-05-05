package com.example.common.realtime.policy;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RealtimeFlowClassificationPolicyTest {

    @Test
    void testAllFlowsAreClassified() {
        // Verify every flow ID has a classification
        Map<RealtimeFlowId, RealtimeFlowType> classifications = RealtimeFlowClassificationPolicy.getAllClassifications();
        
        for (RealtimeFlowId flowId : RealtimeFlowId.values()) {
            assertTrue(classifications.containsKey(flowId),
                    "Flow " + flowId + " must be classified");
            assertNotNull(classifications.get(flowId),
                    "Flow " + flowId + " classification must not be null");
        }
    }

    @Test
    void testDurableFirstFlows() {
        // Verify all message and notification flows are durable-first
        assertTrue(RealtimeFlowClassificationPolicy.isDurableFirst(RealtimeFlowId.CHAT_MESSAGE_CREATE));
        assertTrue(RealtimeFlowClassificationPolicy.isDurableFirst(RealtimeFlowId.CHAT_MESSAGE_DELETE));
        assertTrue(RealtimeFlowClassificationPolicy.isDurableFirst(RealtimeFlowId.CHAT_MESSAGE_PIN));
        assertTrue(RealtimeFlowClassificationPolicy.isDurableFirst(RealtimeFlowId.CHAT_ROOM_CREATE));
        assertTrue(RealtimeFlowClassificationPolicy.isDurableFirst(RealtimeFlowId.NOTIFICATION_PUSH));
        assertTrue(RealtimeFlowClassificationPolicy.isDurableFirst(RealtimeFlowId.FRIENDSHIP_REQUEST_CREATED));
    }

    @Test
    void testEphemeralOnlyFlows() {
        // Verify all presence typing/online flows are ephemeral-only
        assertTrue(RealtimeFlowClassificationPolicy.isEphemeralOnly(RealtimeFlowId.PRESENCE_USER_TYPING));
        assertTrue(RealtimeFlowClassificationPolicy.isEphemeralOnly(RealtimeFlowId.PRESENCE_USER_STOP_TYPING));
        assertTrue(RealtimeFlowClassificationPolicy.isEphemeralOnly(RealtimeFlowId.PRESENCE_USER_ONLINE));
        assertTrue(RealtimeFlowClassificationPolicy.isEphemeralOnly(RealtimeFlowId.PRESENCE_USER_OFFLINE));
        assertTrue(RealtimeFlowClassificationPolicy.isEphemeralOnly(RealtimeFlowId.PRESENCE_USER_STATUS_CHANGED));
        assertTrue(RealtimeFlowClassificationPolicy.isEphemeralOnly(RealtimeFlowId.PRESENCE_ROOM_ACTIVITY));
    }

    @Test
    void testMixedWithConvergenceFlows() {
        // Verify room member updates are mixed with convergence
        assertTrue(RealtimeFlowClassificationPolicy.isMixedWithConvergence(RealtimeFlowId.CHAT_ROOM_MEMBER_ADD));
        assertTrue(RealtimeFlowClassificationPolicy.isMixedWithConvergence(RealtimeFlowId.CHAT_ROOM_MEMBER_REMOVE));
        assertTrue(RealtimeFlowClassificationPolicy.isMixedWithConvergence(RealtimeFlowId.CHAT_ROOM_MEMBER_LIST_UPDATE));
    }

    @Test
    void testGetFlowType() {
        // Verify getFlowType returns correct enum values
        assertEquals(RealtimeFlowType.DURABLE_FIRST, 
                RealtimeFlowClassificationPolicy.getFlowType(RealtimeFlowId.CHAT_MESSAGE_CREATE));
        assertEquals(RealtimeFlowType.EPHEMERAL_ONLY,
                RealtimeFlowClassificationPolicy.getFlowType(RealtimeFlowId.PRESENCE_USER_TYPING));
        assertEquals(RealtimeFlowType.MIXED_WITH_CONVERGENCE,
                RealtimeFlowClassificationPolicy.getFlowType(RealtimeFlowId.CHAT_ROOM_MEMBER_ADD));
    }

    @Test
    void testFlowTypeLabel() {
        // Verify flow type labels are correct
        assertEquals("durable-first", RealtimeFlowType.DURABLE_FIRST.getLabel());
        assertEquals("ephemeral-only", RealtimeFlowType.EPHEMERAL_ONLY.getLabel());
        assertEquals("mixed-with-convergence", RealtimeFlowType.MIXED_WITH_CONVERGENCE.getLabel());
    }

    @Test
    void testFlowTypeFromLabel() {
        // Verify round-trip conversion
        assertEquals(RealtimeFlowType.DURABLE_FIRST, 
                RealtimeFlowType.fromLabel("durable-first"));
        assertEquals(RealtimeFlowType.EPHEMERAL_ONLY,
                RealtimeFlowType.fromLabel("ephemeral-only"));
        assertEquals(RealtimeFlowType.MIXED_WITH_CONVERGENCE,
                RealtimeFlowType.fromLabel("mixed-with-convergence"));
    }

    @Test
    void testFlowIdFromFlowId() {
        // Verify flow ID round-trip conversion
        assertEquals(RealtimeFlowId.CHAT_MESSAGE_CREATE,
                RealtimeFlowId.fromFlowId("chat_message_create"));
        assertEquals(RealtimeFlowId.PRESENCE_USER_TYPING,
                RealtimeFlowId.fromFlowId("presence_user_typing"));
    }

    @Test
    void testInvalidFlowType() {
        // Verify invalid labels raise exception
        assertThrows(IllegalArgumentException.class, 
                () -> RealtimeFlowType.fromLabel("invalid-type"));
    }

    @Test
    void testInvalidFlowId() {
        // Verify invalid flow IDs raise exception
        assertThrows(IllegalArgumentException.class,
                () -> RealtimeFlowId.fromFlowId("invalid_flow_id"));
    }

    @Test
    void testUnclassifiedFlow() {
        // Verify accessing unclassified flow raises exception
        assertThrows(IllegalArgumentException.class,
                () -> RealtimeFlowClassificationPolicy.getFlowType(null));
    }
}

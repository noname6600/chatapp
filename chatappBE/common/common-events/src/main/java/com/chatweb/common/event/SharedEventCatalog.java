package com.chatweb.common.event;

import com.chatweb.common.integration.account.AccountCreatedPayload;
import com.chatweb.common.integration.account.AccountEventType;
import com.chatweb.common.integration.chat.ChatEventType;
import com.chatweb.common.integration.chat.ChatMessagePayload;
import com.chatweb.common.integration.chat.MessageDeletedPayload;
import com.chatweb.common.integration.chat.MessagePinPayload;
import com.chatweb.common.integration.chat.MessageUpdatedPayload;
import com.chatweb.common.integration.chat.ReactionPayload;
import com.chatweb.common.integration.friendship.FriendRequestPayload;
import com.chatweb.common.integration.friendship.FriendshipEventType;
import com.chatweb.common.integration.friendship.FriendshipPayload;
import com.chatweb.common.integration.notification.NotificationCreatedPayload;
import com.chatweb.common.integration.notification.NotificationEventType;
import com.chatweb.common.integration.notification.NotificationRequestedPayload;
import com.chatweb.common.integration.presence.PresenceEventType;
import com.chatweb.common.integration.presence.PresenceRoomJoinPayload;
import com.chatweb.common.integration.presence.PresenceRoomLeavePayload;
import com.chatweb.common.integration.presence.PresenceStopTypingPayload;
import com.chatweb.common.integration.presence.PresenceTypingPayload;
import com.chatweb.common.integration.presence.PresenceUserOfflinePayload;
import com.chatweb.common.integration.presence.PresenceUserOnlinePayload;
import com.chatweb.common.integration.presence.PresenceUserStatePayload;
import com.chatweb.common.integration.user.UserEventType;
import com.chatweb.common.integration.voice.CallEventPayload;
import com.chatweb.common.integration.voice.VoiceEventType;
import com.chatweb.common.integration.voice.VoiceRoomEventPayload;

import java.util.Set;

/**
 * Canonical event-to-payload catalog for all shared events in common-events.
 *
 * <p>This is the authoritative source of truth for which payload class belongs to
 * which event type string. It covers all event types defined in the
 * {@code com.chatweb.common.integration.*} domain enums.
 *
 * <p>Usage â€" pre-populate a registry in transport auto-configurations:
 * <pre>
 *   DefaultEventPayloadRegistry registry = new DefaultEventPayloadRegistry();
 *   SharedEventCatalog.registerAll(registry);
 * </pre>
 *
 * <p>Payload-less events are listed in {@link #PAYLOAD_LESS_EVENT_TYPES} so that
 * deserializers can distinguish "known event type with no shared payload" from
 * "completely unknown / unregistered event type".
 *
 * @since 2.2
 */
public final class SharedEventCatalog {

    private SharedEventCatalog() {}

    /**
     * Canonical set of event type values that are part of the shared contract
     * but carry no shared payload class.
     *
     * <p>These event types are valid and known â€" their absence from a
     * {@link EventPayloadRegistry} is intentional. Payload resolution must not
     * be attempted for them during deserialization.
     */
    public static final Set<String> PAYLOAD_LESS_EVENT_TYPES = Set.of(
            AccountEventType.ACCOUNT_DELETED.value(),
            AccountEventType.ACCOUNT_DISABLED.value(),
            ChatEventType.MEMBER_JOINED.value(),
            ChatEventType.MEMBER_LEFT.value(),
            ChatEventType.MEMBER_REMOVED.value(),
            NotificationEventType.NOTIFICATION_SENT.value(),
            UserEventType.PROFILE_CREATED.value(),
            UserEventType.PROFILE_UPDATED.value()
    );

    /**
     * Registers all payload-bearing shared events into the given registry.
     *
     * <p>This method is idempotent when called against the same registry:
     * re-registering the same event type with the same payload class is silently
     * accepted (no-op), while registering a different class for the same event
     * type throws {@link IllegalStateException}.
     *
     * <p>Payload-less event types listed in {@link #PAYLOAD_LESS_EVENT_TYPES}
     * are intentionally excluded â€" they carry no shared payload and should not
     * go through payload resolution.
     *
     * <p><strong>Important:</strong> Services must not re-register any of these shared event
     * types with different payload classes. The registry follows a first-registration-wins
     * policy; attempting to re-register a shared event type with a different payload class
     * will throw {@link IllegalStateException}. Service-specific event types that are not
     * part of the shared contract must use distinct event type strings not listed here.
     *
     * @param registry the registry to populate, not null
     */
    public static void registerAll(EventPayloadRegistry registry) {
        // Account
        registry.register(AccountEventType.ACCOUNT_CREATED.value(), AccountCreatedPayload.class);
        // ACCOUNT_DELETED, ACCOUNT_DISABLED are payload-less (see PAYLOAD_LESS_EVENT_TYPES)

        // Chat â€" message lifecycle
        registry.register(ChatEventType.MESSAGE_SENT.value(), ChatMessagePayload.class);
        registry.register(ChatEventType.MESSAGE_EDITED.value(), MessageUpdatedPayload.class);
        registry.register(ChatEventType.MESSAGE_DELETED.value(), MessageDeletedPayload.class);
        // NOTE: MESSAGE_PINNED and MESSAGE_UNPINNED use a common MessagePinPayload.
        // Do not re-register these events to service-local payload classes; use the shared contract.
        registry.register(ChatEventType.MESSAGE_PINNED.value(), MessagePinPayload.class);
        registry.register(ChatEventType.MESSAGE_UNPINNED.value(), MessagePinPayload.class);
        registry.register(ChatEventType.REACTION_UPDATED.value(), ReactionPayload.class);
        // MEMBER_JOINED, MEMBER_LEFT, MEMBER_REMOVED are payload-less (see PAYLOAD_LESS_EVENT_TYPES)

        // Friendship â€" request lifecycle (FriendRequestPayload); status changes (FriendshipPayload)
        registry.register(FriendshipEventType.FRIEND_REQUEST_SENT.value(), FriendRequestPayload.class);
        registry.register(FriendshipEventType.FRIEND_REQUEST_ACCEPTED.value(), FriendRequestPayload.class);
        registry.register(FriendshipEventType.FRIEND_REQUEST_DECLINED.value(), FriendRequestPayload.class);
        registry.register(FriendshipEventType.FRIEND_REQUEST_CANCELLED.value(), FriendRequestPayload.class);
        registry.register(FriendshipEventType.FRIEND_UNFRIENDED.value(), FriendshipPayload.class);
        registry.register(FriendshipEventType.FRIEND_BLOCKED.value(), FriendshipPayload.class);
        registry.register(FriendshipEventType.FRIEND_UNBLOCKED.value(), FriendshipPayload.class);

        // Notification
        registry.register(NotificationEventType.NOTIFICATION_REQUESTED.value(), NotificationRequestedPayload.class);
        registry.register(NotificationEventType.NOTIFICATION_CREATED.value(), NotificationCreatedPayload.class);
        // NOTIFICATION_SENT is payload-less (see PAYLOAD_LESS_EVENT_TYPES)

        // Presence â€" online/offline/status
        registry.register(PresenceEventType.USER_ONLINE.value(), PresenceUserOnlinePayload.class);
        registry.register(PresenceEventType.USER_OFFLINE.value(), PresenceUserOfflinePayload.class);
        registry.register(PresenceEventType.USER_STATUS_CHANGED.value(), PresenceUserStatePayload.class);

        // Presence â€" room interaction
        registry.register(PresenceEventType.ROOM_TYPING.value(), PresenceTypingPayload.class);
        registry.register(PresenceEventType.ROOM_STOP_TYPING.value(), PresenceStopTypingPayload.class);
        registry.register(PresenceEventType.ROOM_JOIN.value(), PresenceRoomJoinPayload.class);
        registry.register(PresenceEventType.ROOM_LEAVE.value(), PresenceRoomLeavePayload.class);

        // Voice -- room events
        registry.register(VoiceEventType.VOICE_ROOM_JOINED.value(), VoiceRoomEventPayload.class);
        registry.register(VoiceEventType.VOICE_ROOM_LEFT.value(), VoiceRoomEventPayload.class);
        registry.register(VoiceEventType.VOICE_ROOM_CREATED.value(), VoiceRoomEventPayload.class);
        registry.register(VoiceEventType.VOICE_ROOM_CLOSED.value(), VoiceRoomEventPayload.class);

        // Voice -- call events
        registry.register(VoiceEventType.CALL_INITIATED.value(), CallEventPayload.class);
        registry.register(VoiceEventType.CALL_ACCEPTED.value(), CallEventPayload.class);
        registry.register(VoiceEventType.CALL_DECLINED.value(), CallEventPayload.class);
        registry.register(VoiceEventType.CALL_CANCELLED.value(), CallEventPayload.class);
        registry.register(VoiceEventType.CALL_ENDED.value(), CallEventPayload.class);
        registry.register(VoiceEventType.CALL_MISSED.value(), CallEventPayload.class);
    }
}
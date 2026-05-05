package com.example.common.event;

import com.example.common.integration.account.AccountCreatedPayload;
import com.example.common.integration.account.AccountEventType;
import com.example.common.integration.chat.ChatEventType;
import com.example.common.integration.chat.ChatMessagePayload;
import com.example.common.integration.chat.MessageDeletedPayload;
import com.example.common.integration.chat.MessagePinPayload;
import com.example.common.integration.chat.MessageUpdatedPayload;
import com.example.common.integration.chat.ReactionPayload;
import com.example.common.integration.friendship.FriendRequestPayload;
import com.example.common.integration.friendship.FriendshipEventType;
import com.example.common.integration.friendship.FriendshipPayload;
import com.example.common.integration.notification.NotificationCreatedPayload;
import com.example.common.integration.notification.NotificationEventType;
import com.example.common.integration.notification.NotificationRequestedPayload;
import com.example.common.integration.presence.GlobalOnlineUsersPayload;
import com.example.common.integration.presence.PresenceEventType;
import com.example.common.integration.presence.PresenceHeartbeatPayload;
import com.example.common.integration.presence.PresenceRoomJoinPayload;
import com.example.common.integration.presence.PresenceRoomLeavePayload;
import com.example.common.integration.presence.PresenceStopTypingPayload;
import com.example.common.integration.presence.PresenceTypingPayload;
import com.example.common.integration.presence.PresenceUserOfflinePayload;
import com.example.common.integration.presence.PresenceUserOnlinePayload;
import com.example.common.integration.presence.PresenceUserStatePayload;
import com.example.common.integration.presence.RoomOnlineUsersPayload;
import com.example.common.integration.user.UserEventType;

import java.util.Set;

/**
 * Canonical event-to-payload catalog for all shared events in common-events.
 *
 * <p>This is the authoritative source of truth for which payload class belongs to
 * which event type string. It covers all event types defined in the
 * {@code com.example.common.integration.*} domain enums.
 *
 * <p>Usage — pre-populate a registry in transport auto-configurations:
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
     * <p>These event types are valid and known — their absence from a
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
     * are intentionally excluded — they carry no shared payload and should not
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

        // Chat — message lifecycle
        registry.register(ChatEventType.MESSAGE_SENT.value(), ChatMessagePayload.class);
        registry.register(ChatEventType.MESSAGE_EDITED.value(), MessageUpdatedPayload.class);
        registry.register(ChatEventType.MESSAGE_DELETED.value(), MessageDeletedPayload.class);
        // NOTE: MESSAGE_PINNED and MESSAGE_UNPINNED use a common MessagePinPayload.
        // Do not re-register these events to service-local payload classes; use the shared contract.
        registry.register(ChatEventType.MESSAGE_PINNED.value(), MessagePinPayload.class);
        registry.register(ChatEventType.MESSAGE_UNPINNED.value(), MessagePinPayload.class);
        registry.register(ChatEventType.REACTION_UPDATED.value(), ReactionPayload.class);
        // MEMBER_JOINED, MEMBER_LEFT, MEMBER_REMOVED are payload-less (see PAYLOAD_LESS_EVENT_TYPES)

        // Friendship — request lifecycle (FriendRequestPayload); status changes (FriendshipPayload)
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

        // Presence — online/offline/heartbeat
        registry.register(PresenceEventType.USER_ONLINE.value(), PresenceUserOnlinePayload.class);
        registry.register(PresenceEventType.USER_OFFLINE.value(), PresenceUserOfflinePayload.class);
        registry.register(PresenceEventType.USER_STATUS_CHANGED.value(), PresenceUserStatePayload.class);
        registry.register(PresenceEventType.USER_HEARTBEAT.value(), PresenceHeartbeatPayload.class);
        // (USER_STATUS_CHANGED is payload-bearing, see registration above)

        // Presence — room interaction
        registry.register(PresenceEventType.ROOM_TYPING.value(), PresenceTypingPayload.class);
        registry.register(PresenceEventType.ROOM_STOP_TYPING.value(), PresenceStopTypingPayload.class);
        registry.register(PresenceEventType.ROOM_JOIN.value(), PresenceRoomJoinPayload.class);
        registry.register(PresenceEventType.ROOM_LEAVE.value(), PresenceRoomLeavePayload.class);
        registry.register(PresenceEventType.GLOBAL_ONLINE_USERS.value(), GlobalOnlineUsersPayload.class);
        registry.register(PresenceEventType.ROOM_ONLINE_USERS.value(), RoomOnlineUsersPayload.class);
    }
}

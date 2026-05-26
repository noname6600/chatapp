# 10. Request To Event Lifecycle

This chapter traces complete paths using concrete classes.

## A) Send Message (Durable + Realtime)

### Step 1: Entry
- Client enters through gateway route to chat-service API or websocket SEND routed by realtime-edge chat command router.

### Step 2: Auth validation
- gateway validates JWT and sets X-User-Id
- chat-service resource server validates JWT again

### Step 3: Service command
- MessageCommandService uses send pipeline
- pipeline includes validation, blocked-check, permission, aggregate creation, sequence generation, persistence

### Step 4: DB write
- PersistMessageStep writes message and related entities

### Step 5: sequence/cache interaction
- RedisRoomSequenceService increments room:seq:{roomId}

### Step 6: after-commit publication
- PublishMessageEventStep registers TransactionSynchronization.afterCommit
- on commit -> ChatMessageEventPublisherAdapter.publishMessageCreated

### Step 7: dual publish
- Redis: ChatRedisPublisher.publishMessageSent -> realtime.chat.room.{roomId}
- Kafka: KafkaEventPublisher.publish(chat.message.sent,...)

### Step 8: downstream consumption
- realtime-edge RedisEventListener -> ChatRealtimeDeliveryService -> websocket fanout
- notification-service Kafka consumer -> notification persistence and push

### Step 9: frontend convergence
- immediate websocket update + eventual API consistency after notification and downstream side effects

### Step 10: error handling
- pipeline catches and maps business errors
- publish failures in afterCommit are logged; message may still exist in DB without full async propagation

## B) Typing Event (Ephemeral)
1. client sends presence.room.typing via websocket
2. realtime-edge RealtimeWebSocketHandler validates tokenRef and subscription state
3. PresenceDomainClient POST /api/v1/presence/ws/rooms/{roomId}/typing
4. PresenceEdgeCommandController validates room membership and JWT
5. PresenceRealtimePort publishRoomEvent with PresenceTypingPayload
6. PresenceRedisPublisher publishes to realtime.presence.room.{roomId}
7. realtime-edge RedisEventListener receives
8. PresenceRealtimeDeliveryService delivers to room subscribers
9. clients render typing indicator
10. if failures occur, operation is non-fatal and often warn-throttled in PresenceDomainClient

## C) WebSocket Connect
1. client calls POST /api/v1/realtime/ticket with bearer
2. RealtimeTicketController stores ws:ticket key TTL 30s
3. client connects ws /realtime?ticket=...
4. JwtHandshakeInterceptor validates/deletes ticket, stores ws:session-token:{ref}
5. RealtimeWebSocketHandler registers RealtimeSession + default subscriptions
6. Presence bridge sends connect + global snapshot
7. session lease and counters managed in selected registry backend
8. periodic EdgeSessionMaintenanceJob evicts stale sessions
9. token ref checked on every inbound message
10. missing token ref closes socket

## D) Notification Event
1. source event consumed in notification-service Kafka consumer (for example friend.request events)
2. NotificationKafkaEventApplicationService resolves policy and creates notification
3. NotificationCommandService persists and schedules push after commit
4. NotificationPushService calls NotificationRealtimePort
5. NotificationWebSocketPublisher delegates to RedisNotificationPublisher
6. redis publishes realtime.notification.user.{userId}
7. realtime-edge listener receives and pushes websocket frames
8. unread-count push occurs as separate event

## E) Presence Offline By TTL
1. presence state key expires
2. expected key-expired listener path: PresenceKeyExpiredListener.handle
3. PresenceService.handleUserOfflineByTTL clears connection counts and room presence
4. publishes presence.user.offline
5. realtime-edge forwards to subscribed users

Note: startup check validates Redis keyspace notification config; explicit listener container registration for key-expiry events was not located in presence-service source and should be verified operationally.

## F) Friendship Lifecycle
1. FriendCommandService modifies friendship state
2. publishAfterCommit schedules producer call
3. FriendshipEventProducer routes:
   - friend.request.* -> friendship.request.events
   - friend.* status -> friendship.events
4. notification-service consumes for notifications
5. realtime-edge consumes Kafka friendship events
6. FriendshipRealtimeDeliveryService sends local sessions and handoff for remote instance sessions
7. clients receive friendship websocket events

## G) Room Update (Membership/Moderation Style Event)
1. room mutation is executed in chat-service room command path
2. DB update commits (member join/leave/remove or metadata update)
3. chat-service publishes room-related realtime event through ChatRedisPublisher.publishRoomRealtimeEvent when applicable
4. channel used: realtime.chat.room.{roomId}
5. realtime-edge RedisEventListener receives event
6. for member join/left/removed event types, listener invalidates room access cache entries through ChannelSubscriptionManager.invalidateRoomAccess
7. ChatRealtimeDeliveryService emits websocket frame to room subscribers
8. clients update room state (member list/permission-sensitive UI)

## H) Media Upload (Prepare/Confirm)
1. client requests upload policy/signature through upload-service endpoints
2. upload-service validates JWT and upload policy constraints
3. client uploads binary to cloud provider
4. client confirms upload metadata to upload-service
5. business services consume returned secure metadata (for example avatar/chat attachment usage)
6. if attachment is used in a message send command, normal message pipeline then emits Kafka+Redis events

Note: upload itself is not a Redis/Kafka event producer in the traced path; eventing occurs when uploaded asset becomes part of domain commands (chat message/avatar update).

# 05. Redis Pub/Sub Flow

## 1) Why Redis Pub/Sub Exists Here
Kafka is used for durable cross-service eventing. Redis pub/sub is used for low-latency fanout to websocket clients.

Design intent:
- domain writes and durable convergence on Kafka
- immediate UI synchronization on Redis->realtime-edge->WebSocket

This split gives responsiveness, but introduces guarantee asymmetry.

## 2) Publisher Construction
Publisher abstraction is built by common-redis:
- RedisAutoConfiguration creates RedisEventPublisher (DefaultRedisEventPublisher)
- services inject RedisEventPublisher into domain adapters

Concrete publisher adapters:
- chat-service ChatRedisPublisher
- presence-service PresenceRedisPublisher
- notification-service RedisNotificationPublisher
- realtime-edge EdgeDeliveryHandoffPublisher (special internal cross-instance dispatch channel)

## 3) Subscriber Registration
Realtime edge subscribes using RedisMessageListenerContainer in RedisListenerConfig.

Subscriptions:
- realtime.chat.room.*
- realtime.notification.user.*
- realtime.presence.*
- realtime.edge.handoff.* (conditional)

MessageListener targets:
- RedisEventListener for chat/notification/presence channels
- EdgeDeliveryHandoffListener for handoff channels

## 4) Event Serialization On Publish
All domain events use EventEnvelope with metadata + payload.

Serialize path:
- DefaultRedisEventPublisher.publish(channel,envelope)
- validate metadata and event name
- JsonRedisEventSerializer.serialize(envelope)
- StringRedisTemplate.convertAndSend(channel,payload)

## 5) Event Handling In Realtime Edge
RedisEventListener.onMessage:
1. decode channel and payload string
2. parse JSON with ObjectMapper
3. extract metadata.eventType and metadata.eventId
4. route by channel prefix

Routing targets:
- notification user channel -> NotificationRealtimeDeliveryService.deliverToUser
- chat room channel -> ChatRealtimeDeliveryService.deliverRoom
- presence global/user -> PresenceRealtimeDeliveryService.deliverGlobal
- presence room channel -> PresenceRealtimeDeliveryService.deliverRoom

## 6) Re-Broadcast To Clients
Delivery services query session registry for subscribed sessions and push via WebSocketOutboundDeliveryQueue.

Chat delivery behavior:
- local sessions only (findByChannelOwnedByCurrentInstance)
- no remote handoff for chat, because every instance already receives pub/sub broadcast

Friendship delivery behavior:
- split local vs remote ownership
- local sessions sent directly
- remote sessions notified via handoff channel

## 7) Full Lifecycle Example: Message Sent
User action -> API -> service -> publish -> edge -> websocket:
1. client sends message command (REST or websocket route through edge to chat command router)
2. chat persists message via pipeline
3. PublishMessageEventStep triggers event publication after commit
4. ChatMessageEventPublisherAdapter publishes to:
   - Redis channel realtime.chat.room.{roomId}
   - Kafka event topic (event-type route)
5. realtime-edge RedisEventListener receives room event
6. ChatRealtimeDeliveryService finds local subscribed sessions for room:{roomId}
7. WebSocketOutboundDeliveryQueue enqueues and worker threads send frames
8. clients receive event with eventType + payload + eventId

## 8) Full Lifecycle Example: Typing Event
1. client sends presence.room.typing websocket frame
2. RealtimeWebSocketHandler forwards to PresenceDomainClient.typing
3. presence-service PresenceEdgeCommandController validates room membership
4. presence-service publishes PresenceTypingPayload via PresenceRedisPublisher to realtime.presence.room.{roomId}
5. realtime-edge RedisEventListener routes to PresenceRealtimeDeliveryService.deliverRoom
6. subscribed room clients receive typing event

## 9) Multi-Instance Synchronization Logic

### Chat/presence/notification
- all edge instances subscribe to same Redis patterns
- each instance receives same pub/sub message
- each instance delivers only sessions it owns locally

### Friendship
- event source is Kafka consumers in realtime-edge
- coordinator splits local vs remote session ownership
- remote delivery done via handoff pub/sub channels targeting instance-specific suffixes

## 10) Ordering Guarantees
- Redis pub/sub preserves publish order per publisher connection and channel stream under normal conditions.
- Global ordering across services/instances is not guaranteed.
- Client-observed ordering can diverge due to:
  - network delay
  - concurrent publishers
  - per-session queue buffering

## 11) Duplicate Risks
Potential duplicate sources:
- same event arriving via multiple paths in migration/hybrid flows
- handoff replay or duplicate publish attempts
- retry logic in upstream layers

Mitigations seen:
- dedupe guards for friendship/notification in Kafka paths
- eventId propagated to clients for UI idempotency potential

## 12) Delivery Guarantees
Redis pub/sub provides at-most-once semantics for disconnected subscribers:
- if subscriber connection is down at publish time, message is lost for that subscriber
- no replay buffer in Redis pub/sub path

Therefore, realtime delivery is best-effort and must be combined with API refresh/poll or durable event path for convergence.

## 13) Failure Behavior
If Redis unavailable:
- publish calls throw RedisPubSubException
- listeners stop receiving events
- realtime UI updates degrade
- durable Kafka events may still persist and be consumed elsewhere

If realtime-edge listener disabled:
- no websocket fanout from Redis despite producers publishing

If client disconnects:
- websocket queue clears session
- missed realtime events require state sync via API after reconnect

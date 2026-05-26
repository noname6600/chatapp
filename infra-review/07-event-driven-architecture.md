# 07. Event-Driven Architecture

## 1) Core Event Contract
Every shared event uses EventEnvelope<T> with EventMetadata:
- eventId
- eventType
- sourceService
- createdAt
- correlationId

The central contract authority is SharedEventCatalog.

## 2) Event Naming Conventions
Naming is domain.action style and largely aligned across transports:
- account.created
- chat.message.sent
- chat.message.edited
- chat.message.deleted
- chat.reaction.updated
- friend.request.sent / accepted / declined / cancelled
- friend.blocked / unblocked / unfriended
- notification.requested / created
- presence.user.online / offline / status.changed
- presence.room.typing / stop_typing / join / leave

## 3) Payload Schema Ownership
Payload classes are in common-events integration packages:
- integration.account
- integration.chat
- integration.friendship
- integration.notification
- integration.presence

This avoids per-service ad-hoc schemas and serializer divergence.

## 4) Producer Map

### auth-service
Producer: AccountCreatedEventProducer
Events: account.created
Transport: Kafka

### friendship-service
Producer: FriendshipEventProducer
Events:
- friend.request.* -> friendship.request.events topic
- friend.* status events -> friendship.events topic
Transport: Kafka

### chat-service
Producer adapter: ChatMessageEventPublisherAdapter
Events:
- chat.message.sent
- chat.message.edited/deleted
- chat.reaction.updated
Transport:
- Redis realtime fanout
- Kafka durable propagation

### presence-service
Producer: PresenceRedisPublisher
Events: presence.*
Transport: Redis pub/sub

### notification-service
Producer paths:
- NotificationEventProducer for notification.requested (Kafka)
- NotificationWebSocketPublisher/RedisNotificationPublisher for realtime.notification.user.*

## 5) Consumer Map

### user-service
Consumer: AccountCreatedConsumer
Input: account.created
Side effect: create user profile data flow

### notification-service
Consumers:
- MessageCreatedEventConsumer
- FriendRequestEventConsumer
- reaction/mutation/account consumers
Input: chat/friend/account topics
Side effect: notification persistence + realtime push

### chat-service
Consumer: FriendshipBlockEventConsumer
Input: friendship.events
Side effect: invalidate blocked-pair cache

### realtime-edge
Consumer: FriendshipKafkaEventConsumer
Input: friendship.events + friendship.request.events
Side effect: websocket friendship events to target users

### realtime-edge Redis listener
Consumer: RedisEventListener
Input: realtime.chat.room.*, realtime.notification.user.*, realtime.presence.*
Side effect: websocket delivery services

## 6) Internal Vs External Event Boundaries
- External (cross-service) contract events are in common-events and Kafka routes.
- Internal realtime events are mostly Redis channel fanout to realtime-edge.
- Some events are semantically shared but transport differs by durability need.

## 7) Sync Vs Async Boundaries
Synchronous boundaries:
- REST command execution and transactional DB writes
- immediate projection updates in chat send pipeline

Asynchronous boundaries:
- Kafka consumers for eventual cross-service effects
- Redis pub/sub for realtime broadcast
- afterCommit callbacks

## 8) Event Versioning Approach
No explicit version field was found in metadata.
Versioning currently relies on:
- stable eventType naming
- shared payload class compatibility

Risk: schema evolution requires strict backwards compatibility discipline without explicit version negotiation.

## 9) Event Map

### Account Created
auth-service -> Kafka account.created -> user-service + notification-service

### Message Sent
chat-service write -> Redis realtime.chat.room.{roomId} + Kafka chat.message.sent
- realtime-edge consumes Redis and pushes websocket
- notification-service consumes Kafka and creates notification

### Friendship Request
friendship-service -> Kafka friendship.request.events
- notification-service consumes for notification
- realtime-edge consumes for websocket friendship alerts

### Presence Typing
realtime-edge command -> presence-service -> Redis realtime.presence.room.{roomId}
- realtime-edge consumes redis -> room websocket broadcast

## 10) Consistency Model
- Durable eventual consistency: Kafka paths
- Ephemeral convergence: Redis pub/sub paths
- Immediate local correctness: DB transaction boundaries and direct API responses

System is intentionally hybrid; correctness depends on reconciling these planes.

## 11) Threading/Concurrency Considerations
- @KafkaListener methods process on Kafka consumer threads.
- Redis listener callback threads process pub/sub events.
- WebSocket outbound queue decouples send path from listener callbacks.
- afterCommit scheduling ensures publication order relative to transaction completion.

## 12) Coupling Risks
- Shared event catalog centralization couples all producers and consumers to same library release cadence.
- Transport and event semantics are sometimes linked through eventType-as-topic design.
- Missing universal idempotency utilities across all consumers can produce inconsistent duplicate handling.

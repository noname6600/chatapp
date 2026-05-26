# 08. Event-Driven Flow

## Event Model
All shared events use:
- `EventEnvelope<T>`
- `EventMetadata` (`eventId`, `eventType`, `sourceService`, `createdAt`, `correlationId`)

Contracts are centralized in `common-events/SharedEventCatalog.java`.

## Producers
- auth-service: account created
- chat-service: message created/edited/deleted, reaction updates
- friendship-service: request/status events
- notification-service: notification-created/requested events
- presence-service: presence room/global/user events (mainly Redis)

## Consumers
- user-service consumes account created
- notification-service consumes account/chat/friendship topics
- chat-service consumes friendship block updates for cache invalidation
- realtime-edge consumes friendship (and realtime feeds via delivery adapters)

## Kafka Topic Topology
From `KafkaTopics`:
- `account.created`
- `chat.message.sent`
- `chat.message.events`
- `chat.reaction.updated`
- `friendship.events`
- `friendship.request.events`
- infra: `system.dead-letter`, `system.retry`

## Redis Event Topology
From `RedisChannels`:
- `realtime.chat.room.*`
- `realtime.notification.user.*`
- `realtime.presence.*`

Used for low-latency fanout, not durable replay.

## Retry, DLQ, Idempotency
Observed:
- selected dedupe guards (for example friendship in realtime-edge) based on event id.
- publisher retries are local in some components (for example handoff publisher backoff).
- there is no uniformly enforced consumer retry/DLQ policy per service in code-level handlers.

Implication:
- event handling behavior is heterogeneous; operational runbooks must cover each consumer path separately.

## Event Lifecycle Example: Message Sent
1. API request writes chat message in DB.
2. publish-after-commit step triggers event publisher adapter.
3. adapter emits Redis room message and Kafka `chat.message.sent` envelope.
4. notification-service consumes and persists notification if needed.
5. realtime-edge delivery service sends websocket frame to subscribed room sessions.

## Event Lifecycle Example: Friendship Block
1. friendship-service emits `friend.blocked`.
2. chat-service `FriendshipBlockEventConsumer` invalidates block cache.
3. realtime-edge friendship consumer fans event to user channels.

## Consistency Model
- durable eventual consistency via Kafka.
- best-effort realtime convergence via Redis and websocket.
- no end-to-end exactly-once semantics.

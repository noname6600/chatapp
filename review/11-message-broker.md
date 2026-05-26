# 11. Message Broker

## Broker in Use
Kafka is the durable event bus (single broker in local compose, likely multi-broker in real production target).

## Producer Usage
Producers publish `EventEnvelope` payloads via `KafkaEventPublisher` abstraction.
- chat publishes message/reaction events.
- friendship publishes relationship events.
- auth publishes account-created.

## Consumer Usage
Consumers are implemented with `@KafkaListener` in service-specific classes.
Examples:
- `user-service/.../AccountCreatedConsumer.java`
- `notification-service/infrastructure/kafka/*Consumer.java`
- `chat-service/.../FriendshipBlockEventConsumer.java`
- `realtime-edge-service/adapter/in/kafka/FriendshipKafkaEventConsumer.java`

## Topic Design
Two patterns coexist:
- event-type topics (e.g., `chat.message.sent`)
- aggregate topics (e.g., `friendship.events` containing multiple event types)

This supports flexibility but requires disciplined consumer filtering and documentation.

## Retry and Error Handling
- Some consumers catch exceptions and log warnings (best-effort).
- No global uniform DLQ policy is evident per consumer class.
- `KafkaTopics` defines system retry/DLQ topic constants, but cross-service usage is not uniformly enforced.

## Ordering and Delivery Semantics
- Kafka provides partition ordering, not global ordering.
- Event id dedupe appears in selected consumers only.
- Exactly-once processing is not end-to-end guaranteed.

## Poison Message Handling
Current code paths rely mainly on deserializer behavior + consumer guard clauses.
Operationally this means poison-message strategy is partially implemented and not fully standardized.

## Production Implications
Strength:
- durable backbone for cross-service convergence.

Risks:
- inconsistent consumer recovery patterns.
- replay behavior depends on per-consumer idempotency quality.
- local compose defaults (`auto-create-topics`) are not production-safe defaults.

## Suggested Hardening
- standard consumer error policy (retry budget + DLQ + alert).
- shared idempotency utilities in common layer.
- topic ownership and schema versioning policy with compatibility tests.

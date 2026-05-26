# 06. Kafka Core

## 1) Kafka’s Role
Kafka is the durable event backbone for inter-service asynchronous communication.

Use cases in this codebase:
- account lifecycle propagation (auth -> user/notification)
- friendship lifecycle propagation (friendship -> notification/chat/realtime-edge)
- message and reaction events (chat -> notification and others)

## 2) Topic Structure
From KafkaTopics:
- friendship.events
- friendship.request.events
- account.created
- chat.message.sent
- chat.message.events
- chat.reaction.updated
- system.dead-letter
- system.retry

Important nuance: many producers route by event-type string directly (for example account.created, chat.message.sent), while aggregate routes use explicit constants (friendship.events, friendship.request.events).

## 3) Producer Pipeline
Producer abstraction:
- KafkaEventPublisher interface
- DefaultKafkaEventPublisher implementation

Publish flow:
1. validate topic not blank and envelope not null
2. validate eventType and metadata identity fields
3. kafkaTemplate.send(topic,key,envelope)
4. async completion callback logs success/error

No transactional outbox is implemented in this layer.

## 4) Serialization
Serializer:
- EventEnvelopeKafkaSerializer validates metadata and event name, serializes JSON

Deserializer:
- EventEnvelopeKafkaDeserializer
- validates metadata and eventType
- checks payload-less vs payload-bearing rules from SharedEventCatalog
- resolves payload class from registry

Result: consumer receives typed EventEnvelope payload where event contracts are centrally enforced.

## 5) Consumer Registration
Consumers use @KafkaListener in each service.

Examples:
- user-service AccountCreatedConsumer on account.created
- notification-service MessageCreatedEventConsumer on chat.message.sent
- notification-service FriendRequestEventConsumer on friendship.request.events
- chat-service FriendshipBlockEventConsumer on friendship.events
- realtime-edge FriendshipKafkaEventConsumer on friendship.events + friendship.request.events

## 6) Consumer Group Behavior
Observed explicit groups:
- notification-service group constant NotificationKafkaConsumerGroups.DEFAULT
- realtime-edge-friendship-group
- chat-service-block-cache
- user-service configured in application.yaml

Within same group: partition ownership split.
Across different groups: each group gets its own copy of events.

## 7) Retry And DLQ
Not globally uniform.

### user-service
KafkaConsumerConfig sets:
- DefaultErrorHandler
- DeadLetterPublishingRecoverer
- FixedBackOff(1000ms, 3 retries)

### notification-service
KafkaConsumerConfig sets:
- DefaultErrorHandler
- DeadLetterPublishingRecoverer targeting system.dead-letter partition mapping
- ExponentialBackOff based on notification.kafka.retry.* properties

### others
Some consumers rely on local try/catch and logging patterns.

Operational implication: recovery guarantees differ by service.

## 8) Kafka vs Redis In This System

Kafka:
- durable log
- replay possible
- consumer offsets and groups
- better for eventual consistency and cross-service state transitions

Redis pub/sub:
- ephemeral push
- no replay
- lower latency for fanout
- better for interactive realtime UX

## 9) Event Production Timing (After Commit Pattern)
Many domain services defer publication until DB commit using TransactionSynchronizationManager.afterCommit wrappers.

Examples:
- auth AccountCreatedAfterCommitPublisher via TransactionalAfterCommitExecutor
- friendship FriendCommandService.publishAfterCommit
- chat PublishMessageEventStep.publishMessageCreatedAfterCommit
- notification push side effects after commit

This avoids phantom events on transaction rollback.

## 10) Ordering Guarantees
- Kafka guarantees order within partition, not across partitions/topics.
- Key choice matters. Many producers key by entity IDs (room/message/account/friendship id string).
- Cross-topic ordering is not guaranteed.

## 11) Replay Capability
Because Kafka stores events, consumers can replay from offsets if needed. However, application-level idempotency is required to safely replay.

Deduplication mechanisms present:
- FriendshipEventDedupeGuard (realtime-edge)
- NotificationEventDedupeGuard (notification)

## 12) Failure Semantics
Producer failures:
- async callback logs errors
- without outbox, event can be lost after DB commit if broker path fails at publish time

Consumer failures:
- with DefaultErrorHandler configured -> retries then DLQ
- without robust handler -> risk of repeated failures or message skip behavior depending container config

## 13) Scaling Behavior
- Producers scale horizontally with service instances.
- Consumers scale per group by partition count.
- Throughput tuning depends on topic partitioning strategy, which is not explicitly modeled in source docs and appears environment-defined.

## 14) Real Improvement Opportunities
- standardize consumer error policy across all services
- document partitioning strategy per topic
- add outbox/CDC for strong DB-event atomicity
- unify dedupe strategy library in common module

# Kafka Architecture Review — chatappBE
> Deep-dive audit | Date: 2026-05-20 | Reviewer: multi-agent analysis

---

## 1. Broker Configuration

| Setting | Current | Required for Production |
|---------|---------|------------------------|
| Broker count | 1 | ≥3 |
| Replication Factor | 1 | 3 |
| `min.insync.replicas` | default (1) | 2 |
| Topic auto-creation | `KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"` | `false` |
| ZooKeeper mode | CP 7.6.0 + ZooKeeper | KRaft (ZK deprecated in Kafka 3.x) |
| DLQ topics | Created, never consumed | Needs dedicated consumer |
| Schema Registry | None | Required for schema evolution |
| Rack awareness | Not configured | Required for multi-AZ |

**Critical:** With RF=1, any broker restart causes complete message loss for all in-flight messages. In a chat application, this means users lose messages silently.

---

## 2. Topic Inventory

| Topic | Partitions | Producer | Consumer(s) | Partition Key | Issue |
|-------|-----------|----------|-------------|--------------|-------|
| `auth.account-created` | auto | auth-service (afterCommit) | notification-service | none | No key → round-robin; ordering lost |
| `chat.message-sent` | auto | chat-service (pipeline) | notification-service, realtime-edge | `roomId.toString()` | Correct |
| `chat.message-edited` | auto | chat-service (pipeline) | notification-service, realtime-edge | `roomId.toString()` | Missing afterCommit guard |
| `chat.message-deleted` | auto | chat-service (pipeline) | notification-service, realtime-edge | `roomId.toString()` | Missing afterCommit guard |
| `chat.message-reaction-added` | auto | chat-service (pipeline) | notification-service, realtime-edge | `roomId.toString()` | Missing afterCommit guard |
| `chat.message-reaction-removed` | auto | chat-service (pipeline) | notification-service, realtime-edge | `roomId.toString()` | Missing afterCommit guard |
| `chat.message-pinned` | auto | UNIMPLEMENTED | notification-service (consumes) | N/A | Kafka listener with no producer |
| `chat.message-unpinned` | auto | UNIMPLEMENTED | notification-service (consumes) | N/A | Kafka listener with no producer |
| `friendship.*` topics | auto | friendship-service | realtime-edge | userId? | realtime-edge has NO groupId |
| `*.dlq` topics | auto-created | DefaultErrorHandler | None | N/A | DLQ messages never processed |
| `system.retry` | auto | Nobody | Nobody | N/A | Dead constant in `KafkaTopics` |

---

## 3. Consumer Group Design

### KAFKA-01 — CRITICAL | FriendshipKafkaEventConsumer Has No Consumer Group ID

**Affected file:** `realtime-edge-service/src/main/java/com/chatweb/realtime/adapter/in/kafka/FriendshipKafkaEventConsumer.java`

```java
@KafkaListener(topics = {
    KafkaTopics.TOPIC_FRIENDSHIP_REQUEST_SENT,
    KafkaTopics.TOPIC_FRIENDSHIP_ACCEPTED,
    KafkaTopics.TOPIC_FRIENDSHIP_DECLINED,
    KafkaTopics.TOPIC_FRIENDSHIP_BLOCKED
    // NO groupId parameter
})
```

When `groupId` is absent, Spring Kafka falls back to `spring.kafka.consumer.group-id`. If that property is also missing, Spring generates a **random UUID** per application start. Consequences:

1. Every restart of realtime-edge creates a new consumer group — Kafka retains all unconsumed offsets from old groups indefinitely (log retention), causing offset buildup.
2. In multi-instance deployment, each instance creates its own random group and receives **all** friendship events — every instance processes every event (fan-out ×N instead of load-balanced).
3. Kafka's offset management is bypassed — on reconnect, the consumer starts from the **latest offset** by default, silently dropping all friendship events that arrived during a restart.

**Fix:** Add explicit `groupId = "${spring.kafka.consumer.group-id}"` or define a constant `"realtime-edge-friendship-group"`.

---

### KAFKA-02 — HIGH | Notification Service Consumer Group Naming

**Affected:** `notification-service` KafkaConsumerConfig

The notification-service uses a single consumer group for all topics. In multi-instance deployment, only one instance consumes each partition. This is correct behavior but means notification fan-out requires the consumer to fan out to all user WebSocket connections internally — which it does via Redis pub/sub. This path is correct but untested.

---

### KAFKA-03 — MEDIUM | Chat-service Kafka Event Consumer Missing

The chat-service publishes events but has no Kafka consumer. If the realtime delivery service needs to catch up after downtime, there is no replay mechanism — events consumed from the `chat.*` topics are permanently gone once processed (no retention-based replay).

---

## 4. Producer Configuration

### KAFKA-04 — CRITICAL | Fire-and-Forget Producer — No Delivery Guarantee

**Affected file:** `common-kafka/src/main/java/com/chatweb/common/kafka/producer/DefaultKafkaEventPublisher.java`

```java
// Inferred from review: kafkaTemplate.send() without .get() or callback
kafkaTemplate.send(topic, key, event);
// No acks configuration, no retries, no error callback
```

The Kafka producer uses default settings:
- `acks=1` (leader acknowledgment only) — data loss on leader failure between ack and follower replication
- `retries=0` (default in some Spring Boot versions) — transient network errors cause silent loss
- No `ProducerListener` for failure alerting
- No `KafkaTemplate.executeInTransaction()` — Kafka transactions not used

**Fix:**
```yaml
spring:
  kafka:
    producer:
      acks: all
      retries: 3
      properties:
        enable.idempotence: true
        max.in.flight.requests.per.connection: 5
```

---

### KAFKA-05 — CRITICAL | No Transactional Outbox — Dual-Write Race Condition

**Affected:** All Kafka publish points after DB writes

The canonical dual-write problem: DB commits and Kafka publish are separate operations. If the application crashes between the DB commit and the Kafka `send()`, the event is permanently lost. Current approach:

```
DB.commit() → [crash window] → kafkaTemplate.send()
```

Two failure modes:
1. **Lost event:** DB committed, app crashes before send → downstream services never informed
2. **Duplicate event:** Send retried after network timeout, broker already received it → duplicate processing (mitigated by idempotent producer but not eliminated end-to-end)

**Affected flows:**
- `auth-service`: `TransactionalAfterCommitExecutor.runAfterCommit()` — sends `auth.account-created` after auth-service transaction commits. If the afterCommit callback throws, the exception is swallowed.
- `chat-service`: `PublishMessageSentEventStep` runs in DAG; `PublishMessageEditedEventStep`, `PublishMessageDeletedEventStep`, `PublishReactionEventStep` have NO afterCommit guard — events may be published before their corresponding DB transaction commits.
- `friendship-service`: Publishes friendship events after DB save.

**Fix:** Implement the Transactional Outbox pattern:
1. In the same DB transaction, write the event to an `outbox` table.
2. A separate relay process (Debezium CDC or a polling scheduler) reads the outbox and publishes to Kafka.
3. Mark outbox entries as published only after Kafka broker acks.

---

### KAFKA-06 — CRITICAL | PipelineExecutor Breaks @Transactional — Events Published from Wrong Thread

**Affected file:** `chat-service/src/main/java/com/chatweb/chat/application/pipeline/PipelineExecutor.java`

```java
CompletableFuture.runAsync(() -> step.execute(context));
```

`CompletableFuture.runAsync()` executes on the ForkJoinPool common thread pool (or a custom executor). Spring's `@Transactional` uses `ThreadLocal` to bind the transaction. When a step moves to a new thread:

1. The new thread has no transaction context — `@Transactional` on the step becomes a **new, independent transaction**.
2. `PersistMessageStep.@Transactional` opens a new transaction on the async thread, saves the message, commits — but if a later step fails, the message is already committed and cannot be rolled back.
3. `PublishMessageSentEventStep` checks `TransactionSynchronizationManager.isSynchronizationActive()` — on the async thread this returns `false`, so it publishes immediately without waiting for commit — **phantom events for messages that may be rolled back**.
4. The afterCommit hook registered by `TransactionalAfterCommitExecutor` fires on the original thread's commit, not the async step's commit.

**Fix:** Either:
- Run the pipeline synchronously (remove `CompletableFuture.runAsync`), or
- Pass the transaction context explicitly using `TransactionSynchronizationManager.bindResource()`, or
- Use Spring's `@Async` with a `TaskExecutor` that is transaction-aware.

---

## 5. Event Publishing Correctness

### KAFKA-07 — HIGH | Edit/Delete/Reaction Events Published Before Commit

**Affected files:**
- `chat-service/.../pipeline/edit/steps/PublishMessageEditedEventStep.java`
- `chat-service/.../pipeline/delete/steps/PublishMessageDeletedEventStep.java`
- `chat-service/.../pipeline/reaction/steps/PublishReactionEventStep.java`

The `send-message` pipeline's `PublishMessageSentEventStep` correctly uses an afterCommit hook:
```java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        kafkaTemplate.send(...);
    }
});
```

But the edit, delete, and reaction publish steps publish **directly** without afterCommit registration. If the DB transaction is rolled back (e.g., optimistic lock collision on edit), the Kafka event is already published — downstream services will process an edit/delete/reaction for a message that was never actually modified.

**Fix:** Apply the same afterCommit pattern to all 4 publish steps.

---

### KAFKA-08 — HIGH | auth-service afterCommit Publisher Blocks Servlet Thread

**Affected file:** `auth-service/src/main/java/com/chatweb/auth/service/event/TransactionalAfterCommitExecutor.java`

The afterCommit hook runs synchronously on the **servlet request thread** after the DB transaction commits. The hook calls `kafkaTemplate.send()` which is asynchronous by default but still involves network I/O (DNS, connection setup, serialization). Under load:

- 2-second send timeout blocks the thread for 2 full seconds
- Thread pool exhaustion cascades: all login/register endpoints stall
- Spring's `SessionFactory` is tied to the request thread during this period

**Fix:** Submit the Kafka publish to a dedicated `ThreadPoolTaskExecutor` asynchronously. Accept that the publish may fail and implement a retry/outbox mechanism.

---

### KAFKA-09 — MEDIUM | MESSAGE_PINNED / MESSAGE_UNPINNED Events Never Published

**Affected:** `notification-service` has `@KafkaListener` for `chat.message-pinned` and `chat.message-unpinned` topics. No service publishes to these topics.

The notification-service will never receive these events, but the listener threads are allocated, the consumer group is registered with Kafka, and offsets are tracked. Dead consumer groups accumulate and slow down Kafka's internal group coordinator.

**Fix:** Either implement the pinning event publishers in chat-service, or remove the dead listeners.

---

## 6. Dead Letter Queue Design

### KAFKA-10 — HIGH | DLQ Topics Exist But Are Never Consumed

**Affected:** `notification-service/src/main/java/com/chatweb/notification/infrastructure/kafka/KafkaConsumerConfig.java`

```java
DefaultErrorHandler errorHandler = new DefaultErrorHandler(
    new DeadLetterPublishingRecoverer(kafkaTemplate),
    new FixedBackOff(1000L, 3L)
);
```

After 3 retries, failed messages are routed to `<original-topic>.DLT` (dead letter topic). These DLT topics:
- Are created automatically (auto-create enabled)
- Receive failed messages
- Are **never consumed by any service**
- Retain messages for the default retention period (7 days)
- Silently accumulate without alerting

Additionally, the DLQ partition configuration:
```java
new DeadLetterPublishingRecoverer(kafkaTemplate, (record, ex) -> 
    new TopicPartition(record.topic() + ".DLT", -1))
```
Partition `-1` means "auto-assign" — Kafka may create a DLT with a different partition count than the source topic, causing routing issues for keyed messages.

**Fix:**
1. Implement a DLQ consumer that alerts + stores failed messages for manual inspection.
2. Use partition `record.partition()` instead of `-1` to preserve ordering guarantees.

---

## 7. Deserialization Error Handling

### KAFKA-11 — HIGH | Deserialization Failures Kill Consumer Thread

**Affected:** All Kafka consumers using `JsonDeserializer`

If a message cannot be deserialized (malformed JSON, missing field, schema evolution), Spring Kafka's default behavior throws a `DeserializationException`. Without `ErrorHandlingDeserializer`:
- The consumer thread throws on `poll()`
- The exception propagates to the `KafkaMessageListenerContainer`
- The container logs the error and **retries the same message indefinitely** (poison pill)
- The entire partition is stuck; no other messages on that partition are processed

**Fix:** Wrap deserializers in `ErrorHandlingDeserializer`:
```java
props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
```
And configure the `DefaultErrorHandler` to route deserialization failures to DLQ.

---

## 8. Schema Evolution

### KAFKA-12 — HIGH | No Schema Registry — Breaking Changes Are Silent

All Kafka events use JSON serialization with no schema registry (no Avro, no Protobuf, no JSON Schema). Event classes are in `common-events/SharedEventCatalog.java`.

**Risk:** If `chat.message-sent` event gains a new required field (no default), consumers running the old version will deserialize successfully (Jackson ignores unknown fields by default) but miss the new data. If a field is renamed or removed, consumers will receive `null` silently.

**Specific version mismatch scenario:**
1. Deploy new chat-service that renames `senderId` → `authorId`
2. Old notification-service reads `senderId` as `null`
3. Notification routing fails silently — no message delivered
4. No alert, no DLQ entry (deserialization succeeds)

**Fix:**
- Add `@JsonIgnoreProperties(ignoreUnknown = false)` to fail on unknown fields during development
- Use backward-compatible schema evolution: add fields with defaults, never remove/rename
- Long-term: adopt Confluent Schema Registry with Avro for enforced compatibility

---

## 9. Complete Kafka Event Flow

### Flow 1: User Registration

```
Client → auth-service (POST /auth/register)
  → DB: INSERT users, INSERT email_verification_tokens
  → @TransactionalAfterCommit: kafkaTemplate.send("auth.account-created", AccountCreatedEvent)
    → notification-service (AccountCreatedEventConsumer)
      → EmailService.sendVerificationEmail() via Resend API
```

**Issues:** No dedup guard on AccountCreatedEventConsumer; no afterCommit in some paths; servlet thread blocked during Kafka send.

---

### Flow 2: Chat Message Send

```
Client → gateway (POST /api/v1/chat/messages)
  → gateway JwtAuthFilter: extracts JWT, sets X-User-Id
  → chat-service MessageCommandService.send()
    → Pipeline DAG execution (on async thread via CompletableFuture):
      1. ValidateMessageStep: input sanitization
      2. CheckBlockedPairStep: Feign → friendship-service (GET /friends/check-block)
      3. ResolveRoomStep: verify room exists, user is member
      4. GenerateSequenceStep: sets seq=0 (no-op)
      5. PersistMessageStep: @Transactional (NEW transaction on async thread)
         → messageRepository.save()
         → attachmentRepository.saveAll()
         → RedisRoomSequenceService.nextSeq() (Redis INCR)
      6. PersistMentionStep: saves @mentions
      7. PublishMessageSentEventStep: afterCommit → kafkaTemplate.send("chat.message-sent")
    → notification-service (MessageEventConsumer):
      → NotificationCommandService.createMessageNotification()
      → NotificationWebSocketPublisher → Redis pub/sub → realtime-edge delivery
    → realtime-edge (ChatEventKafkaConsumer):
      → ChatRealtimeDeliveryService.deliverToRoom()
      → For each session subscribed to roomId:
          → WS session.sendMessage() (synchronized)
```

**Critical issues on this path:**
- `CompletableFuture.runAsync()` breaks @Transactional isolation (KAFKA-06)
- `CheckBlockedPairStep` has inverted logic bug (false positives)
- `afterCommit` only on send step; edit/delete/react have none (KAFKA-07)
- WebSocket send is synchronized — head-of-line blocking (WS-06 from websocket-review)

---

### Flow 3: Friendship Event Flow

```
Client → gateway → friendship-service (POST /friends/request)
  → DB: INSERT friendship (status=PENDING)
  → kafkaTemplate.send("friendship.request-sent", FriendshipRequestedEvent)
    → realtime-edge (FriendshipKafkaEventConsumer):
      → [NO groupId — random group per restart] (KAFKA-01)
      → Deliver friend request notification via WebSocket
```

---

### Flow 4: Account Created → Email Verification

```
auth-service: registration complete, afterCommit fires
  → "auth.account-created" → notification-service
    → [No dedup guard]
    → EmailService.sendVerificationEmail()
      → Resend API HTTP call (blocking on Kafka consumer thread)
      → If Resend times out: KafkaListener retries the event
      → After 3 retries: message goes to auth.account-created.DLT
      → DLT never consumed → email silently not sent
```

---

### Flow 5: Realtime Notification Fan-out

```
chat-service publishes "chat.message-sent"
  → notification-service consumes (group: notification-service)
    → NotificationCommandService.createNotification() per mentioned/member user
    → For each target user:
        NotificationWebSocketPublisher.publish()
          → Redis PUBLISH notification:{userId}
            → realtime-edge subscribed via RedisEventListener
              → EdgeDeliveryHandoffPublisher.publish()
                → Thread.sleep(50ms) [BLOCKS Redis listener thread]
                → ChatRealtimeDeliveryService.deliverToUser()
```

**Issues:**
- Fan-out is O(N) Redis PUBLISH calls per message (N = room members)
- `Thread.sleep()` on Redis listener thread stalls ALL subsequent Redis pub/sub messages for 50ms
- Notification creation is unbounded — large rooms cause notification-service OOM

---

## 10. Summary Table

| ID | Severity | Category | One-line description |
|----|----------|----------|---------------------|
| KAFKA-01 | CRITICAL | Consumer | FriendshipKafkaEventConsumer has no groupId — random group per restart |
| KAFKA-05 | CRITICAL | Reliability | No transactional outbox — dual-write race condition |
| KAFKA-06 | CRITICAL | Transaction | PipelineExecutor.runAsync() breaks @Transactional |
| KAFKA-04 | CRITICAL | Producer | Fire-and-forget producer — acks=1, retries=0 |
| KAFKA-07 | HIGH | Correctness | Edit/delete/reaction events published before DB commit |
| KAFKA-08 | HIGH | Performance | afterCommit publisher blocks servlet thread |
| KAFKA-10 | HIGH | Reliability | DLQ topics exist but are never consumed |
| KAFKA-11 | HIGH | Reliability | Deserialization failures create poison pill loop |
| KAFKA-12 | HIGH | Evolution | No schema registry — breaking changes are silent |
| KAFKA-09 | MEDIUM | Dead code | MESSAGE_PINNED/UNPINNED listeners with no publisher |
| KAFKA-02 | MEDIUM | Scaling | Single consumer group — no per-type isolation |
| KAFKA-03 | MEDIUM | Replay | No replay mechanism for missed events |

# Service Integration Phase 1 Cleanup Plan

**Date:** 2026-05-12  
**Scope:** Backend services only (auth, user, chat, friendship, notification, presence, upload, gateway)  
**Excluded:** realtime-edge-service (out of scope, not in root settings.gradle)  
**Goal:** Clean integration blockers, remove legacy contract drift, prepare for single canonical Kafka and Redis contracts

---

## Executive Summary

The backend has **systemic contract drift** with **two incompatible Kafka contracts** running in parallel, **two Redis message models**, and **duplicate event publishers** causing Spring context ambiguity. Gateway routes don't match downstream controllers. Notification uses the wrong Kafka consumer group. The compile chain fails before all services can compile.

**Minimum viable fixes for this phase:**
1. Add missing Jackson JSR310 dependency to common-kafka → unblocks build
2. Remove duplicate/ambiguous chat publishers → clarify Spring wiring
3. Fix gateway route mappings → restore external API access
4. Fix notification Kafka consumer group → prevent event loss
5. Delete legacy Kafka contract wrapper classes OR migrate all services to new EventEnvelope contract

---

## Section 1: Identified Compile Blockers

### B1.1: Jackson Datatype JSR310 Missing

**File:** `chatappBE/common/common-kafka/build.gradle`

**Issue:** `EventEnvelopeKafkaSerializer` tries to register `JavaTimeModule` (from `com.fasterxml.jackson.datatype:jackson-datatype-jsr310`), but that dependency is not declared in common-kafka.

```java
// In EventEnvelopeKafkaSerializer.java
new ObjectMapper()
    .registerModule(new JavaTimeModule())  // <- Class not available
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
```

**Impact:** Build fails during common-kafka compilation.

**Fix:** Add to `chatappBE/common/common-kafka/build.gradle` dependencies:
```gradle
implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'
```

**Severity:** 🔴 CRITICAL - Blocks all service compilation

---

### B1.2: Duplicate Beans for Chat Event Publishers

**Files:**
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventPublisher.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatMessageEventPublisherAdapter.java`
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaReactionEventPublisher.java`

**Issue:** Multiple beans implement the same interfaces without explicit `@Qualifier` or mutual exclusion:

- **`IMessageEventPublisher`** has 2 implementations:
  1. `KafkaChatMessageEventPublisher` → uses old `KafkaEventPublisher` + `Topics.CHAT_MESSAGE_SENT` + `ChatMessageSentEvent`
  2. `ChatMessageEventPublisherAdapter` → uses new `KafkaEventProducer` + `EventEnvelope` + `ChatEventType`

- **`IReactionEventPublisher`** has 2 implementations:
  1. `KafkaReactionEventPublisher` → uses old contract
  2. `ChatMessageEventPublisherAdapter` → uses new contract (inline)

**Injection Point:** Ambiguous autowiring in domain layers:
```java
@Autowired
private IMessageEventPublisher messageEventPublisher;  // Which bean?

@Autowired
private IReactionEventPublisher reactionEventPublisher;  // Which bean?
```

**Impact:** Spring context will fail to autowire or pick an unintended implementation once compile issues are fixed.

**Severity:** 🔴 CRITICAL - Causes Spring wiring failures

---

## Section 2: Gateway Route Mapping Issues

### R2.1: Friendship Route Mismatch

**Gateway Config File:** `chatappBE/gateway-service/src/main/resources/application.yaml`

**Current Route:**
```yaml
- id: friendship-service
  uri: http://${FRIENDSHIP_SERVICE_HOST:localhost}:${FRIENDSHIP_SERVICE_PORT:8085}
  predicates:
    - Path=/api/v1/friendship/**,/api/friendship/**
  filters:
    - RewritePath=/api(?:/v1)?/friendship/?(?<segment>.*), /api/v1/${segment}
```

**Service Controller Paths:**
- `FriendController`: `@RequestMapping("/api/v1/friends")` ← plural `friends`
- `InternalFriendController`: `@RequestMapping("/api/v1/internal/friends")`

**Mismatch:** Gateway sends `/api/v1/friendship/foo` → rewritten to `/api/v1/foo` → controller expects `/api/v1/friends/foo`

**Current Behavior:** External friendship API is not accessible through gateway; requests return 404.

**Fix:** Update gateway route:
```yaml
- id: friendship-service
  uri: http://${FRIENDSHIP_SERVICE_HOST:localhost}:${FRIENDSHIP_SERVICE_PORT:8085}
  predicates:
    - Path=/api/v1/friends/**,/api/friends/**
  filters:
    - RewritePath=/api(?:/v1)?/friends/?(?<segment>.*), /api/v1/friends/${segment}
```

**Severity:** 🔴 CRITICAL - Breaks external friendship API access

---

### R2.2: Upload Route Mismatch

**Gateway Config File:** `chatappBE/gateway-service/src/main/resources/application.yaml`

**Current Route:**
```yaml
- id: upload-service
  uri: http://${UPLOAD_SERVICE_HOST:localhost}:${UPLOAD_SERVICE_PORT:8088}
  predicates:
    - Path=/api/v1/upload/**,/api/upload/**
  filters:
    - RewritePath=/api(?:/v1)?/upload/?(?<segment>.*), /api/v1/${segment}
```

**Service Controller Path:**
- `UploadController`: `@RequestMapping("/api/v1/uploads")` ← plural `uploads`

**Mismatch:** Gateway sends `/api/v1/upload/prepare` → rewritten to `/api/v1/prepare` → controller expects `/api/v1/uploads/prepare`

**Current Behavior:** Upload API is not accessible through gateway; requests return 404.

**Fix:** Update gateway route:
```yaml
- id: upload-service
  uri: http://${UPLOAD_SERVICE_HOST:localhost}:${UPLOAD_SERVICE_PORT:8088}
  predicates:
    - Path=/api/v1/uploads/**,/api/uploads/**
  filters:
    - RewritePath=/api(?:/v1)?/uploads/?(?<segment>.*), /api/v1/uploads/${segment}
```

**Severity:** 🔴 CRITICAL - Breaks external upload API access

---

## Section 3: Kafka Contract Drift Analysis

### Current State: Two Incompatible Contracts

#### Contract A: Legacy/Old (Currently in use by most services)

**Interface:** `com.example.common.kafka.api.KafkaEventPublisher`
```java
void publish(String topic, String key, KafkaEvent event);
```

**Wrapper Classes:** Located in `common-kafka/src/main/java/com/example/common/kafka/event/`:
- `AccountCreatedEvent extends AbstractKafkaEvent`
- `ChatMessageSentEvent extends AbstractKafkaEvent`
- `ChatMessageEditedEvent extends AbstractKafkaEvent`
- `ChatMessageDeletedEvent extends AbstractKafkaEvent`
- `ChatReactionUpdatedEvent extends AbstractKafkaEvent`
- `FriendshipEvent extends AbstractKafkaEvent` (references non-existent `com.example.common.kafka.event.FriendshipEvent`)
- `FriendRequestKafkaEvent extends AbstractKafkaEvent` (references non-existent)
- `NotificationRequestedEvent extends AbstractKafkaEvent`

**Topic Constants:** `com.example.common.kafka.topic.Topics`
```java
public static final String ACCOUNT_CREATED = "account.account.created";
public static final String CHAT_MESSAGE_SENT = "chat.message.sent";
public static final String CHAT_MESSAGE_EDITED = "chat.message.edited";
public static final String CHAT_MESSAGE_DELETED = "chat.message.deleted";
public static final String CHAT_REACTION_UPDATED = "chat.reaction.updated";
public static final String FRIENDSHIP_EVENTS = "friendship.events";
public static final String FRIENDSHIP_REQUEST_EVENTS = "friendship.request.events";
public static final String NOTIFICATION_REQUESTED = "notification.requested";
```

**Services Using This Contract:**
- `auth-service`: `AccountCreatedEventProducer` → publishes via `KafkaEventPublisher`
- `user-service`: `AccountCreatedConsumer` → listens for `AccountCreatedEvent`
- `chat-service`: `KafkaChatMessageEventPublisher`, `KafkaReactionEventPublisher` → publish via `KafkaEventPublisher`
- `chat-service`: `KafkaChatMessageEventConsumer`, `KafkaReactionEventConsumer` → listen for wrapper events
- `friendship-service`: `FriendshipEventProducer` → publishes via `KafkaEventPublisher`
- `notification-service`: Multiple consumers listening for wrapper events

#### Contract B: New/Canonical (Partially in use in ChatMessageEventPublisherAdapter)

**Interface:** `com.example.common.kafka.producer.KafkaEventProducer`
```java
void send(String topic, String key, EventEnvelope<?> envelope);
```

**Event Model:** `com.example.common.event.EventEnvelope<T>`
- Wraps `EventMetadata` (routing info) + generic `T` payload
- Payloads are POJOs from `common-events`: `ChatMessagePayload`, `AccountCreatedPayload`, etc.

**Event Type Enums:** Located in `common-events/src/main/java/com/example/common/integration/`:
- `AccountEventType.ACCOUNT_CREATED.value()` → `"account.account.created"`
- `ChatEventType.MESSAGE_SENT.value()` → `"chat.message.sent"`
- `ChatEventType.MESSAGE_UPDATED.value()` → `"chat.message.updated"`
- `ChatEventType.MESSAGE_DELETED.value()` → `"chat.message.deleted"`
- `ChatEventType.REACTION_UPDATED.value()` → `"chat.reaction.updated"`
- `FriendshipEventType.*`
- `NotificationEventType.*`

**Services Using This Contract:**
- `chat-service`: `ChatMessageEventPublisherAdapter` → publishes via `KafkaEventProducer` + `EventEnvelope`

### The Problem

1. **Two APIs, one per service decision:**
   - Chat can't decide: has adapter using both old and new simultaneously
   - Auth/user/friendship all use old contract
   - Notification consumes both (legacy listeners + envelope parsing)

2. **Spring Context Ambiguity:**
   - Chat injects `IMessageEventPublisher` without `@Qualifier`
   - Two beans satisfy the contract
   - Spring picks one arbitrarily or fails

3. **No Compile Error (yet):**
   - Wrapper event classes DO exist in `common-kafka/src/main/java/com/example/common/kafka/event/`
   - Topic constants DO exist in `Topics.java`
   - Services compile, but behavior is undefined

---

## Section 4: Redis Contract Drift Analysis

### Current State: Old vs. New Message Models

#### Redis Contract A: Legacy/Old (In use by presence-service)

**Subscriber Interface:** `RedisEventSubscriber<T>`
```java
void onMessage(T message);  // T = RedisMessage<Payload>
```

**Message Model:** `RedisMessage<T>`
- Generic wrapper around payload

**Presence Subscribers Using Old Model:**
- `UserTypingSubscriber` implements `RedisEventSubscriber<RedisMessage<PresenceTypingPayload>>`
- `UserStopTypingSubscriber` implements `RedisEventSubscriber<RedisMessage<PresenceStopTypingPayload>>`
- `UserStatusChangedSubscriber` implements `RedisEventSubscriber<RedisMessage<PresenceUserStatePayload>>`
- `UserOnlineSubscriber` implements `RedisEventSubscriber<RedisMessage<PresenceUserOnlinePayload>>`
- `UserOfflineSubscriber` implements `RedisEventSubscriber<RedisMessage<PresenceUserOfflinePayload>>`
- `RoomOnlineUsersSubscriber` implements `RedisEventSubscriber<RedisMessage<RoomOnlineUsersPayload>>`
- `RoomLeaveSubscriber` implements `RedisEventSubscriber<RedisMessage<PresenceRoomLeavePayload>>`

#### Redis Contract B: New (Available but not in use)

**Publisher Interface:** `RedisEventPublisher`
- Available in `common-redis` but not actively used by services
- Expected to use `EventEnvelope`-style semantics

### The Problem

**Presence service publishes/subscribes using old `RedisMessage` model, but common-redis may provide new publisher contract. Not immediately a compile blocker but a consistency/runtime issue.**

---

## Section 5: Kafka Consumer Group Issues

### I5.1: Notification Service Consumer Group Misconfiguration

**File:** `chatappBE/notification-service/src/main/resources/application.yaml`

**Current Config:**
```yaml
spring:
  kafka:
    consumer:
      group-id: user-service  # ← WRONG: should be notification-service
      auto-offset-reset: earliest
```

**Impact:**
- Notification service consumers join the `user-service` Kafka consumer group
- `AccountCreatedEventConsumer` in notification-service may compete with `AccountCreatedConsumer` in user-service for the same partition
- Leads to race condition where either user profile or welcome notification might not be created

**Fix:** Change to:
```yaml
spring:
  kafka:
    consumer:
      group-id: notification-service
      auto-offset-reset: earliest
```

**Severity:** 🔴 CRITICAL - Causes event loss/duplication across services

---

## Section 6: Files to Delete (Legacy/Unused)

These classes exist but are either unused or replaced by newer implementations:

### Legacy Kafka Event Wrappers (if migrating to EventEnvelope exclusively)

If decision is to **use only EventEnvelope + event type enums** and delete old wrappers:

- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/event/AccountCreatedEvent.java`
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/event/ChatMessageSentEvent.java`
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/event/ChatMessageEditedEvent.java`
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/event/ChatMessageDeletedEvent.java`
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/event/ChatReactionUpdatedEvent.java`
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/event/FriendshipEvent.java` (if exists)
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/event/FriendRequestKafkaEvent.java` (if exists)
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/event/NotificationRequestedEvent.java`
- `chatappBE/common/common-kafka/src/main/java/com/example/common/kafka/event/AbstractKafkaEvent.java`

**OR, if keeping old wrappers for backward compatibility:**
- Delete the new `ChatMessageEventPublisherAdapter` (uses both old and new contracts, causing ambiguity)
- Keep `KafkaChatMessageEventPublisher` (uses only old contract, simple)

### Duplicate Publishers in Chat Service

**Delete from `chat-service`:**
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatMessageEventPublisherAdapter.java` ← Does both old and new contracts, ambiguous

**Keep in `chat-service`:**
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventPublisher.java` ← Uses old contract only, clear
- `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaReactionEventPublisher.java` ← Uses old contract only, clear

---

## Section 7: Files to Migrate (Update to canonical contract)

### Option A: Migrate All Services to New Contract (Recommended Long-Term)

If the decision is to **use EventEnvelope + event type enums** as the single canonical model:

**Update these files to use `KafkaEventProducer` + `EventEnvelope`:**

1. **Auth Service:**
   - `chatappBE/auth-service/src/main/java/com/example/auth/kafka/AccountCreatedEventProducer.java`
     - Change: Inject `KafkaEventProducer` instead of `KafkaEventPublisher`
     - Change: Use `EventEnvelope<AccountCreatedPayload>` instead of `AccountCreatedEvent`
     - Change: Use `AccountEventType.ACCOUNT_CREATED.value()` as topic

2. **User Service:**
   - `chatappBE/user-service/src/main/java/com/example/user/kafka/AccountCreatedConsumer.java`
     - Change: Listen for `EventEnvelope<AccountCreatedPayload>`
     - Change: Extract payload from `envelope.payload()`
   - `chatappBE/user-service/src/main/java/com/example/user/kafka/KafkaConsumerConfig.java`
     - Update deserializer config to handle `EventEnvelope`

3. **Chat Service:**
   - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventPublisher.java`
     - Change: Inject `KafkaEventProducer` instead of `KafkaEventPublisher`
     - Change: Use `EventEnvelope<ChatMessagePayload>` instead of `ChatMessageSentEvent`
     - Change: Use `ChatEventType.MESSAGE_SENT.value()` as topic
   - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaReactionEventPublisher.java`
     - Similar migration to use `KafkaEventProducer` + `EventEnvelope`
   - `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/kafka/KafkaChatMessageEventConsumer.java`
     - Change: Listen for `EventEnvelope<ChatMessagePayload>`, etc.

4. **Friendship Service:**
   - `chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventProducer.java`
     - Change: Inject `KafkaEventProducer`
     - Change: Use `EventEnvelope<FriendshipPayload>` instead of `FriendshipEvent`
     - Change: Use `FriendshipEventType.CREATED.value()`, etc. as topics

5. **Notification Service:**
   - `chatappBE/notification-service/src/main/java/com/example/notification/kafka/AccountCreatedEventConsumer.java`
     - Change: Listen for `EventEnvelope<AccountCreatedPayload>`
   - `chatappBE/notification-service/src/main/java/com/example/notification/kafka/MessageCreatedEventConsumer.java`
     - Change: Listen for `EventEnvelope<ChatMessagePayload>`
   - `chatappBE/notification-service/src/main/java/com/example/notification/kafka/ChatMessageEventConsumer.java`
     - Remove if just logging

### Option B: Keep Old Contract, Remove Duplicate (Intermediate)

If the decision is to **keep the old wrapper event model** for now:

**Delete (not migrate):**
- Delete `ChatMessageEventPublisherAdapter` from chat-service
- Delete any half-migrated code using `KafkaEventProducer`

**Keep as-is:**
- All old wrapper event classes
- `KafkaEventPublisher` interface and implementations

---

## Section 8: Ordered Refactor Plan

### Phase 1: Unblock Compilation (IMMEDIATE)

**Order (sequential, each fixes dependencies for next):**

1. **Fix Jackson Dependency**
   - File: `chatappBE/common/common-kafka/build.gradle`
   - Action: Add `implementation 'com.fasterxml.jackson.datatype:jackson-datatype-jsr310'`
   - Expected Outcome: common-kafka compiles

2. **Fix Notification Consumer Group**
   - File: `chatappBE/notification-service/src/main/resources/application.yaml`
   - Action: Change `group-id: user-service` → `group-id: notification-service`
   - Expected Outcome: No functional change yet, but correct for future

3. **Delete Duplicate Chat Publisher (or choose one)**
   - Option A: Delete `ChatMessageEventPublisherAdapter`
     - File: `chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatMessageEventPublisherAdapter.java`
     - Reason: Implements both `IMessageEventPublisher` AND `IReactionEventPublisher`, uses both old and new contracts
     - Keep: `KafkaChatMessageEventPublisher` + `KafkaReactionEventPublisher`
   - Option B: Delete `KafkaChatMessageEventPublisher` and `KafkaReactionEventPublisher`
     - Keep: `ChatMessageEventPublisherAdapter` but refactor to use only one contract
   - **Recommendation:** Option A (delete adapter, keep single-contract publishers)
   - Expected Outcome: No ambiguous beans, Spring context resolves cleanly

4. **Fix Gateway Friendship Route**
   - File: `chatappBE/gateway-service/src/main/resources/application.yaml`
   - Action: Change predicates from `/api/v1/friendship/**` to `/api/v1/friends/**`
   - Action: Update rewrite to `/api/v1/friends/${segment}`
   - Expected Outcome: Friendship API accessible via gateway

5. **Fix Gateway Upload Route**
   - File: `chatappBE/gateway-service/src/main/resources/application.yaml`
   - Action: Change predicates from `/api/v1/upload/**` to `/api/v1/uploads/**`
   - Action: Update rewrite to `/api/v1/uploads/${segment}`
   - Expected Outcome: Upload API accessible via gateway

6. **Run Compile Check**
   ```bash
   .\gradlew.bat --continue \
     :common:common-kafka:compileJava \
     :common:common-events:compileJava \
     :auth-service:compileJava \
     :user-service:compileJava \
     :chat-service:compileJava \
     :friendship-service:compileJava \
     :notification-service:compileJava \
     :presence-service:compileJava \
     :upload-service:compileJava \
     :gateway-service:compileJava
   ```
   - Expected Outcome: All services compile successfully

---

### Phase 2: Kafka Contract Consolidation (POST-COMPILATION)

**Recommended Decision:** Use new `EventEnvelope` contract exclusively, delete old wrappers

**Order (services that don't depend on others first):**

1. **Migrate auth-service** (no dependencies)
   - Update `AccountCreatedEventProducer` to use `KafkaEventProducer` + `EventEnvelope`

2. **Migrate user-service** (depends on auth)
   - Update `AccountCreatedConsumer` and config to listen for `EventEnvelope`

3. **Migrate chat-service** (no Kafka dependencies)
   - Update `KafkaChatMessageEventPublisher` and `KafkaReactionEventPublisher` to use new contract
   - Update consumers accordingly

4. **Migrate friendship-service** (no dependencies)
   - Update `FriendshipEventProducer` to use new contract

5. **Migrate notification-service** (depends on all)
   - Update all event consumers

6. **Delete Old Contract** (after all services migrated)
   - Delete all `AbstractKafkaEvent` subclasses
   - Delete legacy `KafkaEventPublisher` interface and implementations
   - Delete old `Topics.java` (replace with event type enums)
   - Keep only `KafkaEventProducer` interface

---

### Phase 3: Redis Contract Consolidation (POST-COMPILATION)

**Goal:** Align presence-service to use canonical Redis envelope model

1. **Update Presence Service Subscribers**
   - Migrate from `RedisMessage` to `EventEnvelope` model
   - Update subscriber interface and implementations

2. **Verify Chat-Presence Integration**
   - Ensure chat and presence use same Redis contract for fanout

---

## Section 9: Quick Reference - Delete Now List

**These can be deleted immediately (blocking/ambiguous):**

```
chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatMessageEventPublisherAdapter.java
```

**Reason:** Implements two interfaces, uses two conflicting contracts, causes Spring ambiguity. Choose either this OR the two Kafka publishers, not both.

---

## Section 10: Quick Reference - Fix Now List

**Fix before attempting Phase 2 migrations:**

| File | Change | Severity |
|------|--------|----------|
| `chatappBE/common/common-kafka/build.gradle` | Add JSR310 dependency | 🔴 CRITICAL |
| `chatappBE/gateway-service/src/main/resources/application.yaml` | Fix friendship route | 🔴 CRITICAL |
| `chatappBE/gateway-service/src/main/resources/application.yaml` | Fix upload route | 🔴 CRITICAL |
| `chatappBE/notification-service/src/main/resources/application.yaml` | Fix consumer group | 🔴 CRITICAL |
| `chatappBE/chat-service/...` | Delete ambiguous adapter | 🔴 CRITICAL |

---

## Section 11: Testing Strategy Post-Cleanup

After completing Phase 1, verify:

1. **Compilation:**
   ```bash
   .\gradlew.bat clean build -x test
   ```

2. **Root Build:**
   ```bash
   .\gradlew.bat -pl . clean build
   ```

3. **Service-Level Tests:**
   ```bash
   .\gradlew.bat :auth-service:test
   .\gradlew.bat :user-service:test
   .\gradlew.bat :chat-service:test
   .\gradlew.bat :friendship-service:test
   .\gradlew.bat :notification-service:test
   .\gradlew.bat :presence-service:test
   .\gradlew.bat :upload-service:test
   .\gradlew.bat :gateway-service:test
   ```

4. **Integration Boot:**
   ```bash
   docker-compose -f chatappBE/docker-compose.local.yml up -d
   # Verify all services start without errors
   ```

---

## Section 12: Post-Phase 1 Blockers Checklist

- [ ] Jackson JSR310 added to common-kafka
- [ ] Notification consumer group fixed
- [ ] Ambiguous chat publisher deleted
- [ ] Friendship gateway route fixed
- [ ] Upload gateway route fixed
- [ ] All services compile (`gradlew clean build -x test` passes)
- [ ] Gateway readiness checks pass
- [ ] No Spring context ambiguity warnings
- [ ] Docker-compose topology boots cleanly

---

## Appendix A: Service Compile Status After Phase 1

| Service | Status | Reason |
|---------|--------|--------|
| auth-service | ✅ Compiles | No changes needed for Phase 1 |
| user-service | ✅ Compiles | No changes needed for Phase 1 |
| chat-service | ✅ Compiles | Deleted ambiguous adapter clarifies wiring |
| friendship-service | ✅ Compiles | No changes needed for Phase 1 |
| notification-service | ✅ Compiles | Config change only (group-id) |
| presence-service | ✅ Compiles | No Kafka/Redis changes in Phase 1 |
| upload-service | ✅ Compiles | No changes needed for Phase 1 |
| gateway-service | ✅ Compiles | Route config doesn't affect compilation |

---

## Appendix B: Known Limitations Not Addressed in Phase 1

These are intentionally deferred to future phases:

1. **Redis Contract Migration** - Present-service still uses old `RedisMessage` model; migration deferred
2. **Upload Metadata Flow** - Chat attachments/room avatars not fully integrated with upload-service; deferred
3. **Realtime Edge Service** - Excluded from scope per requirements; not addressedInter-service contract tests - deferred
4. **Event versioning** - Not addressed in this phase

---

## Appendix C: Container & Settings Context

**Root Gradle Settings** (`chatappBE/settings.gradle`):
- ✅ Correctly includes 8 running services
- ✅ Correctly EXCLUDES `realtime-edge-service`
- ✅ Includes all common modules

**Docker Compose** (`chatappBE/docker-compose.yml`):
- Services wired for 8 running services
- Kafka, Redis, databases configured
- All services should boot after Phase 1 fixes

---

**END OF PHASE 1 CLEANUP PLAN**

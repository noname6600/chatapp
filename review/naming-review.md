# Naming Review — chatappBE
> Deep-dive audit | Date: 2026-05-20 | Reviewer: multi-agent analysis

---

## 1. Class Naming

### NAME-01 — HIGH | `I` Prefix for Interfaces Applied Inconsistently

| Interface | Used in | Follows convention? |
|-----------|---------|-------------------|
| `ITimeRedisCache` | common-redis-cache | Yes (I prefix) |
| `RealtimeSessionRegistry` | realtime-edge | No I prefix |
| `PipelineStep` | chat-service | No I prefix |
| `MessageSequenceService` | chat-service | No I prefix |
| `RedisEventRegistry` | common-redis | No I prefix |
| `FriendCommandService` | friendship-service | No I prefix — is it an interface? |

The `I` prefix convention (Hungarian notation) is not part of Java naming conventions (Google Java Style, Oracle conventions) and is not applied consistently. In realtime-edge, interfaces have no prefix and implementations are named `RedisRealtimeSessionRegistry` (implementation name includes the adapter detail — correct in hexagonal pattern).

**Fix:** Remove `I` prefix from all interfaces. Use clear noun-based names for interfaces (`TimeRedisCache` not `ITimeRedisCache`). Name implementations with the adapter/strategy qualifier.

---

### NAME-02 — MEDIUM | `Friend` vs `Friendship` Prefix Inconsistency

| Class | Package | Prefix used |
|-------|---------|-------------|
| `FriendController` | friendship-service | `Friend` |
| `FriendCommandService` | friendship-service | `Friend` |
| `FriendQueryService` | friendship-service | `Friend` |
| `Friendship` | friendship-service entity | `Friendship` |
| `FriendshipRepository` | friendship-service | `Friendship` |
| `FriendshipKafkaEventConsumer` | realtime-edge | `Friendship` |
| `FriendshipRequestedEvent` | common-events | `Friendship` |
| `FriendshipEventDedupeGuard` | realtime-edge | `Friendship` |

Controllers and services use `Friend*` while entities, repositories, events, and Kafka consumers use `Friendship*`. These refer to the same domain concept.

**Recommendation:** Standardize on `Friendship` throughout (more precise — a `Friend` and a `Friendship` are semantically different concepts; the entity is the relationship, not the person).

---

### NAME-03 — MEDIUM | `configuration` vs `config` Directory Naming

(Also covered in PKG-01 from package-structure-review.)

Within classes, configuration classes are named `*Config` in some services and `*Configuration` in others:

| Class | Service |
|-------|---------|
| `SecurityConfig` | gateway, realtime-edge |
| `SecurityConfiguration` | auth-service, user-service |
| `SwaggerConfig` | auth-service |
| `KafkaConfiguration` | auth-service, chat-service |
| `WebSocketConfig` | realtime-edge |
| `TimeConfig` | auth-service |

**Fix:** Standardize on `*Config` (shorter, widely used in Spring Boot community). Rename `*Configuration` classes to `*Config`.

---

### NAME-04 — LOW | `DatabaseSchemaFixer` Is a Misleading Name

**Affected:** `auth-service/.../configuration/DatabaseSchemaFixer.java`, `notification-service/.../configuration/DatabaseSchemaFixer.java`

The name implies it "fixes" a broken schema, which is a production incident workaround disguised as a configuration class. The name:
- Does not describe what schema change it makes
- Does not encode the version or context of the fix
- Will remain in the codebase long after the underlying issue is irrelevant

**Fix:** Replace with proper Flyway migrations (e.g., `V3__add_missing_index_to_users.sql`). Delete the class.

---

### NAME-05 — LOW | Root Gradle Project Named `demo`

`rootProject.name = 'demo'` — leftover from Spring Initializr. Fix to `chatapp-be`.

---

## 2. Method Naming

### NAME-06 — MEDIUM | Kafka Magic Strings Instead of Constants in Event Routing

**Affected file:** `notification-service/src/main/java/com/chatweb/notification/infrastructure/kafka/MessageMutationEventConsumer.java`

```java
switch (event.getEventType()) {
    case "MESSAGE_EDITED" -> handleEdited(event);
    case "MESSAGE_DELETED" -> handleDeleted(event);
    case "MESSAGE_REACTION_ADDED" -> handleReactionAdded(event);
    case "MESSAGE_REACTION_REMOVED" -> handleReactionRemoved(event);
}
```

String literals for event type routing instead of constants/enums. If the event type string changes in chat-service, this switch silently stops routing — no compile-time check.

**Fix:** Use a shared enum `MessageMutationEventType { MESSAGE_EDITED, MESSAGE_DELETED, ... }` defined in common-events. Or use separate Kafka topics per event type (eliminating the need for a type discriminator entirely).

---

### NAME-07 — MEDIUM | `checkBlockStatus` vs `isBlocked` vs `checkBlock`

Across the codebase, checking block status uses different method names:
- `FriendshipClient.checkBlockStatus(senderId, receiverId)` — Feign client
- `checkBlockedPair()` in pipeline step name
- `GET /friends/check-block` — endpoint path

No consistent verb chosen for "does a block relationship exist?". The boolean query should be named `isBlocked()` consistently.

---

### NAME-08 — LOW | `nextSeq()` Should Be `nextSequenceNumber()`

```java
public long nextSeq(UUID roomId)
```

`seq` is an abbreviation. In a domain context, full names communicate intent more clearly. `nextSequenceNumber(roomId)` or `allocateSequence(roomId)`.

---

## 3. Event and Topic Naming

### NAME-09 — GOOD | Kafka Topic Naming Convention Is Consistent

Topics use `{service-domain}.{event-name}` in lowercase dot-separated format:
- `auth.account-created` ✅
- `chat.message-sent` ✅
- `chat.message-edited` ✅
- `friendship.request-sent` ✅

This follows the Kafka community convention. No issues.

---

### NAME-10 — MEDIUM | Event Class Names Don't Match Topic Names

| Kafka Topic | Event Class | Mismatch |
|-------------|-------------|---------|
| `auth.account-created` | `AccountCreatedEvent` | Consistent ✅ |
| `chat.message-sent` | `ChatMessageSentEvent` | Prefix added: `Chat` |
| `chat.message-edited` | `MessageEditedEvent` | No `Chat` prefix |
| `chat.message-reaction-added` | `MessageReactionEvent` | No `Added` suffix |

Inconsistent event class naming makes it hard to find the event class for a given topic. `ChatMessageSentEvent` vs `MessageEditedEvent` in the same domain.

**Fix:** Standardize: `{Domain}{Entity}{EventVerb}Event`
- `ChatMessageSentEvent`
- `ChatMessageEditedEvent`
- `ChatMessageDeletedEvent`
- `ChatMessageReactionAddedEvent`
- `ChatMessageReactionRemovedEvent`

---

### NAME-11 — LOW | `TOPIC_SYSTEM_RETRY` Is a Misleading Dead Constant

```java
// KafkaTopics.java
public static final String TOPIC_SYSTEM_RETRY = "system.retry";
```

This constant is referenced nowhere — no producer, no consumer. The name implies a system-wide retry topic, which would be a significant piece of infrastructure. Its presence without any usage misleads developers into thinking retry infrastructure exists.

**Fix:** Delete the constant, or implement the retry consumer if intended.

---

## 4. DTO and Field Naming

### NAME-12 — MEDIUM | `FriendshipEventDedupeGuard` Is Misnamed

**Affected file:** `realtime-edge-service/.../FriendshipEventDedupeGuard.java` (or similar)

This class deduplicates events to prevent processing the same Kafka event twice. However, it appears to be unused (no injection point found in realtime-edge).

Even if it were used, the name `DedupeGuard` is non-standard. More conventional names:
- `DuplicateEventFilter`
- `IdempotentEventProcessor`
- `EventDeduplicator`

---

### NAME-13 — LOW | DTO Naming Is Consistent

DTO classes consistently use `*Request` for inbound and `*Response` for outbound:
- `LoginRequest`, `RegisterRequest`, `ChangePasswordRequest` ✅
- `AuthResponse`, `UserProfileResponse`, `FriendListResponse` ✅

This is correct and consistent. No issues.

---

## 5. Enum Naming

### NAME-14 — MEDIUM | Friendship Status Enum vs Payload Field Format Mismatch

**Affected:** `friendship-service/src/main/java/com/chatweb/friendship/entity/Friendship.java`

```java
public enum FriendshipStatus {
    PENDING, ACCEPTED, DECLINED, BLOCKED
}
```

The enum values are `UPPER_SNAKE_CASE` (correct for Java enums). However, if these values are serialized to JSON without `@JsonValue` customization, they appear in API responses as `"PENDING"`, `"ACCEPTED"`, etc.

The frontend may expect `"pending"`, `"accepted"` (lowercase) — common in REST APIs. If the frontend uses lowercase and the backend uses uppercase, the comparison fails silently.

**Fix:** Add `@JsonValue` and `@JsonCreator` with explicit string mappings, or annotate with `@JsonProperty` per value. Document the contract explicitly.

---

## 6. Configuration Property Naming

### NAME-15 — MEDIUM | Mixed Property Naming in application.yaml

Some properties use camelCase prefix, others use kebab-case:

```yaml
gateway:
  ratelimit:                        # kebab-case prefix
    defaultReplenishRate: 20        # camelCase suffix — inconsistent
    
realtime:
  session-registry:                 # kebab-case — consistent
    mode: redis
    
phaseb:                             # Non-descriptive prefix
  local:
    validation:
      enabled: false
```

`phaseb.local.validation.enabled` is cryptic. The `phaseb` prefix references an internal project phase name that will be meaningless to anyone reading the config in 6 months.

**Fix:** Rename to `app.auth.bypass-validation.enabled` (clearly describes what it does, explicitly dangerous-sounding).

---

## 7. Summary

| ID | Severity | Issue |
|----|----------|-------|
| NAME-06 | HIGH | Kafka magic strings for event type routing — no compile-time safety |
| NAME-01 | HIGH | `I` prefix for interfaces applied inconsistently |
| NAME-02 | MEDIUM | `Friend` vs `Friendship` prefix inconsistency in same domain |
| NAME-03 | MEDIUM | `*Config` vs `*Configuration` class naming split |
| NAME-07 | MEDIUM | `checkBlockStatus` / `isBlocked` / `checkBlock` — inconsistent verb |
| NAME-10 | MEDIUM | Event class names don't match topic names consistently |
| NAME-12 | MEDIUM | `FriendshipEventDedupeGuard` non-standard name and possibly unused |
| NAME-14 | MEDIUM | Enum serialization format not documented — possible frontend mismatch |
| NAME-15 | MEDIUM | `phaseb.*` property prefix is cryptic and project-phase-specific |
| NAME-04 | LOW | `DatabaseSchemaFixer` misleading name for production incident workaround |
| NAME-05 | LOW | Root Gradle project named `demo` |
| NAME-08 | LOW | `nextSeq()` should be `nextSequenceNumber()` |
| NAME-11 | LOW | `TOPIC_SYSTEM_RETRY` dead constant implies nonexistent infrastructure |
| NAME-13 | LOW | DTO naming (`*Request`/`*Response`) is consistent ✅ |

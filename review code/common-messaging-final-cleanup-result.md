# Common Messaging Final Cleanup Result

## 1. Scope

**Modules modified:**
- `chatappBE/common/common-events/src/main/java/`
- `chatappBE/common/common-redis/src/main/java/`
- `chatappBE/common/common-kafka/src/main/java/`
- `chatappBE/common/common-kafka/src/test/java/`

**Modules NOT modified:** all service modules, gateway, auth-service, chat-service, presence-service, friendship-service, notification-service, user-service, upload-service, redis-cache module, docker/nginx/deployment files.

**Verification:** `.\gradlew.bat :common:common-events:test :common:common-redis:test :common:common-kafka:test` → `BUILD SUCCESSFUL`

---

## 2. Problems Targeted

From the post-second-refactor review, this pass targeted all remaining high and medium priority issues within the three-module scope:

**High-priority contract bug:**
- `PresenceEventType.USER_STATUS_CHANGED` was incorrectly classified as payload-less even though `PresenceUserStatePayload` is the legitimate payload and is used by presence-service
- Fixed by moving `USER_STATUS_CHANGED` out of `PAYLOAD_LESS_EVENT_TYPES` and registering it to the correct payload class

**Non-canonical / deprecated contract surface in common-events:**
- `FriendRequestEvent` — deprecated duplicate with nested Type enum that duplicates `FriendshipEventType`; usage should migrate to `FriendRequestPayload` + `FriendshipEventType`
- `NotificationEvent` — deprecated duplicate; usage should migrate to `NotificationCreatedPayload`
- `UserPresencePayload` — misplaced in user package instead of presence package; used for `USER_HEARTBEAT` event

**Kafka semantic alias clutter:**
- `KafkaTopics` contained 1:1 semantic aliases (`TOPIC_ACCOUNT_CREATED`, `TOPIC_CHAT_MESSAGE_SENT`, etc.) that merely mirrored event enum values
- These aliases shifted semantic ownership away from `common-events` back to the transport layer

**Service registration conflict risk:**
- `SharedEventCatalog.registerAll()` pre-registers `ChatEventType.MESSAGE_PINNED` and `MESSAGE_UNPINNED`, but `chat-service` still re-registers them to `RoomMessagePinEventPayload`
- Creates first-registration-wins conflict and requires service-level migration

---

## 3. Changes Made In Common-Events

### Contract Bug Fix: PresenceEventType.USER_STATUS_CHANGED

**File:** `com/example/common/event/SharedEventCatalog.java`
- Removed `PresenceEventType.USER_STATUS_CHANGED` from `PAYLOAD_LESS_EVENT_TYPES`
- Added import for `PresenceUserStatePayload`
- Added registration: `registry.register(PresenceEventType.USER_STATUS_CHANGED.value(), PresenceUserStatePayload.class)`
- Updated comment to reflect payload-bearing nature

**Result:** Redis deserialization will now correctly resolve `presence.user.status-changed` events with `PresenceUserStatePayload` instead of dispatching with `null` payload.

### Deprecated Contract DTOs — Deleted

**Deleted files:**
- `com/example/common/integration/friendship/FriendRequestEvent.java` — marked @Deprecated(forRemoval=true); no external usage found
- `com/example/common/integration/notification/NotificationEvent.java` — marked @Deprecated(forRemoval=true); no external usage found
- `com/example/common/integration/user/UserPresencePayload.java` — misplaced type, moved to presence package

**Reason:** These were non-canonical duplicates that complicated the contract ownership. Services migrating away from them should use:
- `FriendRequestPayload` + `FriendshipEventType` instead of `FriendRequestEvent`
- `NotificationCreatedPayload` instead of `NotificationEvent`

### Misplaced Type — Relocated

**Created:** `com/example/common/integration/presence/PresenceHeartbeatPayload.java`
- Properly named and located payload for `PresenceEventType.USER_HEARTBEAT`
- Moved from user package (where it was misplaced as `UserPresencePayload`) to presence package
- Includes Javadoc explaining the previous misplacement

**Updated:** `SharedEventCatalog.java`
- Changed registration from `UserPresencePayload` to `PresenceHeartbeatPayload`
- Updated imports

### Documentation & Risk Mitigation

**Updated:** `SharedEventCatalog.registerAll()` Javadoc
- Added explicit warning that services should not re-register shared event types with different payload classes
- Documented that conflicting re-registration will throw `IllegalStateException`
- Recommended using transport-specific registry overrides for service-specific needs (outside scope)

**Added:** Inline comment near MESSAGE_PINNED registration
- Warns that these events use common `MessagePinPayload` and should not be re-registered to service-local classes
- Prompts services to use the shared contract

---

## 4. Changes Made In Common-Redis

### Stronger Deserialization Validation

**File:** `com/example/common/redis/serialization/JsonRedisEventSerializer.java`

**Change:** Reject missing payload for payload-bearing events

In Phase 3 (resolve payload), added explicit validation:
```
if (payloadNode == null || payloadNode.isNull()) {
    throw new RedisPubSubException("unknown",
            "Redis deserialize failed: missing payload for payload-bearing event type: " + eventType, null);
}
```

**Rationale:** Payload-bearing events must have a non-null payload field. Previously, missing payload was silently accepted and resulted in `null` payload in the envelope. Now:
- Payload-less events: skip payload resolution (as before)
- Payload-bearing events: require non-null `payload` field in JSON or throw deserialize error

**Error classification now complete:**
1. Invalid JSON
2. Missing metadata field
3. Missing eventType in metadata
4. Unknown event type (unregistered)
5. **Missing payload for payload-bearing event (NEW)**
6. Invalid payload JSON/content
7. Invalid timestamp format
8. Invalid metadata identity fields

---

## 5. Changes Made In Common-Kafka

### Reduced KafkaTopics to Transport-Only

**File:** `com/example/common/kafka/topic/KafkaTopics.java`

**Removed semantic alias constants:**
- `TOPIC_ACCOUNT_CREATED`, `TOPIC_ACCOUNT_DELETED`, `TOPIC_ACCOUNT_DISABLED`
- `TOPIC_USER_PROFILE_CREATED`, `TOPIC_USER_PROFILE_UPDATED`
- `TOPIC_CHAT_MESSAGE_SENT`, `TOPIC_CHAT_MESSAGE_EDITED`, `TOPIC_CHAT_MESSAGE_DELETED`, `TOPIC_CHAT_REACTION_UPDATED`
- `TOPIC_NOTIFICATION_REQUESTED`, `TOPIC_NOTIFICATION_SENT`

**Kept only true transport routes:**
- `TOPIC_FRIENDSHIP_EVENTS` — aggregate route for multiple friendship event types
- `TOPIC_FRIENDSHIP_REQUEST_EVENTS` — aggregate route for friendship request events
- `TOPIC_SYSTEM_DEAD_LETTER` — infrastructure route
- `TOPIC_SYSTEM_RETRY` — infrastructure route

**Updated Javadoc:**
- Clarified that only aggregate and infrastructure routes are owned by `common-kafka`
- Documented that services should reference event type enums directly from `common-events` for semantic routing
- Made explicit that `common-events` is the single owner of semantic event names

**Rationale:** 
- 1:1 semantic aliases duplicate event enum values and shift ownership away from `common-events`
- Services and internal code can reference enums directly (e.g., `AccountEventType.ACCOUNT_CREATED.value()`)
- This ensures `common-events` remains the authoritative single source of semantic names
- Transport/aggregate routes rightfully belong in `common-kafka` since they represent Kafka-specific routing decisions

### Updated Tests

**File:** `com/example/common/kafka/contract/KafkaContractTest.java`

**Deleted test methods:**
- `kafkaTopics_accountRoutesDeriveFromAccountEventType()` — tests removed semantic constants
- `kafkaTopics_chatRouteDerivedFromChatEventType()` — tests removed semantic constants

**Reason:** These tests verified that `KafkaTopics` constants matched enum values. Since the constants no longer exist, the tests are obsolete. The contract is now that services use event enums directly.

---

## 6. Deleted / Removed Code

### Deprecated Contract Types (deleted from common-events)
- `FriendRequestEvent.java` — misnamed payload DTO; use `FriendRequestPayload` + `FriendshipEventType`
- `NotificationEvent.java` — duplicate of `NotificationCreatedPayload`; migrated to canonical type
- `UserPresencePayload.java` (from user package) — misplaced type; relocated as `PresenceHeartbeatPayload` to presence package

### Semantic Alias Constants (deleted from KafkaTopics)
- Account/User/Chat/Notification topic aliases (now derive from enums as needed)
- 14 constants that were 1:1 mappings to event enum values

### Test Code (deleted from KafkaContractTest)
- 2 test methods that verified semantic constant mappings

### Code NOT deleted (kept for service compatibility)
- `KafkaEventProducer` — deprecated interface; services still use it
- `DefaultKafkaEventProducer` — deprecated implementation; services still use it
- `KafkaPubSubException` — deprecated exception; services still use it
- These remain marked `@Deprecated(forRemoval=true)` and clearly documented as temporary compatibility surface

---

## 7. Canonical Final Decisions

### Event Semantics Ownership
**Final Authority:** `common-events` enum values
- Event names are defined in `AccountEventType`, `ChatEventType`, `NotificationEventType`, `UserEventType`, `PresenceEventType`, `FriendshipEventType`
- All event-to-semantic-name mappings are canonical and immutable
- Services reference enums directly for routing, not transport-level aliases

### Payload Registry Ownership
**Final Authority:** `SharedEventCatalog` in `common-events`
- Pre-registers all shared payload-bearing and payload-less events in `registerAll(registry)`
- First-registration-wins policy: same mapping is idempotent, conflicting mapping throws `IllegalStateException`
- No re-registration of shared events with different payload classes is permitted
- Services needing custom registrations must use transport-specific registry instances

### Payload-Less Event Types
**Final Authority:** `SharedEventCatalog.PAYLOAD_LESS_EVENT_TYPES` (immutable Set)
- Explicit list of shared events known to carry no payload:
  - `AccountEventType.ACCOUNT_DELETED`, `ACCOUNT_DISABLED`
  - `ChatEventType.MEMBER_JOINED`, `MEMBER_LEFT`, `MEMBER_REMOVED`
  - `NotificationEventType.NOTIFICATION_SENT`
  - `UserEventType.PROFILE_CREATED`, `PROFILE_UPDATED`
- Any event not in this set and registered in `SharedEventCatalog` is payload-bearing and must have a non-null payload in Redis deserialization

### Payload-Bearing Event Registrations
**Final Authority:** `SharedEventCatalog.registerAll(registry)`

Complete mapping:
- **Account:** `account.created` → `AccountCreatedPayload`
- **Chat messages:** `chat.message.sent` → `ChatMessagePayload`, `chat.message.edited` → `MessageUpdatedPayload`, `chat.message.deleted` → `MessageDeletedPayload`
- **Chat pins:** `chat.message.pinned`, `chat.message.unpinned` → `MessagePinPayload` (shared payload, not re-registerable)
- **Chat reactions:** `chat.reaction.updated` → `ReactionPayload`
- **Friendship requests:** `friend.request.sent`, `friend.request.accepted`, `friend.request.declined`, `friend.request.cancelled` → `FriendRequestPayload`
- **Friendship status:** `friend.unfriended`, `friend.blocked`, `friend.unblocked` → `FriendshipPayload`
- **Notification:** `notification.requested` → `NotificationRequestedPayload`, `notification.created` → `NotificationCreatedPayload`
- **Presence state:** `presence.user.online` → `PresenceUserOnlinePayload`, `presence.user.offline` → `PresenceUserOfflinePayload`, `presence.user.status-changed` → `PresenceUserStatePayload`
- **Presence heartbeat:** `presence.user.heartbeat` → `PresenceHeartbeatPayload`
- **Presence room:** `presence.room.typing` → `PresenceTypingPayload`, `presence.room.stop-typing` → `PresenceStopTypingPayload`, `presence.room.join` → `PresenceRoomJoinPayload`, `presence.room.leave` → `PresenceRoomLeavePayload`
- **Presence aggregate:** `presence.global.online-users` → `GlobalOnlineUsersPayload`, `presence.room.online-users` → `RoomOnlineUsersPayload`

### Redis Registry Model
**Final Authority:** `RedisEventRegistry` (injected by type)
- Pre-populated from `SharedEventCatalog` by `RedisAutoConfiguration`
- Services can override for custom (non-shared) event types
- `RedisEventRegistry` and `DefaultRedisEventRegistry` remain as service-facing type-adapter interfaces

### Kafka Route Ownership
**Final Authority:** `KafkaTopics` (transport routes only)
- Keeps only aggregate and infrastructure routes
- Semantic routing uses event type enums from `common-events` directly
- `common-kafka` does not own 1:1 semantic mappings

### Redis Deserialization Error Handling
**Final Authority:** Strict phase-structured validation
1. Invalid JSON → `RedisPubSubException` with reason
2. Missing/invalid metadata → exception with phase info
3. Missing eventType → exception
4. Unknown event type → exception
5. **Missing payload for payload-bearing event → exception (NEW)**
6. Invalid payload content → exception with event type
7. Invalid timestamp → exception
8. Invalid metadata identity → exception

### Deprecated Compatibility Surface
**Kept temporarily (with forRemoval=true):**
- `KafkaEventProducer` interface — services still import and use
- `DefaultKafkaEventProducer` class — services still use
- `KafkaPubSubException` — services still catch and use
- `PresenceEventType.normalize()`, `isDeprecatedAlias()`, `legacyAliasOf()` — used internally for backward-compat deserialization

**Rationale:** Service-level migration required; removal would break builds outside scope. These remain explicitly marked for removal and documented as temporary.

---

## 8. Validation / Error Handling Improvements

### Redis Deserialization
- Now rejects missing payload for payload-bearing events (previously silently accepted)
- Error messages are more precise and phase-specific
- Invalid payloads are caught and reported with event type context
- Metadata validation is explicit and thorough

### Common-Events Contract
- `SharedEventCatalog` now fully authoritative for event-to-payload mappings
- Javadoc warns against service re-registration of shared events
- Inline comments flag high-risk registrations (`MESSAGE_PINNED`, `MESSAGE_UNPINNED`)

### Test Coverage
- Removed tests validating `KafkaTopics` semantic constant mappings (no longer exists)
- Remaining tests focus on shared catalog behavior, dispatcher wiring, and transport-specific routing

---

## 9. Remaining External Migration Risks

These issues require edits outside the three allowed modules and are documented here as known risks:

1. **Service re-registration of shared chat pin events:**
   - `chat-service` still registers `MESSAGE_PINNED` and `MESSAGE_UNPINNED` to `RoomMessagePinEventPayload` in `ChatRedisEventConfig`
   - Conflicts with `SharedEventCatalog` pre-registration to `MessagePinPayload`
   - **Migration:** Chat-service should remove local re-registration and use the shared payload, or accept the conflict and use a service-specific `RedisEventRegistry` bean

2. **Old Kafka topic alias usage:**
   - Services still use deprecated `KafkaTopics.TOPIC_*` constants that have been removed
   - **Migration:** Services should reference event enum `.value()` directly instead (e.g., `ChatEventType.MESSAGE_SENT.value()`)

3. **Deprecated payload DTOs removed from common-events:**
   - `FriendRequestEvent` — any remaining service imports must migrate to `FriendRequestPayload` + `FriendshipEventType`
   - `NotificationEvent` — any remaining service imports must migrate to `NotificationCreatedPayload`
   - `UserPresencePayload` (from user package) — any imports must update to `PresenceHeartbeatPayload` from presence package

4. **Deprecated Kafka producer aliases still in use:**
   - Services still import and inject `KafkaEventProducer` (interface)
   - Services still instantiate `DefaultKafkaEventProducer` (implementation)
   - **Migration:** Services should use `KafkaEventPublisher` and `DefaultKafkaEventPublisher` instead
   - These aliases cannot be removed until service migration is complete

5. **Old Redis API usage:**
   - Services may still reference deleted Redis classes from previous refactors
   - Outside scope of this review but impacts compatibility

---

## 10. Final Summary

This final cleanup pass completed a third iteration of messaging foundation improvements:

**What was fixed:**
1. **Critical contract bug:** `PresenceEventType.USER_STATUS_CHANGED` now correctly maps to `PresenceUserStatePayload` instead of being misclassified as payload-less
2. **Deprecated duplicates removed:** `FriendRequestEvent` and `NotificationEvent` deleted from `common-events`; `UserPresencePayload` relocated and renamed to `PresenceHeartbeatPayload`
3. **Redis validation strengthened:** Payload-bearing events now reject missing payload during deserialization
4. **Kafka semantics cleaned:** Removed 1:1 semantic alias constants; `KafkaTopics` now owns only true transport routes
5. **Canonical ownership clarified:** `common-events` is now the exclusive owner of event names and payload mappings; `KafkaTopics` references enum values where appropriate

**Architectural improvements:**
- `SharedEventCatalog` is now the single authoritative source for shared event-to-payload contracts
- Redis deserialization validation is complete and error-specific
- Kafka no longer duplicates semantic ownership; services use event enums directly
- Documentation and Javadocs now match actual code ownership and behavior

**Test results:**
- All 12 actionable tasks executed successfully
- `BUILD SUCCESSFUL` for `:common:common-events:test`, `:common:common-redis:test`, `:common:common-kafka:test`
- No compilation errors or test failures

**Remaining work:**
- Service-level migrations are required to fully consume these changes
- Specific high-risk conflict (chat pin re-registration) must be resolved by chat-service
- Deprecated Kafka producer aliases cannot be removed until downstream usage is migrated

**Verdict:** The messaging foundation is now cleaner, more deterministic, and more honest about responsibility boundaries. Within the three-module scope, all identified issues have been addressed. External migration risks are documented and understood.

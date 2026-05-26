# Common Messaging Second Refactor Result

## 1. Scope
- Refactor pass applied only inside these modules:
  - `common-events`
  - `common-redis`
  - `common-kafka`
- No service/application module changes were made.

## 2. Problems Targeted
- No single canonical shared event-to-payload catalog in `common-events`
- Redis registry/bootstrap depended on per-service manual registration
- Redis deserialization errors were not strongly classified and validated end-to-end
- `RedisEventHandler` kept a fake `onEvent` compatibility shim
- `KafkaTopics` duplicated semantic event names instead of deriving from contracts
- Kafka dispatcher observability surface (`KafkaEventObserver`) was partially dead/unwired
- Contract tests did not fully cover new dispatcher-observer and shared catalog behavior

## 3. Changes Made In Common-Events
- Added `SharedEventCatalog` as canonical shared event contract registry:
  - `registerAll(EventPayloadRegistry registry)` registers payload-bearing shared events
  - `PAYLOAD_LESS_EVENT_TYPES` explicitly defines shared payload-less event types
- Updated `DefaultEventPayloadRegistry` registration semantics:
  - same-class re-registration is idempotent (accepted)
  - conflicting re-registration still throws `IllegalStateException`
- Corrected stale Javadocs:
  - `EventPayloadRegistry` now references shared catalog usage instead of removed legacy references
  - `EventContractValidator.isValidEventType` docs now match null-handling behavior
- Added missing shared event constant:
  - `NotificationEventType.NOTIFICATION_SENT("notification.sent")`
- Marked `PresenceEventType.normalize` as deprecated for removal, with safe suppression in `fromValue`

## 4. Changes Made In Common-Redis
- `RedisAutoConfiguration` now pre-populates registry from `SharedEventCatalog.registerAll(registry)`
- Clarified registry adapter intent in Javadocs (`RedisEventRegistry`, `DefaultRedisEventRegistry`)
- Removed fake backward-compat shim from `RedisEventHandler`:
  - removed deprecated `onEvent` default method
  - interface now keeps only `eventType()` and `handle(EventEnvelope<T>)`
- Strengthened publisher input validation:
  - `DefaultRedisEventPublisher.publish(...)` now rejects null/blank channels with `IllegalArgumentException`
- Reworked `JsonRedisEventSerializer`:
  - uses `EventPayloadRegistry` (transport-agnostic contract interface)
  - supports known payload-less event types via `SharedEventCatalog.PAYLOAD_LESS_EVENT_TYPES`
  - adds phase-structured deserialize errors (invalid JSON, invalid metadata/eventType, unknown event type, invalid payload JSON, invalid timestamp)
  - validates post-deserialization metadata identity constraints

## 5. Changes Made In Common-Kafka
- Rewrote `KafkaTopics` to derive 1:1 semantic topic values from `common-events` enums:
  - account/chat/notification/user event routes now point to enum `.value()`
  - aggregate/infrastructure routes remain explicit literals
- Wired dispatcher observability:
  - `KafkaEventDispatcher` now accepts `KafkaEventObserver`
  - emits `logDispatch(...)` on success
  - emits `logDispatchError(...)` on handler failure
  - keeps backward-compatible constructor for existing call sites
- Updated `KafkaAutoConfiguration` to inject observer into dispatcher bean
- Updated `KafkaContractTest`:
  - added dispatcher-observer success/error tests
  - added shared catalog behavior tests
  - added idempotent and conflicting registration contract tests for shared registry behavior
  - kept dispatcher routing and topic derivation coverage aligned with new model

## 6. Canonical Contract Decisions
- `common-events` is the source of truth for shared event semantics
- `SharedEventCatalog` is the canonical bootstrap catalog for shared payload contracts
- Redis/Kafka modules may expose transport routes, but semantic event naming must come from `common-events`
- Payload-less shared events are modeled explicitly via `PAYLOAD_LESS_EVENT_TYPES`, not implicit fallbacks
- Re-registration policy is deterministic:
  - same mapping: allowed
  - conflicting mapping: rejected

## 7. Removed / Renamed / Merged Code
- Removed deprecated `onEvent` shim from `RedisEventHandler`
- Removed duplicate semantic ownership in `KafkaTopics` by delegating to enum values from `common-events`
- Introduced `SharedEventCatalog` and centralized shared registration logic previously scattered by transport/service bootstraps

## 8. Validation / Error Handling Improvements
- Redis publish path now validates channel before routing/serialization
- Redis deserialize flow now provides precise failure classification and metadata validation
- Registry behavior now supports safe idempotent initialization patterns across auto-config + optional manual registration
- Dispatcher now reports both success and failure through observer surface, restoring intended observability contract

## 9. Remaining External Migration Risks
- Services that still rely on overriding `onEvent` in Redis handlers must implement `handle(...)` explicitly
- Service code that still assumes deprecated/removed old Kafka topic alias ownership must align to enum-derived values
- Service beans wired by old producer alias conventions may still require migration if relying on legacy naming assumptions
- Deprecated DTO cleanup in `common-events` could not be safely completed in this scope due to downstream service usage

## 10. Final Summary
- Second pass completed with structural cleanup across `common-events`, `common-redis`, and `common-kafka`.
- Shared contract ownership is now centralized, Redis validation is stricter and more explicit, and Kafka dispatch observability is fully wired.
- Contract tests for all three modules pass:
  - `:common:common-events:test`
  - `:common:common-redis:test`
  - `:common:common-kafka:test`

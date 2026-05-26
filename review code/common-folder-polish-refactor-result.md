# Common Folder Polish Refactor Result

## 1. Changed Files / Classes

### common-events

| File | Changes |
|------|---------|
| `SharedEventCatalog.java` | Removed Unicode decorative separators (`── ... ──────`); replaced with plain ASCII section comments. Fixed misindented Javadoc block for `validatePayloadContract` (opening `/**` and transport-neutral paragraph were at wrong column). Kept all behavior and public API unchanged. |
| `EventContractValidator.java` | `isRegisteredEventType(String)` now delegates to `SharedEventCatalog.isKnownEventType(eventType)` instead of directly accessing `PAYLOAD_BEARING_EVENT_TYPES.contains` and `PAYLOAD_LESS_EVENT_TYPES.contains`. Eliminates the second independent knowledge source that could drift. No logic change. |
| `SharedEventModelContractTest.java` | Removed four Unicode decorative section separators (`── ... ──────`); replaced with plain ASCII section comments. Fixed two test methods (`eventMetadata_rejectsNonConformingEventType`, `validator_validateMetadata_passesForValidMetadata`) that had `@Test` and method declarations indented at 8 spaces instead of 4. Fixed `EventMetadata` constructor call inside `eventEnvelope_serializesAndDeserializesRoundTrip` that mixed 4-space and 12-space alignment on the same parameter list. |

### common-kafka

| File | Changes |
|------|---------|
| `KafkaEventDispatcher.java` | Fixed constructor body: `this.unknownEventPolicy = ...` was indented at 12 spaces (inside the try/collect block level) — corrected to 8 spaces at the correct constructor body level, consistent with `this.observer = observer;`. Fixed Javadoc for `dispatch(EventEnvelope<?>)`: two lines had an extra leading space (`·* Behavior...`, `·* The default policy...`) — normalized to the standard `     *` column. |
| `Slf4jKafkaEventLogger.java` | Fixed broken Javadoc sentence: `"...consumer-side dispatch callbacks.\n * observability. Null-safe: ..."` was a double-sentence fragment. Replaced with: `"...consumer-side dispatch callbacks.\n * All fields are null-safe before access."` |
| `KafkaContractTest.java` | Normalized indentation of four over-indented test methods: `deserializer_rejectsNullBytes`, `deserializer_rejectsEmptyBytes`, `deserializer_rejectsMalformedJson`, `deserializer_rejectsNonCanonicalEventType` — these were indented with 12 spaces instead of 4 for `@Test` and method declarations, and their bodies were misaligned. Fixed assertion chain indentation in `deserializer_rejectsUnknownPayloadBearingEventType` (`.cause()` chain was at 12 spaces instead of 16). |

### common-redis

| File | Changes |
|------|---------|
| `RedisPubSubObserver.java` | Renamed all `EventEnvelope<?> message` parameters to `EventEnvelope<?> envelope` across the entire interface: `logPublish(String, EventEnvelope<?>)`, `logPublish(RedisEventRoutingContext, EventEnvelope<?>)`, `logReceive(String, EventEnvelope<?>)`, `logReceive(RedisEventRoutingContext, EventEnvelope<?>)`, `logError(String, EventEnvelope<?>, Throwable)`, `logError(RedisEventRoutingContext, EventEnvelope<?>, Throwable)`, `logDroppedNoSubscriber(RedisEventRoutingContext, EventEnvelope<?>)`, `logPublishSubscriberCount(RedisEventRoutingContext, EventEnvelope<?>, long)`. No behavior change. |
| `Slf4jRedisPubSubLogger.java` | Renamed `EventEnvelope<?> message` to `EventEnvelope<?> envelope` in `logDroppedNoSubscriber` and `logPublishSubscriberCount` override implementations and all local variable references (`message.metadata()` → `envelope.metadata()`). The `logPublish`, `logReceive`, and `logError` overrides already used `envelope`. No behavior change. |
| `RedisContractTest.java` | Renamed `EventEnvelope<?> message` to `EventEnvelope<?> envelope` in inline `RedisPubSubObserver` implementations: `logDroppedNoSubscriber` in `dispatcher_dropsValidCanonicalEventWithNoSubscriberAndObserverHook`, `dispatcher_rejectsNonCanonicalEventTypeBeforeDropLogic`, `dispatcher_rejectsWrongPayloadClassBeforeDropLogic`; `logPublishSubscriberCount` in `publisher_reportsSubscriberCount`. |

---

## 2. Deleted Files / Directories

None. No dead source files, no stale package-info files, and no empty `src/` directories were found inside any of the three target modules.

Build-output directories under `build/` contain empty Gradle-generated stubs — these are not source artifacts and were left untouched.

---

## 3. Renamed Parameters / Local Identifiers

| Module | Class | Old name | New name | Scope |
|--------|-------|----------|----------|-------|
| common-redis | `RedisPubSubObserver` | `message` | `envelope` | All method parameters typed `EventEnvelope<?>` |
| common-redis | `Slf4jRedisPubSubLogger` | `message` | `envelope` | `logDroppedNoSubscriber` and `logPublishSubscriberCount` override parameters + local variables |
| common-redis | `RedisContractTest` | `message` | `envelope` | Inline anonymous `RedisPubSubObserver` implementations in three tests |

---

## 4. Deprecated / Legacy Leftovers Removed

None found. A `@Deprecated` scan across all three modules returned zero hits. No compatibility wrapper classes, no legacy aliases, and no stale publish-wording in public APIs were found.

---

## 5. Comment / Javadoc Cleanup Summary

| Module | Location | Issue fixed |
|--------|----------|-------------|
| common-events | `SharedEventCatalog.java` | Removed Unicode `──` decorative separators from two section comments; replaced with plain `// Section name`. |
| common-events | `SharedEventCatalog.java` | Fixed misindented Javadoc block opening (`/**` at wrong column) and misindented `<p>This method is transport-neutral…` paragraph line (`·*` instead of `    *`). |
| common-events | `SharedEventModelContractTest.java` | Removed four Unicode `── ... ──────` section comment decorators; replaced with plain `// Section name`. |
| common-kafka | `KafkaEventDispatcher.java` | Normalized two Javadoc lines in `dispatch` that had one extra leading space. |
| common-kafka | `Slf4jKafkaEventLogger.java` | Fixed broken class-level Javadoc sentence (`"…dispatch callbacks.\n * observability. Null-safe: all fields are guarded before access."` → `"…dispatch callbacks.\n * All fields are null-safe before access."`). |

---

## 6. Formatting / Indentation Cleanup Summary

| Module | File | Issue fixed |
|--------|------|-------------|
| common-events | `EventContractValidator.java` | `isRegisteredEventType` now calls `SharedEventCatalog.isKnownEventType(...)` — eliminates the duplicated `PAYLOAD_BEARING_EVENT_TYPES.contains(...) \|\| PAYLOAD_LESS_EVENT_TYPES.contains(...)` expression. |
| common-events | `SharedEventModelContractTest.java` | Two `@Test` + method declaration pairs re-indented from 8-space to 4-space. Constructor argument alignment fixed in `eventEnvelope_serializesAndDeserializesRoundTrip`. |
| common-kafka | `KafkaEventDispatcher.java` | `this.unknownEventPolicy = ...` assignment re-indented from 12-space to 8-space inside constructor body. |
| common-kafka | `KafkaContractTest.java` | Four test methods (`deserializer_rejectsNullBytes`, `deserializer_rejectsEmptyBytes`, `deserializer_rejectsMalformedJson`, `deserializer_rejectsNonCanonicalEventType`) re-indented from 12-space to 4-space. Assertion chain in `deserializer_rejectsUnknownPayloadBearingEventType` normalized (`.cause()` aligned at 16 spaces). |

---

## 7. Validation Command Results

```
.\gradlew.bat :common:common-events:test :common:common-kafka:test :common:common-redis:test --rerun-tasks
BUILD SUCCESSFUL in 56s
12 actionable tasks: 12 executed
```

```
.\gradlew.bat :common:common-events:compileJava :common:common-kafka:compileJava :common:common-redis:compileJava
BUILD SUCCESSFUL in 5s
3 actionable tasks: 3 up-to-date
```

Test results (all passing, unchanged counts):

| Module | Test class | Tests | Failures | Errors | Skipped |
|--------|-----------|-------|----------|--------|---------|
| common-events | `SharedEventModelContractTest` | 30 | 0 | 0 | 0 |
| common-kafka | `KafkaContractTest` | 23 | 0 | 0 | 0 |
| common-redis | `RedisContractTest` | 35 | 0 | 0 | 0 |

---

## 8. Dead Code Confirmation

### common-events
- No `@Deprecated` types, methods, or fields.
- No unused classes or interfaces.
- No empty source packages or directories.
- No dead private helpers or unreachable code.
- No stale package-info files.
- No compatibility wrapper names or wording.

**Dead code remaining: none.**

### common-kafka
- No `@Deprecated` types, methods, or fields.
- No unused classes or interfaces.
- No empty source packages or directories.
- No dead private methods or unused constants.
- No stale compatibility wording in public APIs.

**Dead code remaining: none.**

### common-redis
- No `@Deprecated` types, methods, or fields.
- No unused classes or interfaces.
- No empty source packages or directories.
- No dead private helpers or unused imports introduced by this refactor.
- No `RedisMessage`, `redis.message`, or Redis registry wrapper residuals.

**Dead code remaining: none.**

---

## Hard Rule Confirmation

- No service modules were touched.
- No business logic or validation behavior was changed.
- No versioning work was performed.
- No compatibility wrappers were introduced.
- No deprecated code remains inside common-events, common-kafka, or common-redis.
- All frozen public API method signatures are identical to before this refactor.
- Only parameter names, comments, indentation, and one delegating refactor (`isRegisteredEventType`) were changed.

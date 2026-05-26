# Common Redis Final Deserialize Fix Result

## 1. Changed files/classes

- `chatappBE/common/common-redis/src/main/java/com/example/common/redis/serialization/JsonRedisEventSerializer.java`
- `chatappBE/common/common-redis/src/test/java/com/example/common/redis/contract/RedisContractTest.java`

## 2. Exact deserialize validation order after refactor

`JsonRedisEventSerializer.deserialize(...)` now validates in this order:

1. Parse raw JSON.
2. Require `metadata` node.
3. Read `metadata.eventType`.
4. Reject missing or blank `eventType`.
5. Reject invalid `eventType` syntax via `EventContractValidator.validateEventNameOrThrow(...)`.
6. Reject non-catalog event types via `SharedEventCatalog.isKnownEventType(eventType)`.
7. Read `payload` node.
8. If the event type is payload-less:
   - reject non-null payload,
   - keep `payloadObject = null`.
9. If the event type is payload-bearing:
   - resolve payload class from the injected registry,
   - reject missing payload,
   - convert payload JSON with `ObjectMapper.treeToValue(...)`,
   - wrap payload conversion failures as `RedisPubSubException`.
10. Parse `createdAt`.
11. Build `EventMetadata`.
12. Run final canonical contract validation with `SharedEventCatalog.validatePayloadContract(eventType, payloadObject)`.
13. Return `new EventEnvelope<>(metadata, payloadObject)`.

Strictness guarantees after this change:

- Non-catalog event types are rejected before payload-less handling.
- Non-catalog event types are rejected before `registry.resolvePayload(eventType)`.
- Final canonical payload validation runs before returning `EventEnvelope`.
- Deserialize failures continue to be wrapped as `RedisPubSubException`.

## 3. Tests added/updated

Updated `RedisContractTest` with:

- `deserializer_acceptsCanonicalSharedEnvelope`
  - proves valid canonical deserialize still succeeds.
- `deserializer_rejectsRegistryBackedNonCatalogEventType`
  - uses a custom registry that registers `test.local.event -> String.class`.
  - proves non-catalog event types are rejected before registry misuse can matter.
- `deserializer_rejectsKnownEventTypeWhenCustomRegistryMapsWrongPayloadClass`
  - uses a custom registry that returns `String.class` for a real shared event type.
  - proves final `SharedEventCatalog.validatePayloadContract(...)` blocks wrong payload classes even if registry resolution succeeds.

Existing strict deserialize behavior remains covered for:

- invalid JSON
- missing metadata
- missing event type
- invalid event type syntax
- missing payload for payload-bearing event
- unexpected payload for payload-less event

## 4. Validation command results

Executed from `chatappBE`:

### `./gradlew :common:common-redis:test`

Result: `BUILD SUCCESSFUL`

### `./gradlew :common:common-events:test :common:common-kafka:test :common:common-redis:test`

Result: `BUILD SUCCESSFUL`

### `./gradlew :common:common-redis:compileJava`

Result: `BUILD SUCCESSFUL`

## 5. Remaining blockers, if any

- No remaining freeze blocker was found in the scoped common-only review item for Redis deserialization strictness.
- Optional low-risk cleanup still possible later:
  - rename Redis observer parameter names from `message` to `envelope`,
  - small formatting cleanup in touched Redis files.

## 6. Freeze verdict

- `common-events`: **Freeze-ready**
- `common-kafka`: **Freeze-ready**
- `common-redis`: **Freeze-ready**
- Whole common folder: **Freeze-ready**
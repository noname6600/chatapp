# Common Layer Full Review

## 1. Scope Reviewed
- Hard scope applied: only `chatappBE/common/**` was reviewed for architecture, contracts, runtime flows, tests, and freeze readiness.
- Minimal files outside that scope were read only for Gradle validation discovery: `chatappBE/settings.gradle` and `chatappBE/build.gradle`. No service, gateway, frontend, deployment, database, or other non-common source was inspected or used for architecture judgment.
- Reviewed common modules physically present: `common-core`, `common-event-contract`, `common-events`, `common-feign`, `common-kafka`, `common-redis`, `common-redis-cache`, `common-security`, `common-web`, `common-websocket`.
- `chatappBE/common/build` was treated as generated build output, not a source module.
- No `common-media` module exists under `chatappBE/common`.
- Reviewed main packages/classes:
  - `common-core`: `com.example.common.core.exception` (`BusinessException`, `CommonErrorCode`, `IErrorCode`), `com.example.common.core.pipeline` (`PipelineStep`, `PipelineStepDescriptor`, `PipelineFactory`, `PipelineGraphResolver`, `PipelineExecutor`, `StepCondition`, `StepRetryPolicy`).
  - `common-event-contract`: `com.example.common.event.EventEnvelope`, `com.example.common.event.EventMetadata`.
  - `common-events`: `com.example.common.event` (`EventEnvelope`, `EventMetadata`, `EventPayloadRegistry`, `DefaultEventPayloadRegistry`, `SharedEventCatalog`), `com.example.common.event.validation.EventContractValidator`, and all payload/enums under `com.example.common.integration.account`, `chat`, `enums`, `friendship`, `notification`, `presence`, `user`.
  - `common-feign`: `FeignJwtConfig`, `FeignTraceConfig`.
  - `common-kafka`: `config`, `consumer`, `exception`, `flow`, `observability`, `producer`, `retry`, `serialization`, `topic` packages.
  - `common-redis`: `channel`, `config`, `dispatcher`, `exception`, `flow`, `observability`, `publisher`, `serialization`, `subscriber` packages.
  - `common-redis-cache`: canonical `com.example.common.redis.cache.api/core/exception` classes plus deprecated alias classes under `com.example.common.redis.api/core/exception`.
  - `common-security`: `com.example.common.security.jwt.JwtHelper`.
  - `common-web`: `controller`, `cors`, `exception`, `filter`, `response` packages.
  - `common-websocket`: `adapter.spring`, `auth`, `codec`, `config`, `error`, `frame`, `identity`, `inbound`, `observer`, `sender`, `session`, `subscription` packages.
- Reviewed tests inside common only: `common-events` contract test, `common-kafka` contract test, `common-redis` contract/autoconfiguration tests, `common-security` JWT test, `common-web` CORS/exception tests, and all `common-websocket` tests.

## 2. Module Inventory
- `common-core`
  - Purpose: generic exception and pipeline helpers.
  - Key contents: `BusinessException`/`IErrorCode`/`CommonErrorCode`; generic pipeline graph sorting, step descriptors, retry, timeout, async execution.
  - Responsibility clarity: exception model is narrow; pipeline API is broad and under-specified.
  - Overlap/ambiguity: pipeline is a serious shared runtime abstraction but has no tests and unclear async dependency semantics.

- `common-event-contract`
  - Purpose: intended transport-neutral envelope and metadata contract.
  - Key contents: `EventEnvelope<T>`, `EventMetadata`.
  - Responsibility clarity: conceptually clear, but not actually authoritative because `common-events` defines the same FQCNs.
  - Overlap/ambiguity: direct class duplication with `common-events` is a freeze blocker.

- `common-events`
  - Purpose: shared event catalog, payload registry, event validation, and integration payload/enums.
  - Key contents: event envelope/metadata copies, `SharedEventCatalog`, `EventContractValidator`, `DefaultEventPayloadRegistry`, account/chat/friendship/notification/presence/user event contracts.
  - Responsibility clarity: partly clear as a shared integration contract module; not clear as a reusable common foundation because it mixes generic event mechanics with business event semantics.
  - Overlap/ambiguity: duplicates `common-event-contract` classes and owns domain-specific payloads consumed by Kafka/Redis.

- `common-feign`
  - Purpose: Feign request interceptors for JWT relay and trace propagation.
  - Key contents: `FeignJwtConfig`, `FeignTraceConfig`.
  - Responsibility clarity: narrow.
  - Overlap/ambiguity: trace propagation duplicates raw `traceId`/`X-Trace-Id` strings from `common-web`; no tests or auto-configuration import.

- `common-kafka`
  - Purpose: Kafka event producer/dispatcher/serde/autoconfiguration around the shared event model.
  - Key contents: `KafkaEventProducer`, `DefaultKafkaEventProducer`, `KafkaEventHandler`, `KafkaEventDispatcher`, Kafka serde, retry/DLQ policy, topics, observer, auto-config.
  - Responsibility clarity: mostly clear and transport-specific.
  - Overlap/ambiguity: depends on `common-events`, so Kafka transport depends on app-level event catalog/payloads. It uses "producer/handler/dispatcher" terminology while Redis uses "publisher/subscriber/dispatcher".

- `common-redis`
  - Purpose: Redis Pub/Sub publisher/subscriber/dispatcher/serializer/autoconfiguration around the shared event model.
  - Key contents: `RedisEventPublisher`, `DefaultRedisEventPublisher`, `RedisEventSubscriber`, `RedisEventDispatcher`, `JsonRedisEventSerializer`, channel constants, observer, auto-config.
  - Responsibility clarity: clear for Pub/Sub.
  - Overlap/ambiguity: package namespace overlaps with `common-redis-cache`; depends on `common-events` and therefore on app-level event payloads.

- `common-redis-cache`
  - Purpose: Redis cache manager/cache API with TTL-specific put operations and service-name key prefixing.
  - Key contents: `ITimeRedisCache`, `ITimeRedisCacheManager`, `TimeRedisCache`, `TimeRedisCacheManager`, `CreateCacheException`, plus deprecated compatibility aliases.
  - Responsibility clarity: separate from Pub/Sub but named/described as another "Common Redis Library".
  - Overlap/ambiguity: same root package as `common-redis`, public deprecated aliases, `I*` naming, no tests.

- `common-security`
  - Purpose: JWT helper utilities.
  - Key contents: `JwtHelper.extractUserId`, `JwtHelper.extractAuthorities`.
  - Responsibility clarity: narrow.
  - Overlap/ambiguity: declares dependency on `common-core` but current source does not use it.

- `common-web`
  - Purpose: web response/error/filter/CORS/controller helpers.
  - Key contents: `ApiResponse`, `ApiError`, `TraceIdFilter`, `GlobalExceptionHandler`, `CorsProperties`, `BaseController`.
  - Responsibility clarity: mostly clear.
  - Overlap/ambiguity: CORS lives in `common-web` but still falls back to `common.security.cors`; configuration style differs from Kafka/Redis/WebSocket auto-config.

- `common-websocket`
  - Purpose: reusable realtime/WebSocket foundation: typed frames, identity, auth policy, session/subscription registries, broadcaster/sender, inbound command handling, Spring adapter, auto-config.
  - Key contents: frame/codec/error contracts; `RealtimeIdentity`, `RealtimeAuthorizationPolicy`, registries, sender/broadcaster, inbound handler, observer implementations, Spring handshake/lifecycle/message adapters.
  - Responsibility clarity: strong internal layering. Spring-specific code is mostly isolated under `adapter.spring`.
  - Overlap/ambiguity: event frames depend only on `common-event-contract`, while Kafka/Redis use `common-events` and stricter catalog validation.

## 3. Build/Test Validation
- Command run from `chatappBE`:
  - `.\gradlew.bat :common:common-core:check :common:common-event-contract:check :common:common-events:check :common:common-feign:check :common:common-kafka:check :common:common-redis:check :common:common-redis-cache:check :common:common-security:check :common:common-web:check :common:common-websocket:check --warning-mode all`
  - Result: `BUILD SUCCESSFUL in 14s`; many tasks were up-to-date.
- Command run from `chatappBE` to force actual compile/test execution:
  - `.\gradlew.bat :common:common-core:check :common:common-event-contract:check :common:common-events:check :common:common-feign:check :common:common-kafka:check :common:common-redis:check :common:common-redis-cache:check :common:common-security:check :common:common-web:check :common:common-websocket:check --rerun-tasks --warning-mode all`
  - Result: `BUILD SUCCESSFUL in 46s`; `28 actionable tasks: 28 executed`.
- Modules compiled on forced run:
  - `common-core`, `common-event-contract`, `common-events`, `common-feign`, `common-kafka`, `common-redis`, `common-redis-cache`, `common-security`, `common-web`, `common-websocket`.
- Modules tested on forced run:
  - Test tasks executed with tests: `common-events`, `common-kafka`, `common-redis`, `common-security`, `common-web`, `common-websocket`.
  - Test tasks invoked but `NO-SOURCE`: `common-core`, `common-event-contract`, `common-feign`, `common-redis-cache`.
- Failures/errors:
  - None.
- Warnings/notes:
  - Gradle emitted: `Note: ... common-core/src/main/java/com/example/common/core/pipeline/PipelineStep.java uses unchecked or unsafe operations. Note: Recompile with -Xlint:unchecked for details.`
  - JVM emitted: `OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended`.
  - Gradle emitted an incubating problems report link under `chatappBE/build/reports/problems/problems-report.html`.
- Skipped modules:
  - No common module was skipped. Four modules had no test sources, as listed above.

## 4. What Is Already Strong
- The intended Gradle dependency direction is mostly layered: `common-core` is low-level; `common-web`/`common-security` depend downward on it; transports do not depend on service modules; Kafka/Redis/WebSocket do not import service packages.
- No direct imports from non-common service packages were found under `chatappBE/common/**`.
- Kafka and Redis Pub/Sub now share a strong event-catalog validation pattern. Producers/serializers/dispatchers validate event type syntax, known catalog membership, and payload class.
- Kafka and Redis have useful contract tests around serde, unknown event rejection, payload-less behavior, duplicate handler/subscriber handling, and auto-configuration.
- WebSocket is internally the most polished module: typed frame model, sealed `RealtimeFrame`, explicit session/subscription abstractions, fail-closed authorization auto-config, Spring adapter isolation, race tests, observer safety, and no raw Spring session leakage through core contracts.
- Redis Pub/Sub, Kafka, and WebSocket all expose observer hooks and avoid logging raw token/payload material in the reviewed implementations.
- Redis and Kafka auto-configuration are discoverable through `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`; WebSocket does the same.

## 5. Problems

### High
- `common-event-contract/src/main/java/com/example/common/event/EventEnvelope.java:22`, `common-events/src/main/java/com/example/common/event/EventEnvelope.java:19`, `common-event-contract/src/main/java/com/example/common/event/EventMetadata.java:21`, `common-events/src/main/java/com/example/common/event/EventMetadata.java:19`
  - Why it is a problem: `common-event-contract` and `common-events` define the same FQCNs: `com.example.common.event.EventEnvelope` and `com.example.common.event.EventMetadata`. `common-events/build.gradle:34` also declares `api project(':common:common-event-contract')`, so the depending module both imports and redefines the contract.
  - Impact: classpath shadowing and unpredictable behavior for consumers that combine `common-event-contract`, `common-events`, `common-kafka`, `common-redis`, and `common-websocket`. The two `EventMetadata` implementations are not behaviorally identical: `common-events/EventMetadata.java:40` validates event type syntax, while `common-event-contract/EventMetadata.java:36-49` only validates presence.
  - Recommended fix: make exactly one module own `EventEnvelope` and `EventMetadata`. Prefer `common-event-contract` as the only owner, then make `common-events` add catalog/validator/registry/payloads without redefining those classes. If constructor-level event type validation is desired, put it in the single contract class and update all tests against that one class.

- `common-core/src/main/java/com/example/common/core/pipeline/PipelineExecutor.java:30-38`, `PipelineExecutor.java:47-51`, `PipelineExecutor.java:118-124`
  - Why it is a problem: async pipeline steps are started and only joined after all descriptors have been iterated. A later step sorted after an async dependency can run before that dependency completes. In addition, async steps are scheduled on `executor`, then `runStep()` schedules the actual step again on the same `executor` and blocks on `.join()`.
  - Impact: dependency ordering is not actually enforced for async steps. With a single-thread or saturated executor, an async step can occupy the executor thread while waiting for another task queued to the same executor, causing timeout/failure and possible delayed side effects. This is a shared foundation runtime bug, not polish.
  - Recommended fix: define pipeline semantics precisely and add contract tests. Either execute dependencies in DAG levels and wait for a level before dependents run, or reject async/dependency combinations that cannot be honored. Avoid nested scheduling onto the same executor for already-async execution.

- `common-kafka/build.gradle:34`, `common-redis/build.gradle:34`, `common-websocket/build.gradle:33`, `common-kafka/src/main/java/com/example/common/kafka/producer/DefaultKafkaEventProducer.java:46-54`, `common-redis/src/main/java/com/example/common/redis/publisher/DefaultRedisEventPublisher.java:31-39`, `common-websocket/src/main/java/com/example/common/websocket/frame/RealtimeEventFrame.java:19-25`, `common-events/src/main/java/com/example/common/event/SharedEventCatalog.java:229`
  - Why it is a problem: Kafka and Redis depend on `common-events` and reject unknown/non-catalog event types. WebSocket depends only on `common-event-contract` and `RealtimeEventFrame` validates only frame type and non-null envelope. `SharedEventCatalog.validatePayloadContract()` also explicitly passes unknown service-local events, while Kafka/Redis pre-check and reject them.
  - Impact: common has no single event admission policy. The same `EventEnvelope` can be accepted as a WebSocket event frame and rejected by Kafka/Redis. Freeze would lock in a split-brain contract.
  - Recommended fix: decide one common policy: either all transports accept any syntactically valid envelope and optionally validate catalog payloads, or all common transports enforce the shared catalog. Then add cross-transport contract tests that prove the same envelope is treated consistently.

- `common-redis-cache`
  - Why it is a problem: this is a public shared cache module with zero tests, deprecated public alias APIs (`com.example.common.redis.api.ITimeRedisCacheManager.java:6`, `com.example.common.redis.core.TimeRedisCacheManager.java:9`, etc.), package overlap with `common-redis`, and mutable runtime behavior such as `TimeRedisCacheManager.cacheAvailable` (`TimeRedisCacheManager.java:27`, `60-89`) with no concurrency or failure-contract coverage.
  - Impact: the full common layer cannot be frozen as a reusable foundation while an included public module has unproved cache behavior and visible migration aliases.
  - Recommended fix: either exclude `common-redis-cache` from the freeze set explicitly, or add focused tests for TTL put/putIfAbsent, service-name key prefixing, Redis failure fallback, `clearCacheFail()`, null service names, and alias compatibility. Then decide whether deprecated alias packages remain part of the frozen API.

### Medium
- `common-events/src/main/java/com/example/common/event/SharedEventCatalog.java:3-28`, `common-events/src/main/java/com/example/common/integration/**`
  - Why it is a problem: generic event platform mechanics and business integration semantics live in the same module. `SharedEventCatalog` imports account/chat/friendship/notification/presence/user payloads directly.
  - Impact: `common-kafka` and `common-redis` transitively depend on all app-specific payload contracts. That may be acceptable for this product, but it is not a domain-neutral reusable common foundation.
  - Recommended fix: make the ownership explicit. Either rename/document `common-events` as product integration contracts, or split generic event infrastructure from app integration payloads while keeping the split inside `common` if needed.

- `common-web/src/main/java/com/example/common/web/filter/TraceIdFilter.java:17-33`, `common-feign/src/main/java/com/example/common/feign/FeignTraceConfig.java:14-16`
  - Why it is a problem: trace propagation uses duplicated string constants (`traceId`, `X-Trace-Id`) across modules with no shared contract.
  - Impact: future changes can silently break trace relay between inbound web and outbound Feign.
  - Recommended fix: move trace key/header constants to a small common contract, likely `common-core` or `common-web`, and make Feign/Web use the same source.

- `common-kafka/src/main/java/com/example/common/kafka/producer/DefaultKafkaEventProducer.java:30-32`, `DefaultKafkaEventProducer.java:56`, `common-redis/src/main/java/com/example/common/redis/publisher/DefaultRedisEventPublisher.java:14-19`, `DefaultRedisEventPublisher.java:41-52`
  - Why it is a problem: public constructors accept observer dependencies but do not validate null and do not fall back to no-op observers. Auto-configuration supplies observers, but direct construction is still a public API.
  - Impact: validation or publish/send error paths can throw `NullPointerException` instead of the module's typed exception.
  - Recommended fix: enforce non-null with `Objects.requireNonNull`, or normalize null to a no-op observer in constructors.

- `common-web`, `common-feign`
  - Why it is a problem: these modules use `@Component`, `@Configuration`, `@RestControllerAdvice`, and `@ConfigurationProperties` directly, while Kafka/Redis/WebSocket use Boot auto-configuration imports.
  - Impact: common modules do not have a consistent activation model. Some are explicit auto-config modules; others rely on component scanning/import behavior.
  - Recommended fix: standardize on Boot auto-configuration for common framework modules, or document that web/feign are intentionally scan/import-only.

- `common-web/src/main/java/com/example/common/web/cors/CorsProperties.java:20-31`
  - Why it is a problem: final common-web CORS configuration still has a legacy fallback from `common.web.cors` to `common.security.cors`.
  - Impact: freezes an old namespace into the shared contract and blurs web/security ownership.
  - Recommended fix: either remove the fallback before freeze or document it as a deliberate compatibility contract with an owner and removal policy.

- `common-redis-cache/src/main/java/com/example/common/redis/cache/api/ITimeRedisCacheManager.java:28-29`, `common-redis-cache/src/main/java/com/example/common/redis/cache/core/TimeRedisCache.java:75-80`
  - Why it is a problem: the cache manager API casts `getCache()` to `TimeRedisCache`, and `TimeRedisCache` prefixes keys with `serviceName` without validating that `serviceName` is non-blank.
  - Impact: non-`TimeRedisCache` implementations can throw `ClassCastException`, and null service names can create `null::` key prefixes.
  - Recommended fix: validate service name in constructors/builders and either narrow the contract to `TimeRedisCache` explicitly or guard/cast with a typed failure.

- `common-kafka`, `common-redis`, `common-websocket`
  - Why it is a problem: observer and flow vocabulary is not standardized. Kafka uses `logProduceSuccess/logDispatch`, Redis uses `logPublish/logReceive/logError`, and WebSocket uses lifecycle `on*` callbacks.
  - Impact: the modules feel like related but separately designed systems rather than one coherent platform.
  - Recommended fix: create a terminology matrix or shared observer naming convention. Keep transport-specific words where needed, but standardize success/failure/drop/unknown semantics.

### Low
- `common-core/src/main/java/com/example/common/core/pipeline/PipelineStep.java:7-8`
  - Why it is a problem: raw `new Class[0]` creates the unchecked compiler note seen during validation.
  - Impact: low operational risk, but not freeze-polished.
  - Recommended fix: add a safe typed empty constant/factory or suppress the warning intentionally with explanation.

- `common-core/src/main/java/com/example/common/core/pipeline/PipelineExecutor.java:71`, `PipelineExecutor.java:86-88`
  - Why it is a problem: `stepName` and `duration` are computed but unused.
  - Impact: polish debt and evidence that metrics/logging were started but not completed.
  - Recommended fix: remove them or wire them into a real observer/metric contract.

- `common-security/build.gradle:33`, `common-security/src/main/java/com/example/common/security/jwt/JwtHelper.java`
  - Why it is a problem: `common-security` declares `implementation project(':common:common-core')`, but reviewed source does not import/use common-core.
  - Impact: unnecessary dependency edge.
  - Recommended fix: remove the dependency unless planned security exceptions use it.

- `common-websocket/src/main/java/com/example/common/websocket/auth/DevelopmentOnlyAllowAllRealtimeAuthorizationPolicy.java:8-14`, `common-websocket/src/main/java/com/example/common/websocket/adapter/spring/QueryParamRealtimeHandshakeTokenResolver.java:10-18`
  - Why it is a problem: development/compatibility APIs are public in the frozen module.
  - Impact: not a blocker because WebSocket auto-config is fail-closed, but these APIs can be mistaken for production defaults.
  - Recommended fix: keep them only if clearly documented as test/dev helpers and excluded from auto-config, or move them to test/support package.

- `common-events/src/main/java/com/example/common/event/EventEnvelope.java:10-12`
  - Why it is a problem: Javadoc opens a paragraph and does not close it, unlike the copy in `common-event-contract`.
  - Impact: minor documentation polish issue.
  - Recommended fix: fix after the event contract ownership split.

## 6. Dependency Direction Review
- Clean dependencies:
  - `common-core` has no common module dependency.
  - `common-web -> common-core` is appropriate because `GlobalExceptionHandler` consumes `BusinessException` and `CommonErrorCode`.
  - `common-kafka -> common-events` and `common-redis -> common-events` are directionally acceptable if transports are intended to enforce product shared event contracts.
  - `common-websocket -> common-event-contract` is clean if WebSocket is intended to be generic over event envelopes.
  - No Gradle circular dependencies were found among common modules.
- Bad dependencies or dependency risks:
  - `common-events -> common-event-contract` is not clean while `common-events` redefines `EventEnvelope` and `EventMetadata`.
  - `common-kafka` and `common-redis` know the full integration event catalog through `common-events`, so transport modules are coupled to product event semantics.
  - `common-websocket` intentionally avoids `common-events`, but that makes event validation behavior diverge from Kafka/Redis.
  - `common-redis-cache` shares the `com.example.common.redis` root namespace with `common-redis` without a dependency relationship or clear package boundary.
  - `common-security -> common-core` appears unnecessary in current source.
- Circularity risks:
  - No declared Gradle cycle, but duplicate FQCNs create a classpath-level ownership conflict that is more dangerous than a normal dependency cycle because Gradle can still build successfully.
- Boundary leaks:
  - Spring WebSocket types are well-contained in `common-websocket.adapter.spring`.
  - Kafka and Redis transport details do not leak into `common-core`.
  - Business event semantics are present in `common-events` and transitively in Kafka/Redis.
  - Cache APIs directly extend Spring cache/Redis cache classes, so `common-redis-cache` is Spring Redis-specific by design and should not be treated as a generic cache abstraction.

## 7. Standardization Review
- Aligned areas:
  - Kafka, Redis, and WebSocket all use `EventEnvelope` as the semantic event container.
  - Kafka and Redis both have producer/publisher, dispatcher, handler/subscriber, serializer, observer, routing context, exception, and auto-config concepts.
  - WebSocket has a strong internal vocabulary around `RealtimeFrame`, `RealtimeDestination`, `RealtimeSession`, `RealtimeSubscription`, `RealtimeMessageSender`, and `RealtimeBroadcaster`.
  - Contract guard tests exist for event catalog, Kafka, Redis, and WebSocket.
- Divergent areas:
  - Event contract ownership is split: `common-event-contract` and `common-events` both define the same core classes.
  - Kafka says `Producer`/`Handler`; Redis says `Publisher`/`Subscriber`; WebSocket says `Sender`/`Broadcaster`/`InboundFrameHandler`.
  - Kafka unknown events have a policy (`FAIL`/`DROP`) at dispatcher level; Redis drops valid known events with no subscriber but rejects non-catalog events; WebSocket has command errors but no event catalog validation for outbound `RealtimeEventFrame`.
  - Kafka serde throws `IllegalArgumentException`; Redis serde wraps in `RedisPubSubException`; WebSocket codec throws `RealtimeCodecException`.
  - Kafka/Redis/WebSocket use auto-configuration imports; web/feign rely on direct configuration/component annotations.
  - `IErrorCode`, `ITimeRedisCache`, `ITimeRedisCacheManager` use `I*` interface naming while newer modules use noun-based names (`KafkaEventProducer`, `RealtimeObserver`, `RedisEventSubscriber`).
  - `common-redis-cache` and `common-redis` share a root package but not terminology or test quality.
- Duplicate abstractions:
  - `EventEnvelope` and `EventMetadata` are direct duplicates.
  - Trace ID propagation exists in both web and feign as raw constants.
  - Redis cache and Redis Pub/Sub are separate responsibilities but are named/described similarly enough to confuse ownership.

## 8. Freeze Readiness
- Common layer verdict: NO.
- Per-module freeze notes:
  - `common-core`: NO. Pipeline async/dependency behavior is not freeze-ready and has no tests.
  - `common-event-contract`: NO. It is not the single owner of event envelope/metadata and has no tests.
  - `common-events`: NO until the duplicate contract classes are removed or made authoritative. The catalog tests are strong once ownership is fixed.
  - `common-kafka`: CONDITIONAL. Internally tested and mostly coherent, but blocked by event contract ownership and event admission policy split.
  - `common-redis`: CONDITIONAL. Internally tested and mostly coherent, but blocked by event contract ownership and event admission policy split.
  - `common-redis-cache`: NO. Public API is untested and still exposes deprecated alias packages.
  - `common-security`: YES with low cleanup. Small and tested, but remove the unused common-core dependency.
  - `common-web`: CONDITIONAL. Useful and tested, but config activation and legacy CORS ownership should be settled before treating it as frozen platform behavior.
  - `common-feign`: CONDITIONAL/NO for freeze. Narrow but untested and not aligned with the common auto-config style.
  - `common-websocket`: CONDITIONAL. Internally strong, but event frame validation is not aligned with Kafka/Redis.
- True freeze blockers only:
  - Duplicate `com.example.common.event.EventEnvelope` and `EventMetadata` across two common modules.
  - No single event admission/validation policy across Kafka, Redis, and WebSocket.
  - `common-core` pipeline async/dependency semantics are incorrect or at least unproved.
  - `common-redis-cache` cannot be frozen as part of the whole common foundation without tests and a decision on deprecated alias APIs.

## 9. Post-Freeze Cleanup
- Remove or formally time-box the `common.security.cors` fallback in `common-web`.
- Standardize observer vocabulary and success/failure/drop naming across Kafka, Redis, and WebSocket.
- Standardize common module activation style, preferably with Boot auto-configuration imports for framework modules.
- Move trace ID header/MDC names into one shared constant.
- Remove the unused `common-security -> common-core` dependency if it remains unused.
- Fix low-level Javadocs and unused variables after contract ownership is resolved.
- Decide whether `DevelopmentOnlyAllowAllRealtimeAuthorizationPolicy` and query-param token resolver remain public support APIs or move to test/dev support.
- Normalize older `I*` interface naming in future APIs; do not rename existing public APIs after freeze without a compatibility plan.

## 10. Final Recommendation
- Do not freeze the whole `chatappBE/common/**` layer yet.
- Next steps, inside `common` only:
  - Collapse event envelope/metadata ownership into one module and remove duplicate FQCNs.
  - Decide and test one cross-transport event admission policy for Kafka, Redis, and WebSocket.
  - Fix and test `common-core` pipeline async/dependency semantics.
  - Bring `common-redis-cache` to contract-test parity or explicitly exclude it from the frozen foundation.
  - After blockers, standardize configuration/observer/error vocabulary across common modules.
- Service-level usage, service rewrites, gateway behavior, frontend behavior, deployment, and database concerns are out of scope for this review.

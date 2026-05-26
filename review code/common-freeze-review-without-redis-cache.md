# Common Freeze Review (Excluding common-redis-cache)

## 1. Scope Reviewed

- Included modules:
  - `chatappBE/common/common-core`
  - `chatappBE/common/common-event-contract`
  - `chatappBE/common/common-events`
  - `chatappBE/common/common-feign`
  - `chatappBE/common/common-kafka`
  - `chatappBE/common/common-redis`
  - `chatappBE/common/common-security`
  - `chatappBE/common/common-web`
  - `chatappBE/common/common-websocket`
- Excluded modules:
  - `chatappBE/common/common-redis-cache/**`
- Boundary limits applied:
  - Reviewed only files under the included `chatappBE/common/**` modules.
  - Did not inspect or evaluate service modules, gateway, frontend, deployment/infrastructure, external database schema, or business/domain logic outside the included common layer.
  - `common-redis-cache` was excluded from review effort and is not used as a blocker.

## 2. Build/Test Validation

- Commands run from `chatappBE`:
  - `.\gradlew.bat :common:common-core:check :common:common-event-contract:check :common:common-events:check :common:common-feign:check :common:common-kafka:check :common:common-redis:check :common:common-security:check :common:common-web:check :common:common-websocket:check --warning-mode all`
  - `.\gradlew.bat :common:common-core:check :common:common-event-contract:check :common:common-events:check :common:common-feign:check :common:common-kafka:check :common:common-redis:check :common:common-security:check :common:common-web:check :common:common-websocket:check --rerun-tasks --warning-mode all`
- Compile status:
  - PASS for all included freeze-target modules.
- Test status:
  - PASS for all included freeze-target modules.
- Warnings and notes:
  - First command: build successful, all tasks up-to-date, no actionable warning text printed.
  - Second command: build successful, 29 tasks executed.
  - Second command printed `Note: ... PipelineStep.java uses unchecked or unsafe operations. Note: Recompile with -Xlint:unchecked for details.`
  - Second command printed `OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended`.
  - Gradle also printed an incubating problems report location, but no deprecation warning details were printed to the console under `--warning-mode all`.
  - These warnings are not, by themselves, true freeze blockers.
- Modules with real test sources:
  - `common-core`: 1 test file.
  - `common-events`: 1 test file.
  - `common-kafka`: 1 test file.
  - `common-redis`: 2 test files.
  - `common-security`: 1 test file.
  - `common-web`: 2 test files.
  - `common-websocket`: 24 test files.
- Modules with no test sources:
  - `common-event-contract`: no local test source; its `EventEnvelope` / `EventMetadata` behavior is currently exercised from `common-events`.
  - `common-feign`: no local test source.

## 3. What Improved

- Event contract ownership is materially improved.
  - `com.example.common.event.EventEnvelope` is defined once at `common-event-contract/src/main/java/com/example/common/event/EventEnvelope.java:22`.
  - `com.example.common.event.EventMetadata` is defined once at `common-event-contract/src/main/java/com/example/common/event/EventMetadata.java:22`.
  - No duplicate FQCN definitions of those two classes were found inside the included freeze target.
  - `common-events/build.gradle:34` uses `api project(':common:common-event-contract')`, so `common-events` now consumes the shared contract instead of owning duplicate envelope/metadata classes.
- Shared event catalog ownership is cleaner.
  - `SharedEventCatalog` centralizes known shared event types and canonical payload classes.
  - `EventContractValidator.isRegisteredEventType(...)` delegates to `SharedEventCatalog.isKnownEventType(...)`, avoiding a second registration knowledge source.
  - `SharedEventModelContractTest` verifies catalog disjointness, completeness against shared event enums, payload-bearing/payload-less behavior, and wrong-payload rejection.
- Kafka and Redis admission are now aligned with each other.
  - Kafka serializer/producer/dispatcher reject non-catalog event types and call `SharedEventCatalog.validatePayloadContract(...)`.
  - Redis serializer/publisher/dispatcher reject non-catalog event types and call `SharedEventCatalog.validatePayloadContract(...)`.
  - Kafka and Redis tests now cover canonical acceptance, unknown/non-canonical rejection, payload-less handling, missing payload, and wrong payload class cases.
- Dependency direction is cleaner than before.
  - `common-events -> common-event-contract`.
  - `common-kafka -> common-events`.
  - `common-redis -> common-events`.
  - `common-websocket -> common-event-contract`.
  - `common-security -> common-core`.
  - `common-web -> common-core`.
  - No included module project dependency was found pointing to services, gateway, frontend, or another transport module.
- `common-core` has more pipeline tests than before.
  - Current tests cover synchronous dependency ordering, nominal async ordering, timeout, retry success, retry exhaustion, conditional skip, conditional run, and empty pipeline.
  - These tests are useful, but they do not close the pipeline blocker described below.

## 4. Remaining Problems

### High

- Issue: WebSocket event admission is still split-brain against Kafka and Redis.
  - Module/file/class:
    - `common-websocket/src/main/java/com/example/common/websocket/frame/RealtimeEventFrame.java`, class `RealtimeEventFrame`.
    - `common-websocket/src/main/java/com/example/common/websocket/codec/JsonRealtimeFrameCodec.java`, class `JsonRealtimeFrameCodec`.
    - `common-websocket/src/test/java/com/example/common/websocket/frame/RealtimeFrameContractTest.java`, class `RealtimeFrameContractTest`.
    - Contrast with `common-kafka/src/main/java/com/example/common/kafka/serialization/EventEnvelopeKafkaSerializer.java`, `EventEnvelopeKafkaDeserializer.java`, `DefaultKafkaEventProducer.java`, `KafkaEventDispatcher.java`.
    - Contrast with `common-redis/src/main/java/com/example/common/redis/serialization/JsonRedisEventSerializer.java`, `DefaultRedisEventPublisher.java`, `RedisEventDispatcher.java`.
  - Evidence:
    - Kafka validates catalog membership and payload contract at `EventEnvelopeKafkaSerializer.java:50-55`, `EventEnvelopeKafkaDeserializer.java:51-83`, `DefaultKafkaEventProducer.java:47-54`, and `KafkaEventDispatcher.java:129-134`.
    - Redis validates catalog membership and payload contract at `JsonRedisEventSerializer.java:53-58`, `JsonRedisEventSerializer.java:97-163`, `DefaultRedisEventPublisher.java:33-39`, and `RedisEventDispatcher.java:53-58`.
    - WebSocket `RealtimeEventFrame` only checks frame type and non-null payload envelope at `RealtimeEventFrame.java:19-24`; it does not call `SharedEventCatalog`, `EventContractValidator`, or an equivalent common admission policy.
    - WebSocket codec dispatches `EVENT` frames directly to `RealtimeEventFrame.class` at `JsonRealtimeFrameCodec.java:67` without shared catalog validation.
    - WebSocket tests build and accept an event envelope with `eventType = "chat.message.created"` at `RealtimeFrameContractTest.java:35-39` and assert that this non-catalog value is preserved at `RealtimeFrameContractTest.java:60-64`.
  - Why it is still a problem:
    - The same representative envelope can be accepted by WebSocket framing while Kafka and Redis reject it as non-catalog.
    - This is exactly the cross-transport admission split-brain the freeze review was asked to confirm fixed.
    - The module dependency shape contributes to the problem: `common-websocket/build.gradle:33` depends only on `common-event-contract`, while Kafka and Redis depend on `common-events`; WebSocket currently cannot use `SharedEventCatalog` without adding a dependency or introducing a lower-level shared validator.
  - Impact:
    - Shared event semantics are not transport-neutral.
    - A service can accidentally emit a WebSocket event frame that cannot be published or consumed through Kafka/Redis under the common contract.
    - This is a contradictory shared contract and hidden API ambiguity likely to cause misuse.
  - Recommended fix:
    - Choose and encode one final admission policy for all three transports.
    - If the final policy is "only registered shared event types over common transports", make `common-websocket` validate `RealtimeEventFrame` construction and codec decode/encode paths with the same catalog/payload rules used by Kafka and Redis.
    - If WebSocket intentionally allows service-local events, make that optionality explicit in a shared admission abstraction and add tests proving the non-conflicting policy across Kafka, Redis, and WebSocket.
    - Add cross-transport tests using the same canonical envelope, unknown event type, payload-less event with payload, payload-bearing event with null payload, and wrong payload class.

- Issue: `PipelineExecutor` still does not enforce async dependency completion before dependent steps can run.
  - Module/file/class:
    - `common-core/src/main/java/com/example/common/core/pipeline/PipelineExecutor.java`, class `PipelineExecutor`.
    - `common-core/src/test/java/com/example/common/core/pipeline/PipelineExecutorTest.java`, class `PipelineExecutorTest`.
  - Evidence:
    - `PipelineExecutor.execute(...)` creates `stepFutures` at `PipelineExecutor.java:47`.
    - Async steps are submitted with `CompletableFuture.runAsync(...)` at `PipelineExecutor.java:56-59`.
    - The executor immediately continues iterating later sorted descriptors and only waits for all futures at the end at `PipelineExecutor.java:69-70`.
    - There is no per-step wait on `descriptor.getRunAfter()` dependencies before submitting or running the dependent descriptor.
    - The only async dependency test, `asynchronous_steps_execute_with_dependency_ordering`, uses a single-thread executor at `PipelineExecutorTest.java:197-208`, which proves submission order under one executor thread, not dependency completion under real concurrent execution.
  - Why it is still a problem:
    - Topological sorting gives submission order only; it does not guarantee completion order once async steps are involved.
    - A synchronous step after a slow async dependency can run immediately before the async dependency completes.
    - An async dependent step submitted to a multi-thread executor can run concurrently with, or before completion of, its declared dependency.
    - If an async dependency eventually fails, dependent steps may already have run before `CompletableFuture.allOf(...).join()` reports the failure.
  - Impact:
    - This is a runtime correctness bug in `common-core`.
    - It violates the documented pipeline contract that a step runs after all declared dependencies complete.
    - It makes dependency ordering, failure propagation, and async behavior materially unproved for a core common runtime primitive.
  - Recommended fix:
    - Build execution futures from the dependency graph, so each step future composes after all dependency futures complete.
    - Alternatively, explicitly disallow async steps with dependencies or dependents, enforce that restriction at construction/resolution time, and test the rejection path.
    - Add tests with slow async dependencies, mixed async-to-sync dependencies, async-to-async dependencies on a multi-thread executor, dependency failure preventing dependents, timeout failure preventing dependents, and skipped dependency semantics.

- Issue: `PipelineExecutor` still has nested scheduling and contract ambiguity for synchronous execution and timeout.
  - Module/file/class:
    - `common-core/src/main/java/com/example/common/core/pipeline/PipelineExecutor.java`, class `PipelineExecutor`.
  - Evidence:
    - The class documentation says synchronous steps execute directly on the calling thread at `PipelineExecutor.java:12`.
    - `runWithTimeout(...)` schedules every step body through `CompletableFuture.runAsync(...)` at `PipelineExecutor.java:116-119`.
    - The nested task is sent to `ForkJoinPool.commonPool()` at `PipelineExecutor.java:119`.
  - Why it is still a problem:
    - Synchronous steps are not actually executed directly on the calling thread once `runWithTimeout(...)` is used.
    - The code avoids reusing the same executor for nested scheduling, but still uses a hidden executor (`ForkJoinPool.commonPool`) for all step bodies.
    - Timeout is implemented by timing out the future join; the underlying step can continue running on the common pool after the pipeline reports timeout.
  - Impact:
    - Thread-affine context such as security context, transaction context, MDC, or request context can be lost for "synchronous" pipeline steps.
    - Timed-out work can continue mutating shared context after failure is reported.
    - This is not freeze-ready for a common runtime primitive unless the behavior is explicitly part of the contract and tested.
  - Recommended fix:
    - Make the execution contract explicit: either truly run synchronous steps on the caller thread without timeout preemption, or document and own the executor hop.
    - If timeout remains part of the contract, use a controlled scheduler/executor model and document whether cancellation/interruption is guaranteed.
    - Add tests proving caller-thread behavior or the intentionally chosen executor behavior, plus tests proving whether timed-out work can or cannot continue mutating the context.

### Medium

- None found beyond the high-severity blockers above.

### Low

- Issue: `PipelineStep.runAfter()` emits an unchecked/unsafe compile note.
  - Module/file/class:
    - `common-core/src/main/java/com/example/common/core/pipeline/PipelineStep.java`, interface `PipelineStep`.
  - Evidence:
    - The rerun validation printed: `PipelineStep.java uses unchecked or unsafe operations`.
    - The likely source is the raw empty array returned by `runAfter()` at `PipelineStep.java:7`.
  - Why it is still a problem:
    - It adds warning noise to freeze validation.
  - Impact:
    - Low. This is not a runtime correctness blocker and should not drive the freeze verdict by itself.
  - Recommended fix:
    - Clean up or locally suppress the generic array warning after the true pipeline contract blockers are fixed.

- Issue: Some included modules have no local tests.
  - Module/file/class:
    - `common-event-contract`
    - `common-feign`
  - Evidence:
    - Gradle reported `compileTestJava NO-SOURCE` and `test NO-SOURCE` for both modules.
  - Why it is still a problem:
    - `common-event-contract` is the owner of the envelope/metadata API, but its behavior is tested indirectly from `common-events`.
    - `common-feign` currently has no local guard tests.
  - Impact:
    - Low. This is not a freeze blocker by itself because the included checks pass and key event model behavior is covered from `common-events`.
  - Recommended fix:
    - Add small owner-local contract tests for `EventEnvelope` / `EventMetadata`.
    - Add minimal compile/shape tests for `common-feign` configuration if this module is intended to be frozen as a stable API.

## 5. Dependency Direction Review

- Clean dependencies found:
  - `common-events/build.gradle:34` uses `api project(':common:common-event-contract')`.
  - `common-kafka/build.gradle:34` uses `api project(':common:common-events')`.
  - `common-redis/build.gradle:34` uses `api project(':common:common-events')`.
  - `common-websocket/build.gradle:33` uses `api project(':common:common-event-contract')`.
  - `common-security/build.gradle:33` uses `implementation project(':common:common-core')`.
  - `common-web/build.gradle:34` uses `implementation project(':common:common-core')`.
  - `common-core`, `common-event-contract`, and `common-feign` have no project dependency on another included module.
- Remaining bad dependencies:
  - No direct project dependency from included common modules to services, gateway, frontend, deployment, or another transport module was found.
  - No Kafka-to-Redis, Redis-to-Kafka, or WebSocket-to-Kafka/Redis project dependency was found.
- Boundary leaks:
  - No service/gateway/frontend package imports were found in the included common source set.
  - The remaining boundary problem is not a service leak; it is an internal common-layer policy split. Kafka and Redis depend on `common-events` and can enforce `SharedEventCatalog`; WebSocket depends only on `common-event-contract` and currently admits event envelopes without the shared catalog.

## 6. Freeze Readiness

- Verdict for the included freeze target only: NO.
- Exact true blockers:
  - Cross-transport event admission is still contradictory. Kafka and Redis reject non-catalog or wrong-payload shared envelopes, while WebSocket event frames can accept/preserve a non-catalog event envelope.
  - `common-core` pipeline execution still has an async dependency correctness bug and materially unproved critical runtime behavior.
  - `PipelineExecutor` still has ambiguous nested scheduling/timeout semantics that contradict its documented synchronous execution contract.
- `common-redis-cache` was explicitly excluded from this review and is not part of this verdict.

## 7. Post-Freeze Cleanup

- After the blockers are fixed and the target is actually freeze-ready, clean up the unchecked `PipelineStep.runAfter()` warning.
- Consider moving or duplicating minimal owner-local tests for `EventEnvelope` and `EventMetadata` into `common-event-contract`.
- Consider adding minimal tests for `common-feign` if the module is intended to be frozen as a stable API.
- Normalize a few stale test names such as "UnknownPayloadBearingEventType" when the case is now "unknown non-catalog event type"; this is wording cleanup only.

## 8. Final Recommendation

- The included common freeze target should not be frozen yet.
- The event ownership blocker is fixed.
- Kafka and Redis are aligned with each other.
- The remaining blockers are still in the included freeze target itself: WebSocket event admission is inconsistent with Kafka/Redis, and `common-core` pipeline execution is not correct or explicit enough for freeze.
- Do not move this freeze target to service integration as the next step until those common-layer blockers are fixed and proven.
- Service-level concerns remain out of scope for this review.

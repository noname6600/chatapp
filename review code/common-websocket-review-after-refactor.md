# Common WebSocket Review After Refactor

## 1. Executive Summary
- Current state: `common-websocket` is much more standardized than before. The refactor added a dedicated Spring connection/lifecycle layer, removed concrete JWT/security implementation from the base module, introduced typed realtime frames, standardized destination/subscription/session registries, and added typed send results.
- The module is close, but it is not freeze-ready yet. The remaining issues are inside `common-websocket` itself: the inbound error boundary is incomplete, principal nullability is inconsistent, lifecycle ownership still leaks through public connection-manager APIs and sender-side cleanup, and send-failure observability is not fully typed.
- Overall verdict: NOT READY

## 2. What Improved
- `SpringRealtimeConnectionManager` now owns the physical `WebSocketSession` map and per-session locks.
- `SpringRealtimeLifecycleAdapter` now centralizes connect, disconnect, transport-error cleanup, and inbound dispatch orchestration.
- `SpringRealtimeMessageSender` no longer stores the physical session map itself and returns `RealtimeSendResult` with `RealtimeSendFailureReason`.
- Stale disconnect and stale transport-error cleanup are guarded by expected-session removal in `SpringRealtimeConnectionManager.disconnect(...)`.
- Concrete JWT/security implementation is gone from the base module. The remaining auth surface is generic: `RealtimeIdentityResolver`, `RealtimeAuthorizationPolicy`, and `HandshakeTokenResolver`.
- The wire contract is much clearer: sealed `RealtimeFrame`, typed command/event/error frames, `RealtimeErrorCode`, `RealtimeDestination`, and explicit silent success for subscribe/unsubscribe commands.
- Session and subscription registries are split into dedicated main packages: `session` and `subscription`.
- Auto-configuration is fail-closed for authorization-dependent inbound and handshake beans because `RealtimeAuthorizationPolicy` is not auto-created.
- Observer strategy improved with `NoOpRealtimeObserver`, `CompositeRealtimeObserver`, `LoggingRealtimeObserver`, and `MicrometerRealtimeObserver`.
- Tests now cover stale disconnect, stale transport error, stale send failure, stale inbound sessions, duplicate subscriptions, frame invariants, and public contract guards.

## 3. Remaining Problems

### Critical

#### 1. Inbound internal-error boundary is incomplete
- Exact class/file: `DefaultRealtimeInboundFrameHandler` in `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/inbound/DefaultRealtimeInboundFrameHandler.java`
- Exact class/file: `SpringRealtimeLifecycleAdapter` in `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeLifecycleAdapter.java`
- Why it is still a problem: `DefaultRealtimeInboundFrameHandler.handleRawFrame(...)` only catches `RealtimeCodecException` around `codec.decode(...)` and only catches generic `Exception` around `handleCommand(...)`. Registry lookup, unexpected codec runtime failures, invalid-frame `sendError(...)`, and sender failures while sending an error can still escape. `SpringRealtimeLifecycleAdapter.onTextMessage(...)` also invokes `inboundFrameHandler.handleRawFrame(...)` through `runIfCurrentSession(...)` without a catch boundary, so a custom or failing inbound handler can still propagate an exception to the Spring transport layer.
- Impact: The module's own claim that every inbound path converts unexpected failures into a safe `INTERNAL_ERROR` websocket path is not true. A freeze would lock in a contract where some internal failures still bypass standardized error handling and may close or destabilize the transport handler without a safe client error frame.
- Recommended fix: Put one top-level try/catch boundary around the entire inbound processing path after argument validation. Convert unexpected exceptions to `RealtimeErrorCode.INTERNAL_ERROR` with a safe message and correlation ID when available. Make error-frame sending best-effort and non-throwing. Add a lifecycle-level catch around `inboundFrameHandler.handleRawFrame(...)` so custom handlers cannot escape the transport boundary. Add tests for: codec throwing non-`RealtimeCodecException`, registry throwing, authorization policy throwing, subscription registry throwing, sender throwing while sending an error frame, and a custom inbound handler throwing from `SpringRealtimeLifecycleAdapter`.

### Major

#### 1. Lifecycle ownership still leaks through public connection-manager APIs
- Exact class/file: `SpringRealtimeConnectionManager` in `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeConnectionManager.java`
- Why it is still a problem: The class documentation says lifecycle methods are package-private and external code must go through `SpringRealtimeLifecycleAdapter`, but `connect(...)`, `disconnect(...)`, `executeUnderSessionLock(...)`, `getCurrentSession(...)`, `isCurrentSession(...)`, and `runIfCurrentSession(...)` are all public.
- Impact: The physical lifecycle owner is now separated, but its raw mutation API is exposed as a public bean-level surface. Freezing this would preserve a bypass around the lifecycle adapter and make ownership rules easier to violate from inside any consumer of the module.
- Recommended fix: Keep the class public only if Spring bean construction requires it, but make lifecycle mutation/inspection methods package-private where possible. Alternatively introduce a small internal collaborator and expose only the higher-level lifecycle adapter and sender contracts as public module API.

#### 2. `SpringRealtimeMessageSender` is not purely send-focused yet
- Exact class/file: `SpringRealtimeMessageSender` in `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeMessageSender.java`
- Why it is still a problem: The sender delegates physical session operations to `SpringRealtimeConnectionManager`, which is good, but it still owns `RealtimeSessionRegistry` and `RealtimeSubscriptionRegistry` dependencies and directly performs registry/subscription cleanup in `triggerDeadCleanup(...)`.
- Impact: Sending and lifecycle cleanup remain coupled. This is much better than owning the session map, but the sender still has hidden lifecycle side effects and optional registry dependencies. That makes lifecycle ownership only partially separated.
- Recommended fix: Move dead-session cleanup orchestration behind the lifecycle/connection component, or introduce a dedicated cleanup callback/component injected into both lifecycle and sender. The sender should either only return typed failure results, or call a clearly named lifecycle cleanup collaborator rather than mutating registries itself.

#### 3. Principal contract is not fully consistent
- Exact class/file: `RealtimeAuthorizationPolicy` in `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/auth/RealtimeAuthorizationPolicy.java`
- Exact class/file: `RealtimeInboundFrameHandler` and `DefaultRealtimeInboundFrameHandler` in `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/inbound/`
- Exact class/file: `RealtimeAuthorizationPolicyTest` in `chatappBE/common/common-websocket/src/test/java/com/example/common/websocket/auth/RealtimeAuthorizationPolicyTest.java`
- Why it is still a problem: Inbound code says `principal` must never be null and recommends `RealtimeIdentity.anonymous(...)` for anonymous connections. `RealtimeAuthorizationPolicy.allowConnect(...)` says the principal may be null for anonymous, and tests explicitly assert null is accepted or denied depending on the policy.
- Impact: Implementers do not have one principal rule to follow. Connect authorization and inbound authorization use different nullability assumptions, which can produce inconsistent policy code and makes the principal contract unsafe to freeze.
- Recommended fix: Standardize on non-null `RealtimePrincipal` everywhere. Anonymous/system connections should use `RealtimeIdentity.anonymous(...)` or another non-null implementation with `userId() == null`. Update `RealtimeAuthorizationPolicy` docs and tests, and fail fast if an identity resolver returns an invalid principal.

#### 4. Send failure modeling is typed in results but not standardized across observers
- Exact class/file: `RealtimeSendResult` in `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/sender/RealtimeSendResult.java`
- Exact class/file: `RealtimeObserver`, `LoggingRealtimeObserver`, `MicrometerRealtimeObserver`, and `SpringRealtimeMessageSender` in `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/observer/` and `adapter/spring/`
- Why it is still a problem: `RealtimeSendResult` has `RealtimeSendFailureReason`, but `RealtimeObserver.onSendFailure(...)` only receives `Exception cause`. `LoggingRealtimeObserver` logs a generic `SEND_FAILED`, and `MicrometerRealtimeObserver` records an untyped failure. `SpringRealtimeMessageSender` also does not call `onSendFailure(...)` for every failed typed result, such as `SESSION_NOT_FOUND`, `SESSION_CLOSED`, or `SESSION_REPLACED`.
- Impact: Callers can switch on typed send results, but observability and failure callbacks cannot classify failures consistently. This leaves one of the public contracts half-standardized.
- Recommended fix: Change observer contract to `onSendFailure(String sessionId, RealtimeSendFailureReason reason, Exception cause)` or add an overload before freeze. Emit it for every failed send result, including non-exception reasons.

#### 5. Per-session lock lifecycle still has a split-lock edge case
- Exact class/file: `SpringRealtimeConnectionManager` in `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeConnectionManager.java`
- Why it is still a problem: `disconnect(...)` removes `sessionLocks.remove(sessionId)` inside the synchronized block after cleanup. This fixes the old-cleanup-over-new-session case for the common path, but if one reconnect is already waiting on the old lock and another reconnect arrives after the lock entry is removed, the second reconnect can create a new lock and run concurrently with the first reconnect when the first later enters the old lock.
- Impact: Old cleanup should no longer remove a newly registered replacement session, but the stronger invariant "one lock per session ID at a time" is still not guaranteed under waiter contention. That can produce non-obvious ordering between concurrent replacement connects.
- Recommended fix: Use a lock strategy that cannot create two live locks for the same session ID while waiters may exist. The simplest freeze-safe option is a bounded striped lock keyed by session ID or retaining lock entries for the process lifetime. Add a regression test where one reconnect queues behind disconnect cleanup and another reconnect starts after lock removal but before the queued reconnect enters.

### Minor

#### 1. Registry test package layout is inconsistent with file paths
- Exact class/file: `chatappBE/common/common-websocket/src/test/java/com/example/common/websocket/registry/InMemoryRealtimeSessionRegistryTest.java`
- Exact class/file: `chatappBE/common/common-websocket/src/test/java/com/example/common/websocket/registry/InMemoryRealtimeSubscriptionRegistryTest.java`
- Why it is still a problem: These files live under a `registry` test directory, but declare packages `com.example.common.websocket.session` and `com.example.common.websocket.subscription`. Main code has no `registry` package.
- Impact: Main package structure is understandable, but the test tree still preserves an old/mixed registry grouping. This is not a runtime issue, but it is not freeze-clean.
- Recommended fix: Move tests to `src/test/java/com/example/common/websocket/session/` and `src/test/java/com/example/common/websocket/subscription/`, or introduce a real `registry` package consistently. The current main split is preferable.

#### 2. Naming is mostly normalized, but a few non-standard names remain
- Exact class/file: `HandshakeTokenResolver` and `QueryParamHandshakeTokenResolver` in `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/`
- Exact class/file: `DefaultRealtimeBroadcaster` in `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/sender/DefaultRealtimeBroadcaster.java`
- Why it is still a problem: Most public classes use `Realtime` or `SpringRealtime`, but `HandshakeTokenResolver` and `QueryParamHandshakeTokenResolver` do not. `DefaultRealtimeBroadcaster` also has a stale `CHANNEL_GROUP` comment while the enum value is `CHANNEL`.
- Impact: Low runtime impact, but naming should be cleaned before freezing public API names.
- Recommended fix: Rename to `RealtimeHandshakeTokenResolver` and `QueryParamRealtimeHandshakeTokenResolver` if this API is intended to freeze. Update the stale comment to `CHANNEL`.

#### 3. Minor polish remains in source hygiene
- Exact class/file: `InMemoryRealtimeSessionRegistry` and `InMemoryRealtimeSubscriptionRegistry` in `chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/session/` and `subscription/`
- Exact class/file: several tests under `chatappBE/common/common-websocket/src/test/java/com/example/common/websocket/`
- Why it is still a problem: Some classes import types from their own package unnecessarily, and some comments contain mojibake characters from encoding drift.
- Impact: No runtime impact, but it makes the module feel less frozen and less clean.
- Recommended fix: Remove same-package imports and repair comment encoding before freeze.

## 4. Freeze Checklist Status
- lifecycle ownership separated: PARTIAL
- reconnect/disconnect race fixed: PARTIAL
- JWT/security coupling removed from base module: DONE
- principal contract standardized: PARTIAL
- inbound internal-error boundary completed: NOT DONE
- command success contract finalized: DONE
- send failure reasons standardized: PARTIAL
- package structure normalized: PARTIAL
- naming normalized: PARTIAL
- misleading abstractions removed/renamed: PARTIAL
- observer strategy clarified: PARTIAL

## 5. Final Verdict
- Can `common-websocket` be frozen now? No.
- If yes, what should be protected by tests? Not applicable yet. Once fixed, protect: stale disconnect/reconnect cleanup, split-lock reconnect contention, inbound internal-error conversion, sender error-frame best-effort behavior, principal nullability invariants, typed send-failure observer behavior, and public contract shape.
- If no, remaining blockers in priority order:
  1. Complete the inbound/lifecycle internal-error boundary so unexpected common-layer failures always take the safe websocket error path.
  2. Close the lifecycle ownership leak by hiding raw `SpringRealtimeConnectionManager` mutation APIs and moving sender-side registry cleanup behind a lifecycle cleanup component.
  3. Standardize `RealtimePrincipal` nullability across auth, handshake, inbound, and tests.
  4. Finish typed send-failure standardization across observer callbacks and metrics/logging.
  5. Decide and fix the per-session lock lifecycle strategy so same-session operations cannot split across two lock objects under reconnect contention.
  6. Normalize package/test layout and public naming before freezing.

Out of scope: any service integration changes that may be needed to consume these revised contracts.

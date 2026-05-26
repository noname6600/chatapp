## 1. Changed files/classes

- chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeMessageSender.java
- chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/SpringRealtimeLifecycleAdapter.java
- chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/adapter/spring/config/WebSocketAutoConfiguration.java
- chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/observer/RealtimeObserver.java
- chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/inbound/DefaultRealtimeInboundFrameHandler.java
- chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/observer/LoggingRealtimeObserver.java
- chatappBE/common/common-websocket/src/main/java/com/example/common/websocket/observer/MicrometerRealtimeObserver.java
- chatappBE/common/common-websocket/src/test/java/com/example/common/websocket/adapter/spring/SpringRealtimeMessageSenderTest.java
- chatappBE/common/common-websocket/src/test/java/com/example/common/websocket/adapter/spring/SpringRealtimeLifecycleAdapterTest.java
- chatappBE/common/common-websocket/src/test/java/com/example/common/websocket/adapter/spring/SpringRealtimeLifecycleAdapterRaceTest.java
- chatappBE/common/common-websocket/src/test/java/com/example/common/websocket/adapter/spring/config/WebSocketAutoConfigurationTest.java
- chatappBE/common/common-websocket/src/test/java/com/example/common/websocket/inbound/DefaultRealtimeInboundFrameHandlerTest.java
- chatappBE/common/common-websocket/src/test/java/com/example/common/websocket/observer/RealtimeObserverTest.java
- chatappBE/common/common-websocket/src/test/java/com/example/common/websocket/adapter/spring/LoggingSafetyTest.java

## 2. Guarded cleanup design summary

- Added expected-session guarded cleanup in Spring sender.
- Cleanup/unregister can now be keyed by both sessionId and expected physical WebSocketSession.
- Guarded removal uses compare-and-remove semantics so stale physical sessions cannot remove a newer replacement mapping.
- send() dead-session cleanup now always passes the captured physical session object.
- lifecycle disconnect and transport-error cleanup now pass the actual Spring session object.
- replacement physical session close path is separated from global common cleanup so replacing a physical session does not blindly wipe current replacement state.
- cleanup remains idempotent.

## 3. Connect/replace atomicity summary

- Added atomic connect API in sender: connectSpringSession(sessionId, session, commonRegistryMutation).
- lifecycle onConnected now performs sender physical registration and common session-registry mutation under one per-session critical section.
- lifecycle atomic sequence:
  - register/replace physical Spring session
  - unregister stale common session entry for the same sessionId
  - register current common RealtimeSession
  - emit connected observer callback
- This removes split-brain risk where sender points to one physical session while common registry points to another due connect interleaving.

## 4. Handshake auto-config composition summary

- WebSocket auto-configuration now conditionally provides:
  - SpringHandshakeInterceptor
  - SpringRealtimeHandshakeHandler
- Interceptor creation requires all safe prerequisites:
  - HandshakeTokenResolver
  - RealtimeIdentityResolver
  - RealtimeAuthorizationPolicy
  - RealtimeObserver
- No unsafe implicit QueryParamTokenResolver default was introduced.
- Custom interceptor/handler beans still override defaults via ConditionalOnMissingBean.

## 5. Observer safety API change summary

- Removed raw inbound payload strings from default observer callbacks.
- RealtimeObserver now exposes safe metadata callbacks:
  - onFrameReceived(sessionId, payloadLength)
  - onFrameDecoded(sessionId, frameType)
  - onFrameDecodeFailed(sessionId, payloadLength, reason, cause)
- Default inbound handler now reports only metadata and structured reason labels.
- Built-in logging and metrics observers updated to new safe metadata contract.
- Built-in logging continues avoiding credential/token leakage.

## 6. Tests added/updated

- Added race-focused lifecycle integration tests:
  - old physical session late disconnect does not remove replacement
  - old physical session late transport error does not remove replacement
  - concurrent replacement connect converges sender and common registry to one current session
- Added/updated sender tests:
  - stale expected-session cleanup cannot remove replacement
  - replacement during send remains stable with lock-safe ordering
  - stale/failing send recovery converges to current replacement session
- Updated lifecycle adapter unit tests for new atomic and guarded APIs.
- Expanded auto-config tests for handshake conditional creation and custom override behavior.
- Updated inbound/observer/logging safety tests for metadata-only observer callbacks and structured decode failure reporting.

## 7. Validation results

Executed in chatappBE only:

1. .\gradlew.bat :common:common-websocket:test
- Result: BUILD SUCCESSFUL

2. .\gradlew.bat :common:common-websocket:compileJava
- Result: BUILD SUCCESSFUL

3. .\gradlew.bat :common:common-web:test
- Result: BUILD SUCCESSFUL

No service module compile/test commands were run.

## 8. Remaining blockers, if any

- No remaining high freeze blocker from the guarded-cleanup, atomic-lifecycle, handshake-composition, observer-safety, and race-test checklist.
- Optional/non-blocking item still unchanged in this pass: artifact-level Spring-heavy packaging decision (single artifact vs split core+adapter) remains a product/packaging choice.

## 9. Freeze verdict for common-websocket

- Verdict: freeze-ready for the requested blocker scope in common-websocket/common boundary.
- High-risk stale physical-session cleanup regression path is addressed with expected-session guard semantics and race coverage.

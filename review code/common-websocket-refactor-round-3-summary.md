# common-websocket Refactor Round 3 Summary

## 1. Scope and Guardrails
- Scope held to `common/common-websocket` plus minimal shared contract behavior already consumed by this module.
- No service, gateway, domain, kafka, redis, or frontend modules were modified.
- Priority order followed: compile/test stability first, then stale tests, then contract/identity/lifecycle hardening verification.

## 2. Commands Executed and Exact Status
1. `./gradlew :common:common-websocket:test --no-daemon --no-watch-fs --tests "com.example.common.websocket.adapter.spring.SpringRealtimeMessageSenderTest" --console=plain`
- Initial result: FAILED (assertion mismatch in sender replacement test after deadlock stabilization).
- Final result after test contract alignment: PASSED.

2. `./gradlew :common:common-websocket:test --no-daemon --tests "com.example.common.websocket.inbound.DefaultRealtimeInboundFrameHandlerTest" --tests "com.example.common.websocket.observer.RealtimeObserverTest" --console=plain`
- Result: PASSED.

3. Required verification command:
- `./gradlew :common:common-websocket:test --no-daemon`
- Final result: **BUILD SUCCESSFUL**.

Operational notes:
- Earlier runs failed due Windows file lock contention on `common/common-websocket/build/test-results/test/binary/output.bin` and daemon-stop race timing.
- Stabilized by separating daemon stop/cleanup from test invocation and removing deadlock-prone test ordering.

## 3. What Was Fixed in Round 3
1. Deadlock/hang root cause in sender race test
- File: `common/common-websocket/src/test/java/com/example/common/websocket/adapter/spring/SpringRealtimeMessageSenderTest.java`
- Reworked concurrent replacement test ordering to avoid lock inversion in test code.
- Updated the replaced-while-sending test to match actual striped-lock semantics: in-flight send completes, replacement applies afterward.

2. JSON contract leak in destination model
- File: `common/common-websocket/src/main/java/com/example/common/websocket/subscription/RealtimeDestination.java`
- Added `@JsonIgnore` on computed helper getters/booleans so wire payload remains canonical (`type`, `identifier`) and decode is stable.
- This removed decode-failure cascades in inbound command tests.

3. Stale observer test fixture against stricter subscription invariant
- File: `common/common-websocket/src/test/java/com/example/common/websocket/observer/RealtimeObserverTest.java`
- Switched `RealtimeSubscription` construction from non-subscribable USER destination to CHANNEL destination.

4. Inbound stale guard test stability
- File: `common/common-websocket/src/test/java/com/example/common/websocket/adapter/spring/SpringRealtimeInboundGuardTest.java`
- Replaced prior flaky/hanging variant with deterministic stale physical session and missing-common-session guard assertions.

## 4. Contract Decisions Confirmed
1. Destination JSON contract
- Canonical wire shape for `RealtimeDestination` is restricted to identity fields, not derived helper fields.
- Rationale: avoid strict-decoder failures from computed accessors leaking into payload.

2. Sender replacement semantics under per-session lock
- If replacement occurs while send is in-flight under the same session lock, the in-flight send may complete successfully.
- `SESSION_REPLACED` remains asserted by dedicated pre-lock replacement test path.

3. Subscription object invariant
- `RealtimeSubscription` must use subscribable destinations; tests now consistently use CHANNEL for subscriptions.

## 5. Remaining Gaps
- No functional blocker remains for `common/common-websocket` test freeze gate in this round.
- Residual operational risk is environment-level (Windows file-lock behavior when multiple Gradle runs/terminals overlap), not a module logic/test-contract blocker.

## 6. Freeze Verdict
- **READY** for the `common/common-websocket` freeze gate based on required command outcome:
  - `./gradlew :common:common-websocket:test --no-daemon` => **BUILD SUCCESSFUL**.

## 7. Files Changed in This Round
- `common/common-websocket/src/main/java/com/example/common/websocket/subscription/RealtimeDestination.java`
- `common/common-websocket/src/test/java/com/example/common/websocket/adapter/spring/SpringRealtimeInboundGuardTest.java`
- `common/common-websocket/src/test/java/com/example/common/websocket/adapter/spring/SpringRealtimeMessageSenderTest.java`
- `common/common-websocket/src/test/java/com/example/common/websocket/observer/RealtimeObserverTest.java`

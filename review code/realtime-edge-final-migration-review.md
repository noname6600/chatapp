# Realtime Edge Final Migration Review (Phases A-E)

## Scope and Goal

This review provides a final code-level readiness assessment for the Phase A-E realtime-edge migration before full local/staging validation.

Evaluation dimensions covered:
- edge infrastructure completeness
- per-domain migration completeness (notification, presence, chat, friendship)
- cross-domain consistency
- true blockers vs acceptable tradeoffs

## Executive Summary

Migration structure is substantially in place and most edge infrastructure pieces are implemented and testable. However, the codebase is **not yet ready** for full local/staging validation as a complete migrated system.

Three concrete blockers remain:
1. `chat-service` does not compile due to a syntax error in `ChatWebSocketHandler`.
2. Friendship realtime edge ingress is wired to Kafka aggregate topics that are not produced by `friendship-service`.
3. Friendship module has legacy test-compile debt that currently fails `:friendship-service:compileTestJava`.

Because of these blockers, readiness is below gate level for full-system validation.

## Evidence Snapshot

### Build/Test Signals Collected

- `:realtime-edge-service:compileJava :notification-service:compileJava :presence-service:compileJava :friendship-service:compileJava` -> **PASS**
- `:realtime-edge-service:compileJava :notification-service:compileJava :presence-service:compileJava :chat-service:compileJava :friendship-service:compileJava` -> **FAIL** (`chat-service` syntax error)
- `:realtime-edge-service:test --no-daemon` -> **PASS**
- Focused edge/notification/presence test selection (excluding friendship legacy tests) -> **PASS**
- Test selection including friendship controller test target still fails before execution at `:friendship-service:compileTestJava` due to legacy removed/renamed symbol references -> **FAIL**

## Edge Infrastructure Completeness

Status: **Mostly complete**

What is in place:
- Unified ingress endpoint and alias compatibility in edge websocket config (`/realtime` plus `/ws/*` aliases).
- Session registry abstraction with Redis-backed global ownership model.
- Ownership-aware local delivery + cross-instance handoff publish/consume path.
- Redis ingress listeners for chat/notification/presence.
- Domain delivery services for notification, presence, chat, friendship with local+handoff pattern.
- Presence lifecycle bridge for connect/disconnect and snapshot convergence.
- Metrics binders/counters for session and delivery behavior.

Residual infra caveats (not hard blockers by themselves):
- Placeholder generic Kafka consumer remains with `autoStartup=false` (non-active skeleton).
- Some transitional compatibility/legacy routes intentionally retained for rollback safety.

## Per-Domain Migration Completeness

### Notification

Status: **Functionally ready with minor consistency debt**

Positive:
- Edge command routing exists to notification command endpoint.
- Notification command controller supports expected command set.
- Redis notification channel model aligns with edge Redis listener patterns.
- Focused notification controller tests pass.

Debt / observations:
- Notification edge router timeout policy is hardcoded in constructor while chat/friendship/presence use configurable timeout style.
- Router default URL property fallback differs from service-specific consistency conventions (environment can still override).

### Presence

Status: **Ready for validation**

Positive:
- Edge presence command bridge is implemented and integrated in connection lifecycle.
- Presence command ingress controller is present and tested with auth cases.
- Redis ephemeral state + key-expiry handling are implemented.
- Focused presence integration tests pass.

### Chat

Status: **Blocked**

Blocker:
- `chat-service` compile failure due to syntax issue (extra closing brace at file end in `ChatWebSocketHandler.java`).

Impact:
- Full migrated-system validation cannot be executed with chat domain included until compile is restored.

### Friendship

Status: **Partially complete but blocked for end-to-end edge delivery validation**

Positive:
- Edge friendship command router exists and forwards to friendship command API.
- Friendship command controller exists with expected action set.
- Edge has dedicated friendship Kafka consumer and friendship delivery service.

Critical blocker:
- Edge consumer listens to aggregate topics (`friendship.request.events`, `friendship.events`) while friendship producer publishes to topic names equal to event types (for example `friend.request.sent`, `friend.unfriended`).
- No bridging producer/consumer path was found that maps event-type topics into edge aggregate topics.

Result:
- As coded, friendship realtime events will not reliably arrive at edge friendship consumer path.

Test debt blocker:
- Legacy friendship tests fail compile (`:friendship-service:compileTestJava`) due to stale contracts/symbols from removed/renamed classes.

## Cross-Domain Consistency Assessment

Status: **Moderate consistency with known deltas**

Consistent patterns:
- JWT handshake enforcement on websocket ingress paths.
- Edge as command ingress and fanout ownership layer.
- Shared envelope/event semantics used in multiple flows.
- Local + remote ownership/handoff delivery strategy reused by domain delivery services.

Inconsistencies:
- Command routing style differs by domain (chat handled via dedicated parser flow, presence via direct bridge methods, notification/friendship via dispatcher command envelope).
- Timeout configuration parity differs (notification vs chat/friendship/presence).
- Topic contract mismatch in friendship path (aggregate vs event-type topics) is a cross-domain contract break, not merely style drift.

## True Blockers vs Acceptable Tradeoffs

### True Blockers (must fix before full local/staging validation)

1. Chat compile blocker in `chat-service`.
2. Friendship Kafka topic contract mismatch between producer and edge consumer.
3. Friendship legacy test compile debt if the gate requires successful module test-task compilation as part of validation entry criteria.

### Acceptable Tradeoffs (can be deferred)

1. Best-effort realtime semantics (no exactly-once end-to-end guarantee) if explicitly accepted for current phase.
2. Retained legacy websocket endpoints for rollback compatibility.
3. Transitional inactive adapters/skeletons clearly marked and not in active path.
4. Notification router timeout/property-style normalization (consistency hardening, not immediate runtime blocker when env-configured).

## Final Readiness Classification

Readiness for full local/staging validation as a complete migrated system: **NOT READY**.

Reason: At least two concrete runtime-path blockers are present (chat compile failure and friendship edge ingress topic contract mismatch), plus unresolved friendship test-compile debt for strict quality gates.

## Minimal Hardening Needed Before Validation

1. Fix `chat-service` compile error in `ChatWebSocketHandler.java` and re-run compile sweep.
2. Align friendship Kafka contract so edge consumer actually receives friendship events (either consume event-type topics or publish/bridge to aggregate topics consistently).
3. Resolve or quarantine legacy friendship tests so `:friendship-service:compileTestJava` no longer blocks selected validation test tasks.
4. Re-run the same compile/test gate commands and confirm all pass under chosen validation policy.

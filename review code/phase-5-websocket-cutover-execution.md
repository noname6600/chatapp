# Phase 5 - Websocket Cutover Execution

Date: 2026-05-14
Execution source of truth:
- review code/phase-5-websocket-cutover-readiness.md
- review code/service-fix-plan.md

## Decision

Cutover was NOT executed.

## Why Cutover Was Not Executed

The readiness document does state "Conditionally ready with listed safeguards," but the same document explicitly adds a hard guardrail:
- "Cutover should not proceed yet until notification room authorization and route ownership ambiguity are resolved and gateway switch mechanics are explicitly feature-gated and validated."

This means the current safeguards are not yet acceptable for execution because required preconditions remain open.

The service fix plan is consistent with this and requires:
- cutover only after edge authorization parity is validated,
- no gateway cutover in the initial safe batch,
- gateway cutover only after remaining service-side guard closures.

## Code Changes Executed

None.

No gateway websocket routing changes were applied, because applying them now would violate the source-of-truth preconditions.

## Current Routing State (unchanged)

Gateway websocket ingress remains:
- /ws/chat -> chat-service
- /ws/presence -> presence-service
- /ws/friendship -> friendship-service
- /ws/notifications -> notification-service

This preserves rollback safety and avoids premature ownership switch.

## Temporary Compatibility Assumptions (still in effect)

1. Dual-mode websocket coexistence remains intentional during pre-cutover hardening.
2. Domain websocket handlers remain active as rollback-safe paths.
3. Realtime-edge continues to run in readiness/parity mode, not authoritative ingress mode.
4. Notification room authorization and route ownership cleanup remain cutover blockers.

## Preconditions Required Before Re-attempting Cutover

1. Close notification room authorization gap for room mute/settings.
2. Resolve notification room path ownership ambiguity.
3. Implement explicit gateway feature-gated websocket switch with clear rollback toggle.
4. Pass the pre-cutover validation matrix for chat/presence/friendship/notification edge-routed flows.

## Verification Gate

Focused gateway verification gate executed to confirm baseline health around prior gateway hardening:
- command: ./gradlew :gateway-service:test --tests "*DownstreamReadinessIndicatorTest"
- result: see terminal execution from this task run

Note: No code changes were made in this execution because source-of-truth readiness constraints did not permit cutover.

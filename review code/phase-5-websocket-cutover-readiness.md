# Phase 5 - Websocket Cutover Readiness (No Cutover Performed)

Date: 2026-05-14
Execution source of truth: review code/service-fix-plan.md
Scope reviewed: service-side readiness only (gateway-service, chat-service, presence-service, friendship-service, notification-service, realtime-edge-service)

## 1) Readiness Verdict Per Service

### gateway-service
Verdict: Not ready for cutover execution yet (by design in current state).

Current state observed:
- Gateway websocket routes still point to domain services:
  - /ws/chat -> chat-service
  - /ws/presence -> presence-service
  - /ws/friendship -> friendship-service
  - /ws/notifications -> notification-service
- This keeps dual ownership active and correctly defers cutover.

### realtime-edge-service
Verdict: Conditionally ready for ingress authority, pending listed blockers/safeguards.

Current state observed:
- Edge websocket aliases exist for /realtime and legacy /ws/* paths.
- JOIN path for chat is subscription-only (no room membership mutation call on JOIN).
- Subscription authorization for ROOM/PRESENCE/TYPING is enforced through ChannelSubscriptionManager + chat room access check.
- Generic placeholder Kafka/EventDelivery path has been removed/quarantined; false architecture signals reduced.

### chat-service
Verdict: Conditionally ready for edge-authoritative ingress.

Current state observed:
- Room membership guard is used in key cutover-critical paths:
  - local websocket JOIN
  - room member-count / room code / member listing endpoints used by edge auth checks
  - message query and reaction persistence paths
- This is sufficient for edge room subscription authorization dependency in current implementation.

### presence-service
Verdict: Conditionally ready for edge-authoritative ingress.

Current state observed:
- Room membership authorization is enforced for room presence/typing entry points:
  - /api/v1/presence/room/{roomId}
  - edge command controller join/leave/typing/stop-typing
  - local presence websocket room join/typing paths
- Internal ingress auth filter protects /api/v1/presence/ws/** service-to-service boundary.

### friendship-service
Verdict: Ready to remain temporary local websocket path during cutover.

Current state observed:
- Local websocket handler remains available and low-complexity.
- Edge command forwarding endpoint exists (/api/v1/friends/realtime/commands).
- Internal API auth gate is present for internal paths.

### notification-service
Verdict: Not fully ready for clean cutover.

Current state observed:
- Local websocket path remains active and edge command forwarding endpoint exists.
- Room mute/settings operations still appear to trust authenticated user without explicit room membership authorization adapter check against chat.
- Route ownership ambiguity still exists because room settings endpoints are mapped on both:
  - /api/v1/rooms/**
  - /api/v1/notifications/rooms/**
  while gateway already has strong /api/v1/rooms/** ownership under chat-service.

## 2) Remaining Blockers

1. Notification room authorization gap
- Room mute/settings paths do not clearly enforce chat room membership authorization.
- This remains a service-side authorization parity gap versus cutover expectations.

2. Notification route ownership ambiguity
- Notification exposes /api/v1/rooms/** aliases while gateway routes /api/v1/rooms/** to chat-service.
- Ownership ambiguity should be removed or explicitly constrained before cutover to avoid contract confusion.

3. Gateway cutover mechanics not yet defined as an explicit guarded switch
- No cutover feature-flagged route plan is currently active in gateway for websocket ingress to edge.
- Exact switch/rollback knobs should be defined before production cutover.

4. Residual chat room-scoped surface review needed (non-core but prudent)
- Core edge-dependent room checks are present.
- Some room-scoped endpoints appear less explicitly guarded by room membership than the core set; should be revalidated before cutover freeze.

## 3) Is Edge JOIN/Subscription Authorization Sufficient?

Assessment: Sufficient for current cutover-critical flows, with caveats.

Why:
- Chat JOIN in realtime-edge is subscription-only and checks room authorization before subscribing.
- Generic subscribe flow validates channel format and authorization, including ROOM/PRESENCE/TYPING via room membership check against chat-service.

Caveats:
- Authorization quality depends on chat membership check endpoint semantics and availability.
- Access token propagation during websocket handshake remains critical; missing/invalid token correctly fails authorization.

## 4) Is Presence/Chat Room Authorization Sufficient?

Assessment: Largely sufficient for edge cutover-critical paths; not yet a perfect blanket guarantee.

Chat:
- Sufficient in room membership checks required by edge authorization dependency and local websocket JOIN/reaction/query paths.
- Additional room-scoped endpoint review is still recommended before final go/no-go.

Presence:
- Sufficient for room presence and typing flows across local ws and edge command ingress.
- Internal ingress service credential gate is in place for edge command boundary.

## 5) Can Notification/Friendship Local Websocket Paths Remain Temporarily?

Assessment: Yes, they can remain temporarily and should remain during staged cutover.

Reasoning:
- Plan explicitly allows temporary coexistence until edge parity is proven.
- Keeping local websocket paths provides operational rollback safety.
- Friendship and notification local handlers are still functional while edge migration slices run in parallel.

## 6) Exact Steps Required Before Cutover

1. Close notification room authorization gap
- Add explicit room membership authorization adapter check for room mute/settings operations in notification-service.

2. Resolve notification route ambiguity
- Decide and enforce one canonical room mute path for gateway ownership model (prefer notification-owned namespaced path).
- Remove reliance on conflicting /api/v1/rooms/** alias for notification behavior at gateway boundary.

3. Define gateway websocket cutover switch
- Add explicit gateway websocket route switch plan (feature flag/canary route set) to move /ws/* ingress to realtime-edge.
- Keep legacy routes available for immediate rollback during initial rollout window.

4. Execute pre-cutover validation matrix (must pass)
- Chat JOIN/SEND/EDIT/DELETE/REACTION/PIN/UNPIN via edge
- Presence connect/heartbeat/join/leave/typing via edge
- Friendship command forwarding + event fanout
- Notification command forwarding + websocket delivery + room settings behavior
- Unauthorized room subscription attempts (ROOM/PRESENCE/TYPING) must be denied

5. Establish rollout observability guardrails
- Track websocket connect/disconnect rates, subscription deny rates, command forwarding failures, and downstream auth/4xx spikes.
- Define abort thresholds before rollout.

## 7) Rollback Plan

1. Keep domain websocket endpoints active during initial cutover window
- chat-service /ws/chat
- presence-service /ws/presence
- friendship-service /ws/friendship
- notification-service /ws/notifications

2. Route rollback mechanism
- Revert gateway websocket routes from edge back to existing domain ws routes (single config rollback/deploy step).

3. Runtime rollback criteria
- Elevated auth failures on ROOM/PRESENCE/TYPING subscriptions
- Increased command forwarding failures from edge to domain services
- Material drop in successful websocket session establishment

4. Post-rollback stabilization
- Preserve message/presence/friendship/notification domain ownership unchanged.
- Retain edge deployed but non-authoritative until blockers fixed and revalidated.

## 8) Final Recommendation

Final recommendation: Conditionally ready with listed safeguards.

Interpretation:
- Core edge JOIN/subscription authorization and presence/chat room authorization for cutover-critical flows are materially improved and close to cutover shape.
- Cutover should not proceed yet until notification room authorization and route ownership ambiguity are resolved and gateway switch mechanics are explicitly feature-gated and validated.

## Context

The friendship realtime flow depends on a browser WebSocket (`/ws/friendship?token=...`) and backend JWT handshake validation via JWKS from auth-service. In the current failure mode, the frontend repeatedly observes socket `error` and immediate close (`readyState=3`) while backend logs do not expose sufficient handshake rejection detail. Excessive Hibernate SQL logging further obscures transport diagnostics.

## Goals / Non-Goals

**Goals:**
- Make handshake and authentication failures observable and attributable within one debugging cycle.
- Preserve unread friend-request badge correctness even during realtime transport failure.
- Keep realtime behavior preferred-path: HTTP fallback is a consistency safety net, not a replacement for realtime.
- Reduce log noise that impedes diagnosis.

**Non-Goals:**
- Redesigning friendship event payload contracts.
- Replacing WebSocket with polling-only architecture.
- Introducing new infrastructure beyond current services.

## Decisions

- Add structured, stage-based logs for WebSocket lifecycle in frontend and backend.
Rationale: Current `onerror` events in browser provide no root cause; stage logs (connect attempt, handshake received, token decode success/failure, close reason, retry schedule) isolate failing segment quickly.
Alternative considered: packet capture/proxy tooling only. Rejected because this is slower for routine dev workflows.

- Keep token-in-query transport and improve rejection diagnostics around JWT validation.
Rationale: Existing server interceptor already parses token query param. Ensuring explicit rejection reason is least invasive and fastest to stabilize.
Alternative considered: migrate to header-based SockJS/STOMP flow. Rejected as scope expansion.

- Add fallback unread-count synchronization on app init and reconnect windows.
Rationale: Badge correctness must not depend solely on an active socket. This keeps UX correct under degraded realtime.
Alternative considered: no fallback. Rejected because it leaves stale/zero badge state when socket fails.

- Bound reconnection observability with clear retry cadence logs.
Rationale: Infinite silent retries hide state transitions; explicit retry logs make behavior predictable.
Alternative considered: disable retries. Rejected because transient failures would require manual refresh.

- Disable verbose SQL logs in friendship-service runtime profile used for debugging.
Rationale: SQL traces bury handshake logs and slow diagnosis.
Alternative considered: filter at log collector. Rejected for local-dev complexity.

## Risks / Trade-offs

- [Risk] Extra debug logs may expose token fragments in dev logs. → Mitigation: redact to prefix-only and avoid full token output.
- [Risk] HTTP fallback could mask realtime outages if not clearly surfaced. → Mitigation: emit explicit degraded-mode log markers.
- [Risk] Reduced SQL logging may hinder DB debugging during this phase. → Mitigation: keep config toggleable and document how to re-enable.
- [Risk] Retry loops can still produce noise if backend remains down. → Mitigation: include backoff/retry markers and optional cap in future hardening.

## Context

The frontend is configured with `VITE_API_URL=http://localhost:8080/api/v1` and builds service clients from that base, while the gateway currently defines REST predicates primarily as `/api/<service>/**` and forwards request paths without canonical rewrite rules. Several frontend service modules also duplicate path segments (for example base `.../users` + request `/users/me`), producing paths like `/api/v1/users/users/me`. This mismatch causes broad `404` responses after login and then secondary `401`/refresh-noise behavior in bootstrap flows that rely on successful authenticated fetches.

The issue is cross-cutting: gateway routing contract, frontend API path composition, and auth-dependent bootstrap error handling are all involved.

## Goals / Non-Goals

**Goals:**
- Define a canonical REST routing contract for gateway and frontend using `/api/v1/*`
- Ensure forwarded downstream paths match controller mappings (including gateway-only service prefixes that must be stripped)
- Remove duplicated service segments in frontend REST service calls
- Ensure auth-dependent bootstrap fetches fail gracefully when session is invalid/missing

**Non-Goals:**
- Changing WebSocket endpoint topology (`/ws/*`) beyond compatibility checks
- Introducing service discovery, API version negotiation, or new auth protocol
- Refactoring all frontend stores; only bootstrap/session-failure handling paths are in scope

## Decisions

### Decision 1: Canonical external REST prefix is `/api/v1/*`

Gateway route predicates SHALL explicitly accept `/api/v1/...` for all services used by frontend clients. Optional backward-compatibility for `/api/...` may be retained to avoid breaking older consumers.

Alternative considered: changing frontend `VITE_API_URL` back to `/api` and rewriting all frontend APIs. Rejected because existing env contracts and documentation already standardize `/api/v1`.

### Decision 2: Gateway performs path rewrite for gateway-only service labels

For service labels used only as gateway grouping prefixes (for example `/api/v1/chat/**`, `/api/v1/friendship/**`), gateway SHALL rewrite to downstream controller paths (`/api/v1/**`) where needed. This keeps frontend routes semantically grouped while aligning with downstream controllers such as chat (`/api/v1/rooms`, `/api/v1/messages`) and friendship (`/api/v1/friends`).

Alternative considered: frontend directly calling downstream controller paths (`/api/v1/rooms`, `/api/v1/friends`) without service labels. Rejected to preserve existing frontend client structure and avoid broader routing ambiguity.

### Decision 3: Frontend API modules use non-duplicated suffixes

Each API module SHALL either encode service prefix in base URL OR in request path, but never both. Existing duplicated calls (`/users/me`, `/presence/me`, `/notifications`, etc.) will be normalized to avoid accidental double segments.

### Decision 4: Auth bootstrap guards avoid noisy refresh errors

Auth-dependent bootstrap calls in stores/providers SHALL treat missing or invalid session as a controlled state transition, not an unhandled runtime error. This includes avoiding `No refresh token` bubbles into room/friendship/notification logs and ensuring clean logout/session-reset behavior where appropriate.

## Risks / Trade-offs

- [Risk] Supporting both `/api/v1/*` and legacy `/api/*` may temporarily increase route complexity. → Mitigation: document canonical path and add route-level tests/assertions.
- [Risk] Rewrite rules can accidentally over-match and break specific endpoints. → Mitigation: scope rewrites with explicit regex per service and verify critical endpoints (`/users/me`, `/rooms/my`, `/friends`, `/notifications`).
- [Risk] Tightening bootstrap auth handling may hide server-side issues if all errors are treated as session failures. → Mitigation: only normalize auth/session errors (401/403/no-token); preserve explicit logging for non-auth errors (404/5xx).

## Migration Plan

1. Update gateway route predicates and rewrite filters for canonical `/api/v1/*` traffic.
2. Normalize frontend API module endpoint suffixes to avoid duplicate path segments.
3. Add/adjust bootstrap guards in room/friendship/notification flows for auth-missing or auth-expired behavior.
4. Validate with local smoke checks: login success, profile fetch, room preload, friendship unread count, notifications fetch, and websocket connect stability.
5. Rollback plan: restore previous gateway route config and API path composition files, rebuild gateway/frontend, and re-run smoke tests.

## Open Questions

None; required behavior is clear from observed runtime failures and existing contracts.

## Context

Notification mute settings span the frontend notification store, the gateway route table, and notification-service controllers. The current frontend notification API client is rooted at `/api/v1/notifications`, but room mute settings are served by `RoomMuteController` on `/api/v1/rooms/{roomId}/...`, so settings reads and writes are composed onto the wrong path. On the realtime side, the frontend opens `ws://.../ws/notification`, the notification service registers `/ws/notifications`, and the gateway currently exposes no notification websocket route, which causes the browser to reconnect against a broken path.

This is a cross-cutting contract issue rather than an isolated bug in one module. The fix needs to keep notification REST and websocket behavior aligned across frontend, gateway, and notification-service while preserving the existing user-facing mute and realtime notification features.

## Goals / Non-Goals

**Goals:**
- Restore a canonical route contract for room mute settings fetch, mute, and unmute operations.
- Ensure the notification websocket is reachable through the gateway using the same path contract on frontend, gateway, and backend.
- Stop repeated reconnect churn when the notification websocket fails for non-recoverable reasons such as bad route configuration or invalid handshake path.
- Add focused tests and logging so future contract drift is detected quickly.

**Non-Goals:**
- Redesign notification payload formats or notification inbox UI behavior.
- Introduce a new websocket transport, message broker, or backoff strategy shared by all websocket clients.
- Change unrelated chat, presence, or friendship websocket contracts.

## Decisions

### Decision: Keep room mute settings under the room-scoped REST contract
Room mute settings will continue to use room-scoped endpoints (`/api/v1/rooms/{roomId}/settings` and `/api/v1/rooms/{roomId}/mute`) instead of inventing nested `/api/v1/notifications/rooms/...` routes.

Rationale:
- The backend already exposes room mute settings through `RoomMuteController` under `/api/v1/rooms`.
- The existing `room-notification-settings` spec already describes room-scoped mute routes.
- Adjusting the frontend request composition is lower risk than moving controller mappings or broadening gateway route ownership.

Alternatives considered:
- Move notification-service controllers under `/api/v1/notifications/rooms/...`: rejected because it would change an existing contract and spread route churn into backend mappings and tests.
- Add gateway rewrite rules from `/api/v1/notifications/rooms/...` to `/api/v1/rooms/...`: rejected because it hides a frontend composition bug and makes room-scoped routes less explicit.

### Decision: Standardize the notification websocket path on the plural backend route
The notification websocket path will be standardized on `/ws/notifications` and exposed through gateway routing with the same plural form.

Rationale:
- Notification service already registers `/ws/notifications`.
- Aligning frontend and gateway to the implemented backend contract is the smallest coherent fix.
- A single canonical path reduces repeated singular versus plural drift.

Alternatives considered:
- Rename the backend handler to `/ws/notification`: rejected because it changes the server contract for no functional gain.
- Support both singular and plural paths indefinitely: rejected because it adds duplicate routes and makes future drift harder to detect.

### Decision: Treat certain notification websocket failures as non-recoverable for the current session
The notification websocket client will continue retrying after ordinary disconnects, but it will suppress repeated reconnect attempts when failure signals indicate route or handshake misconfiguration rather than transient network loss.

Rationale:
- Current reconnect behavior is acceptable for transient disconnects.
- Reconnecting forever against a broken path creates noisy network spam and obscures the underlying contract bug.
- Notification state already performs reconciliation fetches on successful reconnect, so reducing futile retries does not change the steady-state data model.

Alternatives considered:
- Disable reconnect entirely: rejected because it would weaken realtime resilience for valid transient failures.
- Leave reconnect logic unchanged and rely only on fixing the path: rejected because future route regressions would recreate the same noisy failure mode.

### Decision: Cover the contract with targeted tests at each boundary
Tests will validate frontend notification API path composition, notification websocket endpoint selection and retry suppression, gateway notification websocket routing, and notification-service room mute controller behavior where needed.

Rationale:
- The failure originated from contract drift across modules.
- Boundary tests are the cheapest way to prevent the same drift from returning.

## Risks / Trade-offs

- [Risk] Retry suppression could stop reconnecting after a failure that was actually transient. → Mitigation: classify only clearly non-recoverable failures for suppression and preserve reconnect behavior for ordinary close flows.
- [Risk] Frontend room mute requests may still be routed through the wrong API client base after the initial patch. → Mitigation: add tests that assert the exact request paths for settings, mute, and unmute.
- [Risk] Gateway and frontend websocket paths could diverge again if only one side is updated. → Mitigation: update both the gateway route spec and the frontend websocket integration spec in the same change and add targeted verification.

## Migration Plan

1. Update frontend notification REST calls to use the canonical room mute settings route contract.
2. Add the notification websocket route in gateway and align frontend websocket configuration to the canonical plural path.
3. Add retry suppression and diagnostics for non-recoverable notification websocket failures.
4. Run focused frontend and backend tests covering room settings and notification websocket behavior.
5. Roll back by restoring the prior frontend or gateway path contract if deployment reveals an unforeseen dependency on the broken route.

## Open Questions

- Should the notification websocket expose explicit close-code or log markers for route-mismatch failures to make non-recoverable retry suppression easier to reason about?
- Is any external client outside this repository currently depending on the singular `/ws/notification` path?
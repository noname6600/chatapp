## Context

The add-friend experience currently includes broader realtime handling that is more complex than the requested behavior. Product direction is to keep realtime effects focused on friend request notification signals while ensuring recommendation cards always present an accurate status after user actions.

This change spans friendship-service event scope and frontend add-friend/notification state handling. Existing backend event producers and frontend socket listeners can be reused with narrowed semantics.

## Goals / Non-Goals

**Goals:**
- Restrict add-friend realtime behavior to friend request notification-focused updates.
- Ensure notification badge reflects incoming friend requests in near-real time.
- Provide clear outgoing request feedback so users can see send-request outcome.
- Keep add-friend recommendation statuses consistent with request/friendship lifecycle states.

**Non-Goals:**
- Rebuild the recommendation algorithm.
- Introduce new transport protocols beyond existing HTTP/WebSocket/Kafka pipeline.
- Add unrelated realtime list-reordering or presence behaviors.

## Decisions

1. Reuse existing friend request events and map only notification-relevant events in add-friend context.
Rationale: minimizes backend churn and avoids new event contracts.
Alternative considered: create dedicated add-friend realtime event type; rejected due to extra producer/consumer complexity.

2. Treat recommendation card status as deterministic UI state derived from server response plus accepted realtime deltas.
Rationale: keeps UI predictable and avoids stale action buttons.
Alternative considered: refetch full recommendation list on each event; rejected due to extra network traffic and jank.

3. Keep badge state source centralized in frontend store and update through one handler path.
Rationale: prevents duplicated increment/decrement logic across components.
Alternative considered: local component state updates; rejected because it causes drift between pages.

4. Narrow backend publishing/consumption paths used by add-friend screen to required friend request signals only.
Rationale: enforces requirement to remove broad realtime behavior from add-friend flow.
Alternative considered: frontend filtering only; rejected because unnecessary traffic still flows.

## Risks / Trade-offs

- [Risk] Event semantics differ between backend producers and frontend assumptions. -> Mitigation: document event-to-UI mapping and add integration tests for both incoming and outgoing request cases.
- [Risk] Badge count drift during reconnect windows. -> Mitigation: perform unread-count reconciliation call on reconnect or page initialization.
- [Risk] Recommendation status may lag if event ordering is inconsistent. -> Mitigation: always apply last-write-wins using server timestamps or authoritative response updates.

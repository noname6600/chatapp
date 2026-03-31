## Context

The current presence implementation already maintains websocket sessions, room membership, online snapshots, and room typing events. However, the backend and frontend only expose binary online or offline state, and the frontend store reduces presence to boolean maps. That prevents the product from representing richer states such as away, from letting the user manually choose a status, and from presenting consistent room and friend presence across the UI.

Typing data also exists today, but it is treated as raw membership in a room-level typing map. The user requirement is stricter: the chat room should show a human-readable typing summary above the input, show one person by name when only one person is typing, summarize when many people are typing, and never display the current user as typing.

This change crosses the presence service, websocket event contracts, frontend presence APIs, global and room presence rendering, and chat-room UI placement, so a design document is warranted before implementation.

## Goals / Non-Goals

**Goals:**
- Represent effective user status as `ONLINE`, `AWAY`, or `OFFLINE` across global presence, room presence, and friend surfaces.
- Support a user-controlled presence mode where the user can explicitly set a status, and an automatic mode where status is derived from connectivity and inactivity.
- Expose presence state through presence-service REST endpoints and websocket snapshots so frontend state can stay synchronized without custom per-screen logic.
- Extend the frontend presence store and API layer to consume richer presence payloads and let the signed-in user update their own status mode.
- Define a room typing indicator contract that filters out the current user, shows names for small sets, collapses to a generic message for larger groups, and renders just above the composer.

**Non-Goals:**
- Building a full cross-device last-seen activity history.
- Introducing presence privacy settings or per-room visibility permissions in this change.
- Replacing the existing websocket-based presence transport.
- Redesigning the broader chat layout outside the typing-indicator placement and richer status labels.

## Decisions

### 1. Separate presence mode from effective status
- Decision: store a user presence mode of either `AUTO` or `MANUAL`, and derive an effective status of `ONLINE`, `AWAY`, or `OFFLINE` for all outward-facing APIs.
- Rationale: the user needs both manual control and automatic determination. A separate mode avoids conflating "manual away" with "automatically away because idle" and gives the frontend one stable effective status to render.
- Alternative considered: store only one status field and infer whether it was manual or automatic elsewhere. Rejected because precedence becomes ambiguous and client reconciliation gets brittle.

### 2. Manual override takes precedence until cleared
- Decision: when the user sets a manual status such as `AWAY` or `OFFLINE`, outbound snapshots and room presence payloads use that status until the user switches back to `AUTO`.
- Rationale: explicit user intent should not be immediately overwritten by heartbeat traffic or incidental activity.
- Alternative considered: manual status expires automatically after a short time. Rejected because it makes the UI feel unreliable and is hard to explain.

### 3. Automatic status is derived from websocket connectivity plus recent activity
- Decision: in `AUTO` mode, effective status is derived from connection state and recent user activity. Connected users with recent activity are `ONLINE`; connected but idle users become `AWAY`; disconnected or TTL-expired users are `OFFLINE`.
- Rationale: the current service already has heartbeat and TTL-based online tracking. Extending that model with idle detection is smaller and safer than building a separate activity service.
- Alternative considered: derive automatic status from browser focus alone. Rejected because focus is only available on the active client and does not capture inactivity reliably across tabs or devices.

### 4. Use one normalized presence payload shape across REST and websocket snapshots
- Decision: enrich global, room, and per-user presence responses so they carry structured entries with `userId`, `status`, and enough room/global context for the frontend to normalize them into one store.
- Rationale: the current mix of boolean snapshots and event-specific payload shapes forces per-screen translation. A normalized contract keeps friend rows, room member lists, and chat UI aligned.
- Alternative considered: keep binary websocket events and add a separate REST-only status API. Rejected because the frontend would still need to reconcile two incompatible sources of truth.

### 5. Typing indicator is rendered from store selectors, not directly from websocket payload text
- Decision: the backend continues to publish room typing events, but the frontend computes the display string using store state, current room membership, and current-user filtering.
- Rationale: formatting rules such as one-name display, self-exclusion, and "multiple people are typing" belong in the product UI layer, not in transport payloads.
- Alternative considered: have the server send fully formatted strings. Rejected because it couples backend events to frontend copy and localization.

### 6. Typing state uses expiry-based cleanup in addition to stop-typing events
- Decision: typing state should be cleared by explicit stop-typing events and by short expiry when no refresh arrives.
- Rationale: relying only on stop-typing events leaves stale typing indicators after disconnects or message-send races.
- Alternative considered: keep the current event-only approach. Rejected because it is already vulnerable to stale state.

## Risks / Trade-offs

- [Manual offline while websocket remains connected] -> Backend and frontend may disagree on whether the user should still appear in room membership -> Mitigation: treat manual `OFFLINE` as hidden from online lists and room presence snapshots even if the control channel remains connected.
- [Idle threshold too aggressive or too lax] -> Users may flap between `ONLINE` and `AWAY` or remain incorrectly active -> Mitigation: use a configurable inactivity threshold with a documented default and cover transitions in tests.
- [Richer payload rollout] -> Existing consumers may assume snapshots are arrays of user IDs only -> Mitigation: version the contract within the presence-service change set and update all in-repo consumers together before enabling.
- [Typing expiry cleanup] -> Fast typists on unstable connections may briefly disappear from the indicator -> Mitigation: refresh typing heartbeat while composing and choose a short but tolerant expiry window.

## Migration Plan

1. Add backend presence domain support for presence mode, effective status calculation, richer snapshot payloads, and a status update API.
2. Extend websocket event handling and REST controllers to emit the normalized presence payloads while keeping transport endpoints stable.
3. Update the frontend presence store, API layer, and websocket adapter to consume the richer payloads and expose selectors for friend, room, and typing UI.
4. Update friend rows, room member lists, and chat-room typing indicator placement to use the new selectors and display rules.
5. Validate transitions for auto status, manual overrides, room presence, and typing cleanup with backend and frontend tests.

Rollback:
- Revert the frontend to boolean online rendering and stop using the richer payload fields.
- Disable the manual status API path and fall back to binary online or offline behavior in presence-service.

## Open Questions

- What inactivity timeout should be the default threshold for auto-away status?
- Should manual `OFFLINE` hide the user from all room online lists immediately or only from global friend presence?
- Does the product want a visible "Auto" status option in the UI, or should returning to automatic mode be implicit when the user clears a manual choice?
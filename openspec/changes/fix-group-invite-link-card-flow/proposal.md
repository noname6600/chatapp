## Why

Group invite behavior is currently inconsistent and broken in key paths: invite-card sends can fail validation, invite endpoints can miss routing, and invite actions are split across unrelated UI entries. This causes join friction and prevents a reliable “invite to group” flow from chat and profile surfaces.

## What Changes

- Replace fragmented invite triggers with a single consistent invite-card send flow.
- Remove the separate room-header “Send Invite Card” server-setting style action and make “Invite Members” the source of invite-card sending.
- Add/align backend invite endpoint handling so invite actions do not fall through to static-resource 404 handling.
- Allow ROOM_INVITE-only message payloads to pass message validation without requiring extra text or attachments.
- Update invite cards to present a clear join target using link-style UI with invite code context for one-click join.
- Rename profile popup action from “Invite to server” to “Invite to group”.
- Add hover/flyout group selection on profile popup invite action, listing current user group rooms and sending selected group invite card.

## Capabilities

### New Capabilities
- `group-invite-link-card-flow`: Unified invite-to-group interactions across room header and profile popup, producing joinable invite cards with link/code presentation.

### Modified Capabilities
- `message-sending`: Accept ROOM_INVITE-only block payloads as valid message content and return clear validation behavior for mixed/invalid block shapes.
- `message-block-rendering`: Render ROOM_INVITE cards as actionable join surfaces with link/code-oriented affordances and deterministic unavailable/joined states.

## Impact

- Backend chat-service invite routing and validation pipeline for message blocks.
- Frontend room header, invite member action wiring, user profile popup actions, and invite-card rendering behavior.
- API contract and UI expectations for join-from-invite card actions.
- Regression scope for message send validation, invite endpoint routing, and invite card click-through flow.
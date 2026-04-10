## Why

Users currently have no direct in-chat group invite flow. To invite someone, they must navigate manually or share room info out-of-band, which causes friction and lower conversion into group participation. This change adds a first-class invite card flow so group invites can be sent, viewed, and joined directly from chat.

## What Changes

- Add a new "Send Group Invite" action in FE chat surfaces for eligible group rooms.
- When a user sends an invite, persist and deliver a structured invite-card message payload in chat.
- Render invite-card messages in FE message list with room summary (room name, avatar/icon, optional member count/preview).
- Add "Join Group" action on the invite card for recipients; clicking joins the target room and routes user into that room.
- Add BE API/command flow to validate invite visibility and membership eligibility before allowing join.
- Add idempotent join behavior so repeated clicks do not create duplicate memberships.
- Add clear states on invite cards (joinable, already joined, unavailable/expired) and proper error handling.

## Capabilities

### New Capabilities
- `group-invite-card-flow`: End-to-end invite-card creation, delivery, rendering, and one-click join behavior across FE and BE.

### Modified Capabilities
- `message-sending`: Add structured group-invite card payload support to message creation and persistence/broadcast flow.
- `message-block-rendering`: Add rendering contract for group-invite card blocks in chat timeline.
- `room-join-code-integrity`: Extend room-join flow with invite-card-based join entrypoint and eligibility checks.

## Impact

- Frontend:
  - Chat composer/actions UI (send invite trigger)
  - Message rendering pipeline for structured invite cards
  - Join action state handling and room navigation integration
- Backend:
  - `chat-service` message command/DTO/event shape updates for invite-card payload
  - Room membership/join validation path for invite-card joins
  - Possible updates to shared event/message contracts in common modules
- APIs & Contracts:
  - Message payload schema extension for invite-card metadata
  - Join endpoint behavior/idempotency/error codes for invite-origin joins
- Testing:
  - FE unit/integration tests for card send/render/join states
  - BE service/controller tests for invite validation and idempotent join semantics

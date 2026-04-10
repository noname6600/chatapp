## 1. Backend Invite Validation and Routing

- [x] 1.1 Update message validation pipeline to accept valid ROOM_INVITE-only message payloads as non-empty content.
- [x] 1.2 Enforce ROOM_INVITE required metadata checks (`roomId` required, optional snapshot fields tolerated) with explicit validation errors.
- [x] 1.3 Add or align invite-related API controller mappings so invite requests are resolved by API routes, not static-resource handlers.
- [x] 1.4 Add/adjust backend tests for ROOM_INVITE validation pass/fail and invite endpoint mapping behavior.

## 2. Frontend Invite Entry-Point Consolidation

- [x] 2.1 Remove duplicate room-header “Send Invite Card” action and wire “Invite Members” to dispatch ROOM_INVITE card sends.
- [x] 2.2 Rename profile popup action from “Invite to server” to “Invite to group”.
- [x] 2.3 Implement profile-popup hover/flyout group-room selector sourced from current user’s eligible group rooms.
- [x] 2.4 Dispatch selected group invite from profile popup as ROOM_INVITE card send through existing message send pipeline.

## 3. Invite Card Rendering and Join UX

- [x] 3.1 Update ROOM_INVITE block rendering to show link/code-oriented join context with clear clickable affordance.
- [x] 3.2 Preserve deterministic invite card states (joinable, joined, unavailable) across render and join attempts.
- [x] 3.3 Ensure invalid ROOM_INVITE blocks do not expose clickable join actions.

## 4. Verification and Regression Coverage

- [x] 4.1 Add/update FE tests for consolidated invite actions (room header + profile popup group invite selection).
- [x] 4.2 Add/update FE tests for ROOM_INVITE rendering states and link/code-oriented card semantics.
- [x] 4.3 Add/update BE tests for invite validation and invite endpoint routing behavior.
- [x] 4.4 Run targeted FE and BE test suites for invite send, render, and join flows.
- [x] 4.5 Perform manual E2E check: send invite via room header and via profile popup, then recipient joins successfully from card.
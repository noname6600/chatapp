## 1. Contract and Data Model Updates

- [x] 1.1 Define additive `ROOM_INVITE` message block contract in shared message DTO/types used by FE and BE.
- [x] 1.2 Add backend validation rules for invite-card payload fields (`roomId` required, snapshot metadata optional).
- [x] 1.3 Ensure realtime message/event serialization includes invite-card block without breaking legacy message consumers.

## 2. Backend Invite Send Flow

- [x] 2.1 Extend chat-service message create command to accept and persist `ROOM_INVITE` blocks.
- [x] 2.2 Enforce sender eligibility checks so users can only send invite cards for rooms they can reference.
- [x] 2.3 Return clear validation/authorization errors when invite-card send fails policy checks.

## 3. Backend Join-From-Invite Flow

- [x] 3.1 Add or extend room join endpoint/command for invite-card joins using target `roomId`.
- [x] 3.2 Reuse existing room join policy checks (visibility, permissions, membership constraints) in invite join path.
- [x] 3.3 Implement idempotent join behavior for already-member and repeated/concurrent join requests.
- [x] 3.4 Add integration tests for authorized join, denied join, and idempotent repeated join outcomes.

## 4. Frontend Invite Composer and Send Integration

- [x] 4.1 Add FE chat action to compose and send `ROOM_INVITE` card messages for eligible group rooms.
- [x] 4.2 Wire invite-card payload into existing message send pipeline (including optimistic send path compatibility).
- [x] 4.3 Handle backend invite-send failure states with user-visible error feedback.

## 5. Frontend Invite Card Rendering and Join Action

- [x] 5.1 Add message renderer support for `ROOM_INVITE` blocks with room summary card UI.
- [x] 5.2 Implement invite card join button behavior calling backend join-from-invite flow.
- [x] 5.3 Add deterministic card states for joinable, joined, and unavailable/denied conditions.
- [x] 5.4 Route users to the joined room on successful join action.

## 6. Verification and Regression Coverage

- [x] 6.1 Add FE tests for invite card send payload creation, render fallbacks, and join action state transitions.
- [x] 6.2 Add BE tests for invite send validation and invite join policy enforcement.
- [x] 6.3 Run targeted FE and BE test suites covering message send, block rendering, and room join flows.
- [ ] 6.4 Perform manual end-to-end check: send invite card in chat, recipient clicks join, room entry succeeds.

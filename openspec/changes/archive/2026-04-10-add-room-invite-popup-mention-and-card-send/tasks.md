## 1. Frontend Invite Popup Entry and State

- [x] 1.1 Add room more-menu action to open the invite popup in current room context.
- [x] 1.2 Implement popup state lifecycle (open, close, loading, error) without interrupting current chat timeline state.
- [x] 1.3 Render room code section with copy-to-clipboard action and success/failure feedback.

## 2. Invite Candidate List and Mention-Friendly Identity

- [x] 2.1 Implement invite candidate fetching strategy (friends or eligible users per product rule) scoped to current room.
- [x] 2.2 Render candidate rows with display name, username, and about text when available.
- [x] 2.3 Add search/filter behavior with case-insensitive matching on display name and username.

## 3. Invite Card Message Send Flow

- [x] 3.1 Extend frontend message send model to support invite-card payload type with room context fields.
- [x] 3.2 Wire invite button per candidate row to send invite-card message and show pending/success/failure UI state.
- [x] 3.3 Ensure room timeline renders newly sent invite-card messages consistently with existing message list behavior.

## 4. Backend Message Handling for Invite Cards

- [x] 4.1 Extend chat-service message validation to accept invite-card payloads and reject malformed room context.
- [x] 4.2 Persist and publish invite-card messages through existing message pipeline and websocket/event distribution.
- [x] 4.3 Ensure invite-card join action uses existing room join authorization and join-code semantics.

## 5. Verification and Rollout

- [x] 5.1 Add/adjust frontend tests for popup trigger, room-code copy, candidate search, and invite send button behavior.
- [x] 5.2 Add/adjust backend tests for invite-card payload validation, persistence, and delivery behavior.
- [ ] 5.3 Validate end-to-end manual flow: open popup from room menu, copy code, send invite card, and join from card.

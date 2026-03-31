## 1. Presence Domain and API Contracts

- [x] 1.1 Define backend presence mode and effective status types for automatic and manual presence handling.
- [x] 1.2 Extend presence-service persistence or cache flow to derive `ONLINE`, `AWAY`, and `OFFLINE` from heartbeat, inactivity, and manual override state.
- [x] 1.3 Add or update presence-service REST endpoints so the signed-in user can read and change their presence mode or status.
- [x] 1.4 Extend websocket and snapshot payload contracts for global and room presence so they return structured user presence entries instead of binary user ID sets only.

## 2. Presence Service Behavior

- [x] 2.1 Update websocket session and heartbeat handling so automatic presence transitions between online, away, and offline are emitted correctly.
- [x] 2.2 Ensure manual `AWAY` and manual `OFFLINE` override automatic status until the user returns to automatic mode.
- [x] 2.3 Update room membership and global presence broadcasting so friend and room surfaces receive the effective status expected by the frontend.
- [x] 2.4 Add backend tests for automatic status transitions, manual overrides, room snapshots, and offline cleanup.

## 3. Frontend Presence Data Flow

- [x] 3.1 Add frontend presence API methods and types for reading and updating the signed-in user's presence status or mode.
- [x] 3.2 Refactor the frontend presence store from boolean online maps to normalized rich-status state for global users, room users, and current-user presence preferences.
- [x] 3.3 Update websocket presence handling to normalize rich status payloads into the store without losing room typing behavior.
- [x] 3.4 Add frontend tests for presence-store normalization and API or websocket payload mapping.

## 4. Presence UI Surfaces

- [x] 4.1 Update friend presence UI to render online, away, and offline states using the rich presence data.
- [x] 4.2 Update room member presence UI to render rich presence states from room snapshots instead of binary online dots only.
- [x] 4.3 Add a user-facing control in the frontend for switching between automatic status and manual statuses such as away or offline.
- [x] 4.4 Add UI tests covering friend-row and room-member rendering for online, away, and offline users.

## 5. Room Typing Indicators

- [x] 5.1 Update room typing state management to filter out the signed-in user from typing summaries.
- [x] 5.2 Add typing-summary formatting rules so one typing user shows by name, two to three users show names, and more than three users collapse to a generic multiple-people message.
- [x] 5.3 Move or render the typing indicator in the chat layout so it appears below the message list and above the message input.
- [x] 5.4 Add expiry or cleanup handling so stale typing users are removed when stop-typing, message send, room leave, disconnect, or timeout occurs.

## 6. Validation and Regression Coverage

- [x] 6.1 Add integration coverage for end-to-end presence status synchronization between presence-service and frontend consumers.
- [x] 6.2 Add UI coverage for the room typing indicator display rules and self-typing exclusion.
- [x] 6.3 Run targeted backend presence-service tests and frontend tests for presence and typing flows.
- [ ] 6.4 Manually verify friend presence, room member presence, manual status changes, automatic away transitions, and chat-bottom typing display in a multi-user flow.
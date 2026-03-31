## Why

Room list behavior is inconsistent: private rooms can miss last-message sender context, and room ordering is not consistently based on most recent activity. This degrades chat usability because users cannot quickly identify who sent the latest message or which room is most active.

## What Changes

- **BE contract (minimal):** The `LastMessagePreview` DTO exposes `senderId` only — no `senderName` field on the DTO. Remove any previously-added `senderName` field. Both `buildPreview()` methods in `RoomService` and `RoomQueryService` must not populate `senderName`.
- **FE user cache:** Build a comprehensive user store that caches all relevant users: the current user, all friends, and all users belonging to groups the current user is in. This store is synced from BE data and cached on the FE.
- Preserve current split room-list layout: left column remains group-room avatar rail; right column remains private-message list.
- Frontend resolves sender display name for private-room previews by matching `lastMessage.senderId` against the user cache — guaranteed lookup with no fallback needed.
- This approach is fully consistent for both the initial API load path and the realtime WS path because `ChatMessagePayload` already carries `senderId`.
- Fix frontend ordering for the private-message list (right column) by latest activity while keeping the group avatar rail behavior unchanged.
- Add regression tests for ordering, private-room last-message rendering, and the `senderId → displayName` resolution path.

## Capabilities

### New Capabilities
- `roomlist-lastmessage-order-integrity`: Defines frontend room list requirements for private-room last-message sender visibility and deterministic recency-based ordering.
- `user-store-cache`: FE user cache populated from BE that includes current user, friends, and all group members for reliable sender name resolution.

### Modified Capabilities
- None.

## Impact

- **Backend:** Remove `senderName` from `LastMessagePreview` DTO; remove `.senderName()` calls from both `buildPreview()` methods. No other BE changes.
- Frontend user store: load and cache the current user, all friends, and all group members upfront during app initialization.
- Frontend room list state derivation and rendering logic in chatappFE, with explicit split-layout preservation.
- Frontend preview normalizer resolves sender label by looking up `senderId` in the user cache.
- Frontend websocket/event merge behavior that updates private-room previews and right-panel order.
- Frontend tests for room list sorting and private-room preview metadata using the user-store lookup path.

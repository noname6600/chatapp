## Why

The room list UI lacks collapsible sections and visual polish, making it harder to navigate as conversation counts grow. Separately, a cache-keying bug in profile save causes both the old and new username to appear simultaneously in @mention autocomplete after a user renames themselves.

## What Changes

- Room list gains collapsible section headers ("Group Rooms", "Direct Messages") that persist their open/closed state per user session.
- Group room icons and private room rows receive a visual design refresh (hover states, typography, spacing, activity indicators).
- `ProfileEditor` is fixed to pass the full `UserProfile` (including `accountId`) when calling `updateUserLocal`, so the correct cache entry is overwritten on save.
- The `user-cache` persist store cache TTL for a just-saved profile entry is reset to `Date.now()` so mention autocomplete immediately reflects the new username without a 24 h wait.

## Capabilities

### New Capabilities

- `room-list-collapsible-sections`: Collapsible section headers for group rooms and DMs in the room sidebar, with toggle state persisted in session storage.

### Modified Capabilities

- `message-mention-autocomplete`: The post-username-save path must write to the correct cache key (`accountId`) so mention suggestions show only the current username.

## Impact

- **FE only** — no BE changes required.
- Files affected: `src/components/rooms/RoomList.tsx`, `src/components/rooms/GroupRoomItem.tsx`, `src/components/rooms/PrivateRoomItem.tsx`, `src/components/profile/ProfileEditor.tsx`.
- The `useUserStore` `updateUserLocal` method is unmodified; only the call sites in `ProfileEditor` are fixed.
- No API contract changes.

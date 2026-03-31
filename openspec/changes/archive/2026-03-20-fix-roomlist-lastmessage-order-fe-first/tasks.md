## 1. BE Contract Cleanup

- [x] 1.1 Remove `senderName` field from `LastMessagePreview` DTO in chat-service (revert any previously-added field).
- [x] 1.2 Remove `.senderName()` call from `RoomQueryService.buildPreview()`.
- [x] 1.3 Remove `.senderName()` call from `RoomService.buildPreview()`.
- [x] 1.4 Confirm chat-service Gradle build compiles cleanly after cleanup.

## 2. FE User Cache Population

- [x] 2.1 Create or update the FE user store to cache all relevant users: current user, all friends, and all users in groups the current user belongs to.
- [x] 2.2 Populate the user cache during app initialization (after auth is complete and before room list is rendered).
- [x] 2.3 Add mechanisms to update the user cache when new friendships or group memberships are created/modified.
- [x] 2.4 Confirm user cache is populated and available to the room list normalizer.

## 3. FE Split Layout Preservation

- [x] 3.1 Ensure room list rendering keeps split layout: group avatar rail on the left and private conversation list on the right.
- [x] 3.2 Remove any mixed single-list rendering path that merges group and private rooms into one column.
- [x] 3.3 Confirm group room UI remains avatar-focused and unaffected by private preview changes.

## 4. FE Private Preview Integrity

- [x] 4.1 Update FE normalization to resolve sender display name by looking up `lastMessage.senderId` in the FE user cache — remove any use of `lastMessage.senderName`.
- [x] 4.2 Remove any fallback logic or special cases for the realtime WS path (user cache guarantees lookup will succeed).
- [x] 4.3 Apply the unified `senderId → displayName` resolution consistently for both initial API load and realtime message update flow.

## 5. FE Private Ordering Integrity

- [x] 5.1 Implement stable recency comparator for private rooms with deterministic tie-break fields.
- [x] 5.2 Apply comparator to right-panel private room ordering without changing left-panel group ordering behavior.
- [x] 5.3 Verify there is no private-room ordering drift after realtime updates.

## 6. Regression Coverage and Validation

- [x] 6.1 Add/update unit tests for `senderId → displayName` resolution from the user cache (both matched and unmatched cases).
- [x] 6.2 Add/update unit tests confirming no fallback path exists for realtime events.
- [x] 6.3 Add/update unit tests for right-panel private-room recency ordering and tie cases.
		- [x] 6.4 Run FE test suite and FE build.
- [ ] 6.5 Perform manual smoke checks for split layout and private realtime reorder behavior, then record outcomes.
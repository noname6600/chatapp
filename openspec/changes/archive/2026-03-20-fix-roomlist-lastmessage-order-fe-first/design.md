## Context

The room list in chatappFE currently shows inconsistent preview metadata for private rooms and does not always order rooms by the most recent activity.

Two user-visible failures are in scope:
- Private rooms can miss the sender context for the latest message preview.
- Room list ordering can diverge from true recency when data arrives from mixed sources (initial list load and websocket updates).

Layout constraint from product direction:
- The room list must remain split: left side shows only group-room avatars, right side shows private conversations with preview details.

**BE contract (clarified):**
- The `LastMessagePreview` DTO exposes `senderId` (already present). It does NOT need a `senderName` field.
- If `senderName` was previously added to the DTO, it must be removed.
- Both `buildPreview()` methods (`RoomService` and `RoomQueryService`) must not populate `senderName`.
- `ChatMessagePayload` (WS `MESSAGE_SENT` event) carries `senderId` — so the FE already has what it needs for the realtime path too.

**FE user cache:**
- The FE maintains a comprehensive user cache that is populated during app initialization and updated as needed.
- The cache includes: the current user, all friends of the current user, and all users belonging to groups the current user is in.
- This cache is synced from BE data via existing or new API endpoints and stored in the FE user store.
- To display a sender label in the room list, the FE normalizer looks up `lastMessage.senderId` in this cache — guaranteed to succeed for all users the app needs to display.
- This approach is consistent for both the API load path and the realtime WS path — no special-case fallback needed.

## Goals / Non-Goals

**Goals:**
- Preserve split room-list layout (group avatar rail on the left, private conversation list on the right).
- Ensure private-room previews render latest-message sender context, latest message content, avatar, and name by resolving `senderId` from the FE user store.
- Enforce deterministic room ordering by latest activity timestamp for private rooms in the right panel.
- Normalize room-list derivation so initial API data and websocket updates follow one merge/sort path.
- Add explicit unit tests covering the `senderId → displayName` resolution path.

**Non-Goals:**
- Add `senderName` to `LastMessagePreview` DTO or to the WS `ChatMessagePayload` event.
- Introduce new backend endpoints or change existing endpoint signatures.
- Redesign room list UI/visuals.
- Change unread count/business rules beyond ordering and preview metadata derivation.

## Decisions

### Decision 1: Populate and cache all relevant users on FE
The FE maintains a comprehensive user cache that includes: the current user, all friends, and all users in groups the FE user is in. This cache is populated during app initialization (and updated as new groups/friendships are added) and is then used for all `senderId → displayName` lookups in the room list normalizer.

Rationale:
- Guarantees that `senderId` lookups will always succeed for any user the app needs to display.
- Eliminates the need for fallback logic or special-case handling for missing users.
- Ensures consistent display names across all UI surfaces (room list, group members, etc.).
- Leverages the existing FE caching pattern for other entities.

Alternative considered:
- Lazy-load user details on-demand when a `senderId` is not found in the cache. Rejected because it introduces async complexity and would still need a fallback for race conditions.

### Decision 2: Use a stable recency comparator with deterministic tie-breaker
Sort private rooms by descending `lastActivityAt` for the right panel. Keep group-room avatar rail behavior unchanged. When timestamps are equal or missing, use deterministic tie-breaker (`updatedAt` then room id) to avoid client-side reorder jitter.

Rationale:
- Guarantees predictable rendering order and avoids flicker when data arrives in close succession.

Alternative considered:
- Sort only by `lastMessage.createdAt` with no fallback. Rejected because many rooms may have null/empty last message.

### Decision 3: BE DTO stays `senderId`-only — no `senderName`
The `LastMessagePreview` DTO already has `senderId`. Do NOT add `senderName`. Any previously-added `senderName` field must be removed. FE resolves the display name client-side using the user cache.

Rationale:
- Avoids BE holding denormalized display names that get stale if a user renames themselves.
- FE user cache is the single source of truth for current display names.
- No extra BE work required — the data contract is already correct.

Alternative considered:
- Have BE provide `senderName` from the denormalized `Room.lastMessageSenderName`. Rejected because the FE user cache provides current, reliable display names.

### Decision 4: Single normalizer with cache-based resolution
Use one normalization function to derive `lastMessageSenderDisplay`, `lastActivityAt`, and sort keys from both API room payloads and realtime update payloads. The sender label is resolved by looking up `lastMessage.senderId` in the FE user cache.

Rationale:
- Prevents divergent logic paths that currently produce inconsistent preview data.
- `senderId` is present in both the API response and the WS event, so a single code path covers both.
- With a comprehensive user cache, lookups are guaranteed to succeed.
- Makes ordering and preview behavior testable with pure unit tests.

Alternative considered:
- Keep separate API mapping and websocket mapping logic. Rejected because drift is likely and the user cache unifies both paths.

### Decision 5: Unified resolution path — no special realtime fallback
Since `ChatMessagePayload` already carries `senderId` and the FE has a comprehensive user cache, the same `senderId → displayName` lookup works identically for both API and realtime paths. No fallback label or contract-gap warning is needed.

Rationale:
- Simpler code with no conditional paths.
- FE user cache is always populated before rooms are displayed.
- Eliminates the complexity of different behavior for initial load vs. realtime events.

## Risks / Trade-offs

- [Risk] BE `buildPreview()` in `RoomService` and `RoomQueryService` are separate code paths that must both be updated together. -> Mitigation: both are addressed in this change.
- [Risk] Timestamp field inconsistencies across payloads can still produce incorrect ordering if parsing is not centralized. -> Mitigation: parse timestamps in one utility with strict fallback semantics.
- [Risk] If the user/member for a `senderId` is not yet loaded in the FE store, the sender label will be empty or show a fallback. -> Mitigation: ensure room members are loaded before the room list renders; show a sensible fallback (e.g., empty string) without a blocking error.
- [Risk] Frequent resorting on websocket bursts may impact performance on the private-room panel. -> Mitigation: keep sorting O(n log n), update only changed private rooms, and avoid unnecessary state replacement.

## Migration Plan

1. **BE cleanup:** Remove `senderName` from `LastMessagePreview` DTO if it was added; remove `.senderName()` calls from both `buildPreview()` implementations. Confirm Gradle build is clean.
2. Implement/update FE normalization utilities to resolve sender display name from `senderId` via the user/member store.
3. Remove any special-case fallback for realtime WS events (no fallback needed — `senderId` is sufficient).
4. Restore split layout rendering and apply private-panel recency ordering.
5. Run BE Gradle compile. Run FE tests and FE build.
6. Perform manual room-list sanity checks.

## Open Questions

- Should private rooms without any activity be pinned at bottom of the right panel?
## Context

The chat frontend has two known deficiencies:

1. **Room List Layout**: Group rooms and DM rooms are displayed in separate static columns with no section headers or collapse/expand functionality. All rooms are always visible, which becomes unwieldy as the list grows. Users cannot hide irrelevant sections.

2. **@Mention Stale Cache**: When a user changes their username via `ProfileEditor`, the Zustand `user-cache` store is updated incorrectly. `ProfileEditor.handleSave()` calls `updateUserLocal(draft as any)` where `draft` is `ProfileDraft` — a type that has *no `accountId` field*. The `updateUserLocal` function keys its map on `profile.accountId`, so it writes the updated profile to `users[undefined]`. The existing entry at `users[realUUID]` retains the old username. Both entries later surface in mention autocomplete, causing both old and new usernames to appear simultaneously.

Codebase context:
- `chatappFE/src/components/rooms/RoomList.tsx` — entry point for the left-sidebar room list; renders group icon column + DM list column
- `chatappFE/src/components/rooms/GroupRoomItem.tsx` — a single group room icon button (12×12, tooltip, unread badge)
- `chatappFE/src/components/rooms/PrivateRoomItem.tsx` — a single DM row (avatar, name, last message preview, unread badge)
- `chatappFE/src/components/profile/ProfileEditor.tsx` — profile save handler; owns both `handleSave` and `handleAvatarChange` update paths
- `chatappFE/src/store/user.store.tsx` — Zustand store with `persist` middleware; `updateUserLocal(profile)` writes `users[profile.accountId]`

---

## Goals / Non-Goals

**Goals:**
- Add collapsible section headers ("Groups" and "Direct Messages") to the room list sidebar, defaulting to open
- Persist collapse state across navigation within the session (sessionStorage or component state — no need for server persistence)
- Refine visual styling of both sections for a cleaner, more polished appearance
- Fix `ProfileEditor` to pass a full `UserProfile` object (with `accountId`) when updating the local user cache, so the correct store entry is updated on username/profile change
- Ensure mention autocomplete reflects the current username after a profile save, with no stale duplicates

**Non-Goals:**
- Reordering or sorting of rooms within a section
- Server-side persistence of sidebar collapse state
- Any backend changes — this is a pure frontend fix
- Changing the `updateUserLocal` API signature or the Zustand store internals

---

## Decisions

### 1. Collapsible section state: `useState` (no persistence)

**Decision:** Use React `useState` (defaulting to `true` for both sections open) with no sessionStorage persistence.

**Rationale:**  
- Simple — no additional hook or utility needed
- Collapse is a transient UI preference; re-opening both sections on page reload/navigation is acceptable behavior
- sessionStorage introduces edge cases (stale key drift, SSR concerns) with negligible benefit for a non-critical preference

**Alternative considered:** `useSessionStorage` custom hook — provides persistence across route changes. Rejected: adds complexity for marginal UX gain. Can be added later if users request it.

---

### 2. Section header placement: inline in `RoomList.tsx`

**Decision:** Add section headers and toggle chevrons directly in `RoomList.tsx` rather than extracting a `CollapsibleSection` component.

**Rationale:**  
- Only two sections exist; a shared component would be premature abstraction
- Keeps all collapse logic co-located and immediately readable
- Can be refactored later if a third section is added

---

### 3. Mention cache fix: spread `currentUser` over `draft` at the call site

**Decision:** Fix `ProfileEditor.tsx` at both `updateLocal` call sites — pass `{ ...currentUser, ...draft }` (with `accountId` from `currentUser`) instead of `draft as any`.

**Rationale:**  
- Minimal change, maximal correctness — `currentUser` is already available in the component from `useAuthStore`
- No changes needed to `updateUserLocal` itself; the bug is solely at the call site
- Avoids a type cast (`as any`) that masked the bug

**Alternative considered:** Change `updateUserLocal` to accept a partial and merge with the existing entry. Rejected: introduces new logic in the store and doesn't make the call site intention-revealing.

---

### 4. Visual refresh approach: Tailwind utility class updates only

**Decision:** Improve styling using Tailwind CSS class adjustments within existing component files. No new CSS files, no CSS-in-JS.

**Rationale:**  
- Consistent with existing codebase patterns (all components use Tailwind)
- Fast to implement and easy to diff
- Changes remain component-scoped

---

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| `currentUser` is `null` when `handleSave` fires (e.g., logged out mid-edit) | Guard both `updateLocal` call sites with `if (currentUser)` |
| sessionStorage (if used later) conflicts with same-key usage in other components | Namespace the key: `"roomlist-groups-open"` / `"roomlist-dms-open"` |
| Mention autocomplete still shows stale data if the `users[undefined]` phantom entry is never evicted | Ensure the fix removes the undefined-keyed write. No explicit eviction needed — store TTL will expire it within 24h, and the phantom entry has no `accountId` so it won't match real searches |
| Collapse animation jank if room list is virtualized in the future | Not applicable now; note for future virtualization work |

---

## Migration Plan

1. Fix `ProfileEditor.tsx` — two `updateLocal` call site changes (no new dependencies)
2. Update `RoomList.tsx` — add section state, section headers with toggle chevrons, conditional rendering of section content; apply visual refinements to layout
3. Update `GroupRoomItem.tsx` — style refinements (optional, can be done as part of step 2)
4. Update `PrivateRoomItem.tsx` — style refinements (optional, can be done as part of step 2)

**Rollback:** All changes are frontend-only, no migrations. Rolling back is a git revert or re-deploy of the previous frontend build.

---

## Open Questions

- Should the group room section render as an icon-only left column (current) or switch to a labeled full-width list similar to DMs? (Current design: icon column; proposal keeps this)
- Should unread count badges on collapsed section headers aggregate counts for hidden rooms? (Nice-to-have; not in scope for this change)

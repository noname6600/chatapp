## 1. Mention Cache Fix

- [x] 1.1 Fix `ProfileEditor.tsx` `handleSave`: replace `updateLocal(draft as any)` with `updateLocal({ ...currentUser, username: draft.username.trim(), displayName: draft.displayName, aboutMe: draft.aboutMe, backgroundColor: draft.backgroundColor, avatarUrl: draft.avatarUrl ?? currentUser.avatarUrl })` guarded by `if (currentUser)`
- [x] 1.2 Fix `ProfileEditor.tsx` `handleAvatarChange` (inline handler): replace `updateLocal({ ...draft, avatarUrl } as any)` with `updateLocal({ ...currentUser, ...draft, avatarUrl })` guarded by `if (currentUser)`
- [x] 1.3 Verify that `updateLocal` is typed to accept `UserProfile` (not `ProfileDraft`) — update import if needed

## 2. Room List Section Headers and Collapsible State

- [x] 2.1 Add `groupsOpen` and `dmsOpen` state (`useState(true)`) to `RoomList.tsx`
- [x] 2.2 Add "Groups" section header with chevron toggle button above the group room icon column in `RoomList.tsx`
- [x] 2.3 Add "Direct Messages" section header with chevron toggle button above the DM list in `RoomList.tsx`
- [x] 2.4 Conditionally render group room items only when `groupsOpen` is `true`
- [x] 2.5 Conditionally render DM room items only when `dmsOpen` is `true`
- [x] 2.6 Display room counts in each section header (e.g. `groupRoomIds.length`, `privateRoomIds.length`)

## 3. Room List Visual Refinements

- [x] 3.1 Refine `RoomList.tsx` layout: tighten spacing, add visual separation between sections, ensure section headers are clearly distinct from room items
- [x] 3.2 Refine `GroupRoomItem.tsx`: improve hover background, active ring style, and unread badge placement for a more polished look
- [x] 3.3 Refine `PrivateRoomItem.tsx`: improve hover state, last-message preview truncation, and unread badge styling for consistency

## 4. Verification

- [ ] 4.1 Manually test: change username in settings → open @mention autocomplete → confirm only new username appears (no duplicate old username)
- [ ] 4.2 Manually test: open chat sidebar → both "Groups" and "Direct Messages" sections visible and expanded
- [ ] 4.3 Manually test: click "Groups" header → section collapses; click again → expands
- [ ] 4.4 Manually test: click "Direct Messages" header → section collapses; click again → expands
- [ ] 4.5 Manually test: collapse one section, navigate away and back → section remains collapsed

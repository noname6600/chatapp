## Context

The system has two relevant backend services for this change:
- **user-service** — stores `UserProfile` with `accountId`, `username`, `displayName`, `avatarUrl`. Has `findByUsername(String)` in the repository but no public HTTP endpoint for it.
- **friendship-service** — works purely with UUID pairs. Sends friend requests via `POST /api/v1/friends/request/{userId}`. Has no knowledge of usernames.

The frontend already caches user profiles in the `useUserStore` hook and knows which users are in which rooms via the chat store (loaded when viewing a room or friend list). The same user data will be leveraged for suggestions without a new backend endpoint.

The current user presence status selector lives somewhere on the Friends page; the sidebar's user block shows avatar + name + username with no status affordance.

## Goals / Non-Goals

**Goals:**
- Add `GET /api/v1/users/search?username=` to user-service for exact-match username resolution.
- Redesign the Add Friend tab: search-by-username input (with live preview card) at top; FE-derived suggestion cards (from user store cache) below.
- Move the presence status control into the sidebar user block.

**Non-Goals:**
- Fuzzy/partial username search — exact match only to avoid broad user enumeration.
- Any new backend endpoints for group-mate suggestions.
- Any changes to the friend-request backend flow itself.
- Push notifications for new friend requests in this change.

## Decisions

### Decision 1: Username search lives in user-service, not friendship-service

**Why:** Usernames are user-service data. Friendship-service must not reach into user-service directly (avoids circular-style coupling). The frontend resolves username → UUID via user-service, then sends the request to friendship-service with the UUID. This matches the existing two-call pattern.

**Exact-match only** — partial search would allow enumerating user lists. Exact match + rate-limit is acceptable for an "add friend by username" UX.

**Alternative considered:** Add a username-lookup proxy in friendship-service. Rejected — duplicates user data ownership and adds inter-service HTTP call to the request path.

### Decision 2: Group-mate suggestions derived from frontend user store

The frontend already caches user profiles via `useUserStore` and knows room membership from the chat store when rooms are loaded. Instead of adding a backend endpoint, the Add Friend panel will:
1. Iterate over all group rooms the current user belongs to (available in chat store)
2. Extract all unique user IDs from those rooms' members
3. Bulk-fetch missing profiles (not in cache) via the existing user-service bulk endpoint
4. Filter the list to exclude: current user, existing friends, users with pending outbound requests
5. Render the remaining users (up to 20) as suggestion cards

**Why:** Avoids a new backend query and keeps the suggestion logic close to the UI where it can be debugged and iterated. The data is already fetched and cached; no extra network cost.

**Alternative considered:** Query chat-service for group-mate suggestions. Rejected — adds coupling and a new backend endpoint when the FE already has (or can easily have) this data.

### Decision 3: Sidebar presence status control

The compact pattern: a small colored status dot rendered **overlaid on the bottom-right of the user avatar** in the sidebar (same position as Discord/Slack). Clicking the avatar or dot opens a tiny popover/dropdown with the 3 options (Online / Away / Offline). This is a pure rearrangement of existing UI — the underlying `usePresenceStore` hook and API calls are unchanged.

The Friends page status control is removed after the sidebar one is in place.

### Decision 4: Add Friend panel layout

```
┌─────────────────────────────────────────┐
│  Search by username                      │
│  ┌──────────────────────┐ [Send Request] │
│  │ @username...         │                │
│  └──────────────────────┘                │
│  [Preview card — avatar, name, username] │
├─────────────────────────────────────────┤
│  People you may know                     │
│  ┌────────┐ ┌────────┐ ┌────────┐       │
│  │ Avatar │ │ Avatar │ │ Avatar │  ...   │
│  │  Name  │ │  Name  │ │  Name  │       │
│  │[+ Add] │ │[+ Add] │ │[+ Add] │       │
│  └────────┘ └────────┘ └────────┘       │
```

The search input debounces at 400ms. On a match, a preview card appears showing avatar, display name, and username with a "Send Request" button. On 404, show "No user found with that username". Already-friends and pending requests show a disabled state instead of the button.

## Risks / Trade-offs

- **User enumeration via username search**: Mitigated by exact-match only and existing auth guard. No guest access.
- **Suggestion staleness**: Suggestions are derived from cached room membership data. If the user joins a new room after the panel opens, suggestions won't include people from the new room. Acceptable UX — user can close and re-open the panel.
- **Suggestion overlap with friends**: Filtered client-side. No extra network cost. Slightly wasteful but keeps the logic simple.
- **Sidebar status dot click area**: Small target on mobile. Mitigation: entire avatar area is the click target.

## Migration Plan

1. User-service: add endpoint + test.
2. Frontend API client: add `searchUserByUsername`.
3. `AddFriendPanel.tsx`: redesign with username search and FE-derived suggestions.
4. `Sidebar.tsx`: add status dot + popover on avatar.
5. `FriendsPage.tsx`: remove now-redundant status control.
6. No data migrations needed.

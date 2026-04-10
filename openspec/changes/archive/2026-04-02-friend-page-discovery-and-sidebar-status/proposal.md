## Why

The "Add Friend" experience is too bare: it only has a panel (presumably a text input) with no feedback about who you're attempting to add, and no way to discover people you already share groups with. Additionally, the current user's presence status selector lives deep inside the Friends page — users expect to change their status from the sidebar near their own avatar, like every major chat app. Both issues make the app feel incomplete on its social surface.

There is also a cross-service coupling problem: the friend-service works exclusively with UUID pairs and has no knowledge of usernames. Searching for a friend by typed username requires resolving that username to a UUID via the user-service first, which currently has no public endpoint for username lookup.

## What Changes

- **Backend — user-service**: Add `GET /api/v1/users/search?username=<value>` endpoint that performs an exact-match lookup by username and returns the matching user's public profile (or 404). The repository method `findByUsername` already exists; only the controller endpoint and a route guard are needed.
- **Backend — friendship-service**: No changes needed for the add-by-username flow — the frontend will resolve username → UUID via user-service, then call the existing `POST /api/v1/friends/request/{userId}`.
- **Frontend — Add Friend panel (`AddFriendPanel.tsx`, `FriendsPage.tsx`)**: Redesign the Add tab with two sections: (1) a username search input at the top that resolves the user and previews their profile before sending a request; (2) a "People you may know" section below that derives group-mate suggestions from the already-cached user store (by finding users present in the same group rooms) and renders them as actionable cards.
- **Frontend — Sidebar**: Move the presence status selector (currently rendered on the Friends page) into the `Sidebar.tsx` user profile block, displayed as a compact status-dot or dropdown directly adjacent to the user avatar.

## Capabilities

### New Capabilities
- `user-username-search`: User-service exposes a username search endpoint used by the friend discovery flow.

### Modified Capabilities
- `rich-presence-status`: The status selector/indicator MUST be co-located with the current user avatar in the sidebar, not on the Friends page.

## Impact

- `chatappBE/user-service` — new controller endpoint + security config
- `chatappFE/src/components/friend/AddFriendPanel.tsx` — full redesign
- `chatappFE/src/components/layout/Sidebar.tsx` — add presence status control near user avatar
- `chatappFE/src/api/` — new API client function for username search
- `chatappFE/src/pages/FriendsPage.tsx` — remove standalone status control if present

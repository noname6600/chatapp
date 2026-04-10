## 1. Backend — user-service: username search endpoint

- [x] 1.1 Add `GET /api/v1/users/search?username=` handler in `UserProfileController` that calls `userProfileRepository.findByUsernameIgnoreCase(username)` and returns `UserBasicProfile` or 404
- [x] 1.2 Return HTTP 400 if the `username` query parameter is missing or blank
- [x] 1.3 Register the new route in the Spring Security config to require authentication (same as existing `/api/v1/users/**`)

## 2. Frontend — API clients

- [x] 2.1 Add `searchUserByUsername(username: string): Promise<UserBasicProfile | null>` in the user API module — calls `GET /api/v1/users/search?username=<value>`, returns `null` on 404

## 3. Frontend — Add Friend panel redesign

- [x] 3.1 Add a debounced username search input at the top of `AddFriendPanel.tsx` (400ms debounce, calls `searchUserByUsername`
- [x] 3.2 Render a preview card below the input when a user is found: avatar, display name, @username, and a "Send Request" button
- [x] 3.3 Show "No user found" message when the API returns null (404)
- [x] 3.4 Disable the "Send Request" button and show "Already friends" label when the resolved user is already a friend
- [x] 3.5 Disable the "Send Request" button and show "Request pending" label when an outgoing request already exists for that user
- [x] 3.6 On "Send Request" success, update the button state to "Request pending" without clearing the preview card
- [x] 3.7 Add a "People you may know" section below the search area that derives suggestions from the user store cache: iterate over all group rooms (from chat store), extract unique user IDs, bulk-fetch missing profiles, filter out existing friends and users with pending requests, and render a responsive card grid (max 20 suggestions)
- [x] 3.8 Each suggestion card shows avatar, display name, username, and an "Add" button; clicking "Add" calls the friend request API and flips the button to "Pending" (disabled) in-place

## 4. Frontend — Sidebar: presence status dot on avatar

- [x] 4.1 In `Sidebar.tsx`, import `usePresenceStore` and read the current user's effective status
- [x] 4.2 Overlay a colored status dot on the bottom-right of the current user avatar (green = ONLINE, amber = AWAY, grey = OFFLINE) using absolute positioning within the avatar wrapper
- [x] 4.3 Make the avatar (or dot) clickable; on click, show a small popover/dropdown with three options: "Online", "Away", "Offline"
- [x] 4.4 On option select, call the presence set-status API, update local presence state, and close the popover

## 5. Frontend — Friends page cleanup

- [x] 5.1 Locate and remove the standalone presence status selector/control from `FriendsPage.tsx` (now handled by the sidebar)

## 6. Verification

- [ ] 6.1 Search for own username — preview card shows "Already friends" or own profile with no send option
- [ ] 6.2 Search for a valid non-friend username — preview card appears, "Send Request" sends correctly
- [ ] 6.3 Search for a nonexistent username — "No user found" message shown
- [ ] 6.4 "People you may know" section shows only non-friends from shared group rooms (derived from cached data)
- [ ] 6.5 Clicking "Add" on a suggestion card shows "Pending" on that card and does not reload the list
- [ ] 6.6 Sidebar status dot reflects current user's presence status
- [ ] 6.7 Clicking avatar in sidebar opens status popover; selecting a new status updates the dot color
- [ ] 6.8 Friends page no longer contains a standalone status control

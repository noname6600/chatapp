## 1. Friends Page Shell Refresh

- [x] 1.1 Refactor `src/pages/FriendsPage.tsx` into a settings-style page shell with a stronger header, carded content container, and summary panels for friend counts/status.
- [x] 1.2 Update tab and section presentation so online, all, pending, and add-friend states remain inside the upgraded shell with clearer grouping and spacing.
- [x] 1.3 Bring pending-request and add-friend sections into the same visual language without changing their existing friend-request behavior.

## 2. Friend Card Actions and Direct Chat Entry

- [x] 2.1 Redesign `src/components/friend/FriendRow.tsx` into a richer card/action layout with clearer identity, presence, and right-side primary/secondary controls.
- [x] 2.2 Implement direct-chat launch from the friend row and the explicit right-side chat button by reusing `startPrivateChatApi`, room refresh, chat-store activation, and navigation to `/chat`.
- [x] 2.3 Ensure secondary controls such as more-menu/remove, accept/decline, and cancel do not trigger row-level chat navigation.

## 3. Verification and Regression Coverage

- [x] 3.1 Add or update component tests for the upgraded friend row interaction model, including row click vs. secondary action behavior.
- [x] 3.2 Add or update page-level tests for the friends page shell so the settings-style structure and tab-specific rendering remain stable.
- [x] 3.3 Run targeted frontend tests for friends page, friend row, and any direct-chat launch behavior touched by the change.

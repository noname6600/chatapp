## Why

The friends page currently feels visually disconnected from the rest of the polished account surfaces and still uses a placeholder chat action that does not open a direct conversation. Improving it now closes a noticeable UX gap: friends should be easier to scan, more consistent with the settings page, and immediately actionable.

## What Changes

- Redesign the friends page so it uses a carded, settings-style layout with a clearer page header, summary panels, and more intentional tab/content structure.
- Upgrade friend rows from basic gray strips into richer interactive cards with clearer presence, avatar, identity, and action affordances.
- Make friend items open or start a direct private chat when the row or primary action is clicked, while preserving explicit right-side controls for secondary actions.
- Preserve existing incoming/outgoing request and add-friend flows while presenting them in a more cohesive visual shell.
- Keep the implementation frontend-only by reusing the existing private-chat API and current Zustand chat/room state flows.

## Capabilities

### New Capabilities
- `friend-page-hub`: Covers the friends page layout, settings-style visual structure, friend card interaction model, and direct-chat entry from the friends surface.

### Modified Capabilities
- `discord-ui-features`: Extend the modern light-theme styling requirement so social surfaces such as the friends page align with the visual quality and structure already used by the settings page.

## Impact

- Affected code: `src/pages/FriendsPage.tsx`, `src/components/friend/FriendRow.tsx`, related friend actions/components, and chat launch wiring that uses `startPrivateChatApi`, `useChat`, and navigation to `/chat`.
- Affected UX: friends list browsing, pending requests presentation, and direct-message entry from the friends page.
- APIs: no backend contract changes expected; reuses existing private chat creation endpoint.
- State/stores: touches friend, user, presence, room, and chat state coordination on direct-chat launch.

## Why

When users edit a message, the UI shows an optimistic update (before server confirmation) to provide immediate feedback. However, the optimistic state is incorrectly displaying the wrong user avatar and name for the edited message. This happens across all chat types (one-on-one and group chats), creating a confusing user experience where the message temporarily appears to be from a different user until the server responds.

## What Changes

- Optimistic edit updates will correctly preserve and display the original message author's avatar and user name
- The edited message content updates optimistically, but user metadata (avatar, name) remains consistent from before the edit through the server confirmation
- No changes to the actual message data model or API contracts; this is purely a UI state management fix

## Capabilities

### New Capabilities
`optimistic-edit-correctness`: Ensure that optimistic updates during message editing preserve accurate user metadata (avatar, name) so the UI correctly attributes edited messages to their original authors throughout the editing lifecycle.

### Modified Capabilities
<!-- No existing capability requirements are changing -->

## Impact

- Affects frontend message editing state management (likely in useEdit hook or message store)
- Affects message list rendering during optimistic updates
- Changes how optimistic message objects are constructed
- No API, backend, or message data model changes
- No impact on actual message persistence or WebSocket updates

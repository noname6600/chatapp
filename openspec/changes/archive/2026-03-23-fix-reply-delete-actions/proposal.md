## Why

Reply and delete actions in chat messages are currently unreliable or non-functional in the current rendering/data-flow setup. This blocks core chat workflows and must be fixed quickly to restore expected user interaction behavior.

## What Changes

- Restore working reply action from message item actions to input reply preview and send flow.
- Restore working delete action from message item actions through delete-confirm flow and message removal behavior.
- Align action wiring between `MessageList`, `MessageItem`, and state hooks/stores so item actions do not break during component-boundary refactors.
- Add regression coverage for reply and delete action behavior in message item interactions.

## Capabilities

### New Capabilities
- `message-item-reply-delete-actions`: Reliable end-to-end behavior for reply and delete actions initiated from a single message item.

### Modified Capabilities
- `message-sending`: Clarify reply metadata propagation from selected replied message into message send flow.
- `simplified-message-input`: Clarify interaction contract between reply preview state, send flow, and action-originated reply selection.

## Impact

- Affected frontend chat components and hooks: `MessageItem`, `MessageList`, reply/delete hooks, and related action handlers.
- Affected message input reply preview and clear/send interaction path.
- Affected tests for message item action behavior and message lifecycle updates after delete.

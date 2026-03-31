## Why

The current chat message rendering flow mixes list-level orchestration and item-level UI concerns, which increases coupling and makes pagination, grouping, and reply behavior harder to reason about and test. Clarifying responsibility boundaries now reduces regression risk while ongoing message and pagination changes continue.

## What Changes

- Define and enforce a list-versus-item responsibility contract for chat rendering.
- Refactor `MessageList` to own room/list-wide orchestration concerns: room initialization, pagination triggers, scroll lifecycle, grouping state, and item-level derivations.
- Refactor `MessageItem` to focus on rendering and local interaction for a single message.
- Introduce smaller message UI subcomponents for clearer separation of rendering concerns.
- Reduce cross-component prop leakage by passing only single-item data from list to item.

## Capabilities

### New Capabilities
- `chat-message-rendering-boundaries`: Defines required ownership boundaries between `MessageList`, `MessageItem`, and message-item subcomponents.

### Modified Capabilities
- `message-grouping`: Clarify that grouping decisions are list-level responsibilities and item rendering consumes precomputed grouping context.
- `scroll-load-older-messages`: Clarify that pagination and scroll-trigger decisions remain list-level and are not delegated to single-item components.

## Impact

- Affected frontend chat components: `MessageList`, `MessageItem`, and new item subcomponents.
- Affected data flow and props between list and item components.
- Affected tests for grouping, pagination behavior, and item rendering boundaries.

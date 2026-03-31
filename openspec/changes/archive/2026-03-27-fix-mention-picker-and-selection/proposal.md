## Why

The current mention experience still has critical gaps:
- Mention selection can still fail send-path reliability in some runtime cases.
- Mentions may render with raw typed token instead of a consistent `@DisplayName` label.
- Highlight behavior is not differentiated between self-mentions and other mentions.
- Mention text in message list is not consistently interactive to open user profile popup.
- Username availability from bulk user fetch can be inconsistent, degrading suggestion identity rendering.

## What Changes

- Keep mention suggestions bounded to 5 items and searchable by both display name and username.
- Ensure mention insertion always serializes a canonical mention target (userId + username + displayName) and remains sendable.
- Render mentions in chat content as `@DisplayName` regardless of original typed token.
- Make mention tokens clickable in message list to open user profile popup (avatar/name metadata).
- Apply mention highlight policy:
	- If current user is mentioned, highlight the full message item.
	- If another user is mentioned, highlight only the mention token.
- Ensure bulk user fetch includes username consistently so right-column suggestion identity is stable.

## Capabilities

### New Capabilities
- `message-mention-autocomplete`: Reliable mention search, selection, and deterministic mention metadata.
- `message-mention-rendering`: Consistent `@DisplayName` rendering, token interactivity, and mention-aware highlighting.

### Modified Capabilities
- `message-sending`: Preserve valid send behavior when message content includes selected mentions.

## Impact

- Frontend composer mention query/filter/selection and mention metadata persistence.
- Message list rendering path for plain text and structured blocks.
- Mention click interaction and user profile popup trigger wiring.
- Mention highlight decision logic for self-vs-other mentions.
- User bulk profile contract alignment so username is available for mention UI.
- Unit/integration/manual validation for mention send, rendering, highlight, and popup behavior.

## Why

Currently, edited messages display the "edited at" timestamp inconsistently depending on the message structure, and the UI doesn't properly handle inline edits or mentions within edited blocks. Users need clearer visual distinction between simple text edits (which should show the timestamp inline) and complex block edits (which should show the timestamp on a new line). Additionally, empty blocks create unwanted spacing, and edited text lacks mention support, reducing consistency with normal message composition.

## What Changes

- **Inline timestamp display**: For single-line text edits, show "edited at" timestamp inline on the same block instead of on a new line
- **Block-edit timestamps**: For multi-block edits, show "edited at" timestamp on a new line with proper formatting
- **Empty block handling**: Remove unnecessary spacing when a block is added but contains no content
- **Mention support in edits**: Enable @mentions in edited text blocks, matching the mention resolution and rendering behavior of normal message composition

## Capabilities

### New Capabilities
- `inline-edit-timestamp-display`: Show "edited at" timestamp inline for single-block text edits vs. on new line for multi-block edits
- `edit-text-mentions`: Support @mentions in edited message text blocks with proper user resolution and UI rendering

### Modified Capabilities
- `message-block-rendering`: Modify how message blocks are rendered to handle empty blocks without adding unwanted spacing
- `message-edit-flow`: Update edit flow to properly track whether edit is single-line vs. multi-block

## Impact

- **Frontend Components**: MessageContent, MessageBlocks, MessageItem, BlockMessageEditor
- **Data Model**: No schema changes; timestamp behavior is display-only
- **APIs**: No breaking changes; edit API already supports blocks
- **User Experience**: Cleaner UI for edited messages, better consistency with normal composition features

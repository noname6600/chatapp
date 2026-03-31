## Why

The current chat UI is functional but lacks the polish and features users expect from modern chat applications like Discord. Users need a richer experience with message reactions, replies, file previews, rich text editing, and real-time presence indicators.

## What Changes

- **Rich Message Editor**: Support markdown, code blocks, and @mentions with TipTap
- **Emoji Reactions**: Pick and display emoji reactions on messages (like Discord)
- **Message Replies**: Reply to specific messages with visual linkage
- **File Previews**: Show image/document previews before sending
- **Typing Indicators**: See who is typing in real-time
- **Online Status**: Visual indicators showing user presence (online/away/offline)
- **Modern Light Theme**: Complete UI overhaul with modern components
- **Message Actions**: Edit and delete messages with confirmation UI
- **User Mentions**: @mention users with autocomplete

## Capabilities

### New Capabilities
- `rich-message-editor`: Support markdown, code blocks, @mentions in message input
- `emoji-reactions`: Add/remove emoji reactions to messages with real-time sync
- `message-replies`: Reply to specific messages with visual quotes
- `file-upload-preview`: Preview images/files before sending
- `typing-indicators`: Show "User is typing..." indicator
- `user-presence-display`: Show online status next to usernames
- `modern-light-ui`: Complete UI redesign with modern components and light theme
- `message-editing`: Edit and delete messages with backend sync
- `user-mention-autocomplete`: @mention users with autocomplete suggestions

## Impact

- **Affected areas**: All chat components (MessageList, MessageInput, RoomList, UserCards)
- **New dependencies**: TipTap (rich editor), Emoji Mart (emoji picker), Radix UI (headless components)
- **Backend integration**: Uses existing API endpoints (already support reactions, edits, deletes)
- **Real-time**: Leverages existing WebSocket for typing indicators and presence
- **UI Theme**: Shift from current styling to modern light theme

## Non-Breaking

- All existing features continue to work
- No changes to API contracts
- Backward compatible with current data structures

## ADDED Requirements

### Requirement: Rich message editor with markdown support
The message input field SHALL support formatting (bold, italic, code, code blocks) and markdown syntax. Users SHALL be able to format text without leaving the input field.

#### Scenario: Format text
- **WHEN** user selects text and clicks bold button
- **THEN** text is wrapped in markdown syntax (`**text**`)
- **THEN** preview shows formatted text

#### Scenario: Insert code block
- **WHEN** user types triple backticks ` ``` ` and hits enter
- **THEN** code block editor opens with language selector
- **THEN** syntax highlighting applies to code

#### Scenario: Markdown paste
- **WHEN** user pastes markdown-formatted text
- **THEN** formatting is automatically rendered

### Requirement: Emoji reactions on messages
Users SHALL be able to add emoji reactions to any message. Reactions are displayed below the message with counts and users' names on hover.

#### Scenario: Add emoji reaction
- **WHEN** user clicks emoji picker icon on message
- **THEN** emoji picker opens (recent + popular + search)
- **WHEN** user selects emoji
- **THEN** emoji appears below message with count = 1
- **THEN** other users see reaction appear in real-time

#### Scenario: Remove own reaction
- **WHEN** user clicks emoji they already reacted with
- **THEN** reaction is removed from message
- **THEN** count decreases OR emoji disappears if count = 0

#### Scenario: View who reacted
- **WHEN** user hovers over reaction emoji
- **THEN** tooltip shows "You, John, Sarah reacted with 👍"

### Requirement: Reply to specific messages
Users SHALL be able to reply to a message, creating a visual link between reply and original message.

#### Scenario: Reply to message
- **WHEN** user right-clicks message and selects "Reply"
- **THEN** original message quote appears in input area
- **WHEN** user types and sends message
- **THEN** reply has `replyToMessageId` pointing to original

#### Scenario: View reply context
- **WHEN** user sees reply message in list
- **THEN** original quoted message shows above (with sender name, partial content)
- **WHEN** user clicks quoted message
- **THEN** scroll to original message in list

#### Scenario: Deleted reply target
- **WHEN** original message is deleted but reply exists
- **THEN** quote shows "(deleted message)" instead of content

### Requirement: File upload preview
Before sending, users SHALL see preview of images and files being uploaded.

#### Scenario: Image preview
- **WHEN** user selects image file to upload
- **THEN** image thumbnail preview shows in input area
- **THEN** send button is enabled
- **WHEN** user clicks 'x' on preview
- **THEN** file is removed and input clears

#### Scenario: Multiple files
- **WHEN** user uploads multiple images
- **THEN** carousel shows all previews
- **THEN** left/right arrows navigate previews

### Requirement: Typing indicators
Users SHALL see when other users are typing in the room.

#### Scenario: Show typing indicator
- **WHEN** user types in message input
- **THEN** "User is typing..." indicator shows below message input
- **WHEN** user stops typing for 3 seconds
- **THEN** indicator disappears

#### Scenario: Multiple users typing
- **WHEN** 2+ users type simultaneously
- **THEN** indicator shows "John and Sarah are typing..."

### Requirement: User presence display
Users SHALL see online status indicators next to usernames and in room member lists.

#### Scenario: Online status dot
- **WHEN** viewing any user's profile or room member list
- **THEN** colored dot appears next to name: green (online), yellow (away), gray (offline)

#### Scenario: Status update
- **WHEN** user changes status via presence service
- **THEN** dots update in real-time for all viewing users

### Requirement: Message editing
Users SHALL be able to edit sent messages and see "(edited)" label.

#### Scenario: Edit message
- **WHEN** user hovers over their own message
- **THEN** edit button appears
- **WHEN** user clicks edit
- **THEN** message content loads in input with editor
- **WHEN** user saves changes
- **THEN** message updates and shows "(edited)" timestamp

#### Scenario: View edit history
- **WHEN** user hovers over "(edited)" label
- **THEN** tooltip shows original send time

### Requirement: Message deletion
Users SHALL be able to delete their messages with confirmation.

#### Scenario: Delete message
- **WHEN** user right-clicks own message
- **THEN** context menu with "Delete" option appears
- **WHEN** user clicks delete
- **THEN** confirmation dialog appears
- **WHEN** user confirms
- **THEN** message is deleted and replaced with "(deleted message)"

### Requirement: User mention with autocomplete
Users SHALL be able to mention other users with @username, with autocomplete suggestions.

#### Scenario: Mention autocomplete
- **WHEN** user types @ in message input
- **THEN** autocomplete dropdown appears with list of users in room
- **WHEN** user types characters
- **THEN** list filters to matching users
- **WHEN** user clicks user or presses enter
- **THEN** @username is inserted and highlighted

#### Scenario: Mentioned user notification
- **WHEN** message with @username is sent
- **THEN** mentioned user receives notification (name highlighted in message)

### Requirement: Modern light theme styling
All components SHALL use a modern light color scheme with consistent spacing and typography.

#### Scenario: Color scheme
- **WHEN** viewing any chat component
- **THEN** background is light gray (#f5f5f5) for main area
- **THEN** message bubbles are white with subtle shadow
- **THEN** accent color is blue for buttons and links
- **THEN** text is dark gray (#1a1a1a)

#### Scenario: Component polish
- **WHEN** hovering over interactive elements
- **THEN** smooth hover effects appear (slight color change, shadow)
- **WHEN** opening modals or menus
- **THEN** fade-in animation occurs

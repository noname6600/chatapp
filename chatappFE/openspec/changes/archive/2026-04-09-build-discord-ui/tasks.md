## 0. Setup & Dependencies

- [ ] 0.1 Install TipTap editor library and extensions
- [ ] 0.2 Install Emoji Mart for emoji picker
- [ ] 0.3 Install Radix UI components (dialog, dropdown, context-menu, popover)
- [ ] 0.4 Install Framer Motion for animations
- [ ] 0.5 Install React Hot Toast for notifications
- [ ] 0.6 Install Highlight.js for code syntax highlighting
- [ ] 0.7 Update Tailwind config with light theme colors (optional dark mode)
- [ ] 0.8 Create theme configuration file (colors, spacing, typography)

## 1. Foundation Components (Light Theme)

- [ ] 1.1 Create Button.tsx component (light theme variants)
- [ ] 1.2 Create Card.tsx component (white background, subtle shadow)
- [ ] 1.3 Create Avatar.tsx component with online status indicator
- [ ] 1.4 Create Badge.tsx for reaction counts and status labels
- [ ] 1.5 Create UserPresenceIndicator.tsx (online/away/offline dot)
- [ ] 1.6 Create Tooltip.tsx wrapper (Radix-based)
- [ ] 1.7 Create Dialog.tsx wrapper (Radix-based, light theme)
- [ ] 1.8 Create ContextMenu.tsx wrapper (Radix-based)

## 2. Rich Message Editor

- [ ] 2.1 Create RichEditor.tsx with TipTap integration
- [ ] 2.2 Add bold, italic, code formatting buttons
- [ ] 2.3 Add code block support with language selector
- [ ] 2.4 Add list formatting (bullet, numbered)
- [ ] 2.5 Add quoted text formatting
- [ ] 2.6 Add markdown syntax highlighting in editor
- [ ] 2.7 Create Editor Toolbar.tsx with formatting buttons
- [ ] 2.8 Add keyboard shortcuts (Ctrl+B, Ctrl+I, Ctrl+K, Ctrl+Enter to send)

## 3. Emoji Reactions

- [ ] 3.1 Create EmojiPicker.tsx wrapping Emoji Mart
- [ ] 3.2 Add recent emojis tracking (localStorage or store)
- [ ] 3.3 Create ReactionDisplay.tsx showing reactions below message
- [ ] 3.4 Implement addReaction hook (calls API)
- [ ] 3.5 Implement removeReaction hook (calls API)
- [ ] 3.6 Update chat store with reaction actions
- [ ] 3.7 Add WebSocket listeners for reaction events
- [ ] 3.8 Create ReactionTooltip.tsx showing who reacted

## 4. Message Replies

- [ ] 4.1 Create ReplyPreview.tsx showing quoted message
- [ ] 4.2 Add reply context menu to MessageItem
- [ ] 4.3 Implement setReplyMessage action in chat store
- [ ] 4.4 Update MessageInput.tsx to show reply preview
- [ ] 4.5 Add clearReply button to input
- [ ] 4.6 Update sendMessage to include replyToMessageId
- [ ] 4.7 Update MessageItem to show reply context (link to original)
- [ ] 4.8 Handle deleted reply targets (show "(deleted message)")

## 5. File Upload Preview

- [ ] 5.1 Create FileUploadInput.tsx with drag-and-drop
- [ ] 5.2 Create ImagePreview.tsx for image thumbnails
- [ ] 5.3 Create FilePreviewCarousel.tsx for multiple files
- [ ] 5.4 Add file validation (size, type)
- [ ] 5.5 Implement image compression before upload
- [ ] 5.6 Display attachment info (name, size, type)
- [ ] 5.7 Add remove file button in preview
- [ ] 5.8 Update sendMessage to handle attachments

## 6. Typing Indicators

- [ ] 6.1 Create TypingIndicator.tsx component
- [ ] 6.2 Add typing event sender (on input change)
- [ ] 6.3 Add typing event debouncing (3 second threshold)
- [ ] 6.4 Update presence store with typingUsers
- [ ] 6.5 Add WebSocket listener for userTyping events
- [ ] 6.6 Display "X is typing..." below message input
- [ ] 6.7 Handle multiple users typing ("X and Y are typing...")
- [ ] 6.8 Auto-clear typing indicator on message send

## 7. User Presence Display

- [ ] 7.1 Update Avatar.tsx to show presence indicator
- [ ] 7.2 Update RoomMemberList to show all members with status
- [ ] 7.3 Create UserProfileCard.tsx showing full status info
- [ ] 7.4 Add presence color constants (online=green, away=yellow, offline=gray)
- [ ] 7.5 Connect to existing presence store
- [ ] 7.6 Display last seen time for offline users
- [ ] 7.7 Real-time status updates from presence service
- [ ] 7.8 Persist presence indicators across reloads

## 8. Message Editing

- [ ] 8.1 Update MessageItem.tsx with edit button on hover
- [ ] 8.2 Create EditMessageInput.tsx (RichEditor in edit mode)
- [ ] 8.3 Implement editMessage hook (calls API)
- [ ] 8.4 Update chat store with editMessage action
- [ ] 8.5 Add "(edited)" label to edited messages
- [ ] 8.6 Add edit timestamp to tooltip
- [ ] 8.7 Add WebSocket listener for messageEdited events
- [ ] 8.8 Handle concurrent edits (optimistic + real-time)

## 9. Message Deletion

- [ ] 9.1 Update MessageItem.tsx with delete button on hover
- [ ] 9.2 Create DeleteConfirmDialog.tsx
- [ ] 9.3 Implement deleteMessage hook (calls API)
- [ ] 9.4 Update chat store with deleteMessage action
- [ ] 9.5 Add WebSocket listener for messageDeleted events
- [ ] 9.6 Show "(deleted message)" placeholder
- [ ] 9.7 Hide delete button for messages > 2 days old (optional)
- [ ] 9.8 Handle delete cascade (if reply target is deleted)

## 10. User Mention Autocomplete

- [ ] 10.1 Create UserMentionMenu.tsx autocomplete component
- [ ] 10.2 Add @ trigger detection in RichEditor
- [ ] 10.3 Fetch room members on @ trigger
- [ ] 10.4 Implement mention filtering on text input
- [ ] 10.5 Highlight selected mention
- [ ] 10.6 Insert @username on selection
- [ ] 10.7 Add @username styling in rendered messages
- [ ] 10.8 Send mention notification to user

## 11. Message List & Item Redesign

- [ ] 11.1 Update MessageList.tsx styling for light theme
- [ ] 11.2 Update MessageItem.tsx with new layout
- [ ] 11.3 Add message hover effects (show actions)
- [ ] 11.4 Implement message grouping by sender
- [ ] 11.5 Add consistent spacing and typography
- [ ] 11.6 Create MessageActions.tsx (edit, delete, reply buttons)
- [ ] 11.7 Style timestamps with light theme colors
- [ ] 11.8 Handle message loading states

## 12. Chat Input & Room Header Redesign

- [ ] 12.1 Update MessageInput.tsx layout for RichEditor
- [ ] 12.2 Update RoomHeader.tsx with light theme
- [ ] 12.3 Add member count to room header
- [ ] 12.4 Create room settings dropdown
- [ ] 12.5 Style send button with hover effects
- [ ] 12.6 Add file upload icon with tooltip
- [ ] 12.7 Add emoji picker icon with tooltip
- [ ] 12.8 Handle input placeholder text

## 13. Room List & Sidebar Redesign

- [ ] 13.1 Update RoomList.tsx styling for light theme
- [ ] 13.2 Update RoomItem.tsx showing unread count
- [ ] 13.3 Add room avatar display
- [ ] 13.4 Show last message preview
- [ ] 13.5 Highlight active room
- [ ] 13.6 Add hover effects to room items
- [ ] 13.7 Create room context menu (settings, leave)
- [ ] 13.8 Display online friend count

## 14. Friend List & Presence Redesign

- [ ] 14.1 Update FriendList.tsx styling
- [ ] 14.2 Add online status indicator to each friend
- [ ] 14.3 Show "last seen" for offline friends
- [ ] 14.4 Add quick chat button to friend items
- [ ] 14.5 Sort friends (online first)
- [ ] 14.6 Create friend context menu (remove, block)
- [ ] 14.7 Style friend requests section
- [ ] 14.8 Add friend request counter

## 15. Animations & Polish

- [ ] 15.1 Add fade-in animation to modals
- [ ] 15.2 Add slide-in animation to sidebars
- [ ] 15.3 Add scale animation to buttons on hover
- [ ] 15.4 Add smooth transition to typing indicator
- [ ] 15.5 Add emoji reaction pop animation
- [ ] 15.6 Add toast notifications for actions (sent, edited, deleted)
- [ ] 15.7 Add loading spinner while fetching messages
- [ ] 15.8 Add skeleton loaders for message list

## 16. Testing & Validation

- [ ] 16.1 Test rich editor markdown rendering
- [ ] 16.2 Test emoji reactions add/remove/sync
- [ ] 16.3 Test message replies (send, display, cascade delete)
- [ ] 16.4 Test file upload preview (image, multiple files)
- [ ] 16.5 Test typing indicator (single + multiple users)
- [ ] 16.6 Test presence indicators (real-time updates)
- [ ] 16.7 Test message edit (optimistic + sync)
- [ ] 16.8 Test message delete with confirmation
- [ ] 16.9 Test @mention autocomplete and notifications
- [ ] 16.10 Test light theme consistency across components

## 17. Finalization

- [ ] 17.1 Review all components for accessibility
- [ ] 17.2 Ensure responsive design (mobile, tablet, desktop)
- [ ] 17.3 Optimize bundle size (lazy load emoji picker)
- [ ] 17.4 Test with real backend (end-to-end)
- [ ] 17.5 Create user documentation for new features
- [ ] 17.6 Performance optimization (memoization, virtualization)
- [ ] 17.7 Browser compatibility testing
- [ ] 17.8 Final visual review and refinements

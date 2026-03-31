## 1. Component Foundation

- [x] 1.1 Create `UnreadMessageIndicator.tsx` component with sticky positioning
- [x] 1.2 Add base styling with Tailwind for banner (background, padding, border)
- [x] 1.3 Implement unread count display text (e.g., "5 unread messages")
- [x] 1.4 Add "Jump to Latest" button placeholder to banner
- [x] 1.5 Implement show/hide logic based on `roomUnreadCount > 0`

## 2. Room Store Integration

- [x] 2.1 Create `jumpToLatestMessage()` action in room store to expose scroll ref
- [x] 2.2 Update room store to expose `lastMessage` ref for scroll targeting
- [x] 2.3 Ensure `roomUnreadCount` is properly tracked and updated on WebSocket events
- [x] 2.4 Verify `roomUnreadCount` recalculates on page reload from API response

## 3. Message List Integration

- [x] 3.1 Create ref for last message element in message list
- [x] 3.2 Integrate `UnreadMessageIndicator` component below room header in message view
- [x] 3.3 Wire banner's unread count prop from room store `roomUnreadCount`
- [x] 3.4 Pass last message ref to banner component for jump-to-latest targeting

## 4. Jump-to-Latest Button Behavior

- [x] 4.1 Implement click handler for "Jump to Latest" button
- [x] 4.2 Add `scrollIntoView({ behavior: 'smooth', block: 'nearest' })` on last message ref
- [x] 4.3 Test button click on message list with various viewport states
- [x] 4.4 Ensure smooth scroll doesn't interfere with subsequent message arrivals

## 5. Real-Time Updates

- [x] 5.1 Verify WebSocket `NewMessageEvent` handler increments `roomUnreadCount` in store
- [x] 5.2 Test that banner text updates immediately when new message arrives
- [x] 5.3 Verify new messages append to bottom and jump-to-latest targets them correctly
- [x] 5.4 Test unread count with rapid message arrivals (no race conditions)

## 6. Auto-Hide and Persistence

- [x] 6.1 Implement auto-hide logic when `roomUnreadCount <= 0`
- [x] 6.2 Verify banner hides after `markRoomRead` API call completes
- [x] 6.3 Test that opening a previously-read room does not show banner
- [x] 6.4 Verify unread state persists across page reload (backend `lastReadSeq`)

## 7. Accessibility & Styling

- [x] 7.1 Add ARIA labels: `aria-label="Unread messages"` on banner container
- [x] 7.2 Add `aria-label="Jump to latest messages"` to button
- [x] 7.3 Ensure button is keyboard focusable (tabindex=0 or native button)
- [x] 7.4 Add hover and focus CSS states for button (outline/background change)
- [x] 7.5 Add active/pressed state CSS for button click feedback
- [x] 7.6 Test with screen reader (NVDA/JAWS) to verify announcements

## 8. Testing & Validation

- [x] 8.1 Unit test: Banner shows when `unreadCount > 0`, hides when === 0
- [x] 8.2 Unit test: `jumpToLatestMessage()` action is callable from store
- [x] 8.3 Integration test: Clicking button scrolls to last message
- [x] 8.4 Integration test: New WebSocket message updates banner count
- [ ] 8.5 E2E smoke test: Open room with unread messages, verify banner and button work
- [ ] 8.6 E2E smoke test: Click jump button, verify scroll to bottom
- [ ] 8.7 E2E smoke test: Page reload preserves unread state from backend

## 9. Deployment & Documentation

- [ ] 9.1 Update FE README with new component location and usage
- [ ] 9.2 Visual design review (colors, spacing, alignment with design system)
- [ ] 9.3 Cross-browser testing (Chrome, Firefox, Safari, Edge)
- [ ] 9.4 Responsive design check (mobile, tablet, desktop viewports)
- [ ] 9.5 Final code review and merge to main

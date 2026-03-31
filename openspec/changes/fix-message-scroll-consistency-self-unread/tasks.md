## 1. Scroll Detection and Infrastructure

- [x] 1.1 Add `isAtBottom()` utility helper to ChatMessageList that checks if `scrollTop + clientHeight >= scrollHeight - 50`
- [x] 1.2 Add `batchScrollToBottom()` utility that batches scroll triggers within 100ms, with debounce to prevent animation stutter
- [x] 1.3 Create `useScrollToBottom()` hook that encapsulates scroll logic with 50px threshold and smooth scroll behavior
- [x] 1.4 Add ref to ChatMessageList for scroll container to enable scroll position queries
- [x] 1.5 Test scroll detection across viewport sizes and message count scenarios

## 2. Auto-Scroll on New Message Events

- [x] 2.1 Modify `chat.store.tsx` MESSAGE_SENT handler to call scroll-to-bottom when new message arrives and user is at bottom
- [x] 2.2 Modify `chat.store.tsx` to exclude self-sent messages from triggering unread count increment in realtime handler
- [ ] 2.3 Modify `room.store.tsx` MESSAGE_SENT handler to scroll to bottom if at bottom (integration with room list updates)
- [x] 2.4 Test that scroll respects user's reading position (does not scroll if user scrolled up)
- [ ] 2.5 Test that optimistic messages trigger scroll immediately before server confirmation
- [ ] 2.6 Test rapid message batching scrolls only once (no animation jank)

## 3. Self-Message Unread Exclusion in notification.store

- [x] 3.1 Retrieve `currentUserId` from auth context in notification.store
- [x] 3.2 Modify `countUnreadNotifications()` to filter out notifications where `notification.senderId === currentUserId`
- [x] 3.3 Modify realtime MESSAGE_SENT handler in notification.store to NOT increment unread if message is from current user
- [x] 3.4 Update notification reconciliation to exclude self-messages when recalculating from server snapshot
- [x] 3.5 Test that sending a notification-triggering message (own mention? unlikely) does not increment own unread count
- [x] 3.6 Test that unread badge only shows for messages from other users

## 4. Self-Message Unread Exclusion in room.store

- [x] 4.1 Retrieve `currentUserId` from auth context in room.store (VERIFIED: userId available via useAuth)
- [x] 4.2 Modify MESSAGE_SENT handler to check if `event.senderId === currentUserId` before incrementing unread count (VERIFIED: already implemented)
- [x] 4.3 Modify room list unread count display to reflect backend calculation that excludes self messages (VERIFIED: isSender check prevents increment)
- [ ] 4.4 Modify "behind-latest" count calculation to exclude self-messages from the count derivation
- [ ] 4.5 Ensure room last-message preview correctly reflects latest message without unread bias on self-messages
- [x] 4.6 Test that sending message to a room while viewing room list does not increment that room's unread count

## 5. Feature Flag Integration

- [x] 5.1 Create feature flag `enableAutoScrollOnNewMessage` in config
- [x] 5.2 Create feature flag `enableSelfMessageUnreadExclusion` in config
- [x] 5.3 Wrap scroll logic in `if (config.enableAutoScrollOnNewMessage)` guard (IMPLEMENTED in MessageList)
- [x] 5.4 Wrap self-message filtering in `if (config.enableSelfMessageUnreadExclusion)` guard (IMPLEMENTED in room.store)
- [x] 5.5 Ensure feature flags can be toggled safely without breaking existing behavior when disabled

## 6. Unit Tests

- [ ] 6.1 Create test: auto-scroll triggers when new message arrives and user is at bottom
- [x] 6.2 Create test: auto-scroll does NOT trigger when user is scrolled up (reading old messages)
- [ ] 6.3 Create test: optimistic message triggers immediate scroll before confirmation
- [x] 6.4 Create test: rapid messages batch into single scroll
- [x] 6.5 Create test: self-sent message does NOT increment unread count in notification.store
- [x] 6.6 Create test: other-user message DOES increment unread count in notification.store
- [x] 6.7 Create test: self-sent message does NOT increment unread count in room.store
- [x] 6.8 Create test: other-user message DOES increment unread count in room.store
- [ ] 6.9 Create test: behind-latest count excludes self-messages

## 7. Integration Testing

- [ ] 7.1 Test end-to-end: send message from bottom of room, observe smooth scroll and no unread increment on own message
- [ ] 7.2 Test end-to-end: user scrolled up, other user sends message, observe new message in list but no auto-scroll
- [ ] 7.3 Test end-to-end: user sends message while scrolled up, observe message appears and scroll triggers to show it
- [ ] 7.4 Test end-to-end: receive message from another user, observe auto-scroll if at bottom
- [ ] 7.5 Test room list syncronization: send message to room, verify room list unread count unchanged for sender

## 8. Accessibility and UX

- [ ] 8.1 Verify smooth scroll respects `prefers-reduced-motion: reduce` media query (instant scroll instead of animation)
- [ ] 8.2 Ensure keyboard navigation during scroll animation does not break focus management
- [ ] 8.3 Test screen reader announcements for unread banner (count should reflect only unread from others)
- [ ] 8.4 Test that "Jump to Latest" button in unread banner uses same scroll logic and is consistent

## 9. Quality Assurance and Documentation

- [ ] 9.1 Review scroll behavior across multiple browsers (Chrome, Firefox, Safari, Edge)
- [ ] 9.2 Test auto-scroll on mobile browsers (iOS Safari, Chrome Android)
- [ ] 9.3 Test auto-scroll with large message lists (1000+ messages) for performance
- [ ] 9.4 Verify no console errors or warnings during scroll and unread updates
- [ ] 9.5 Document scroll threshold (50px) and batching window (100ms) in code comments
- [ ] 9.6 Update CHANGELOG with new features: auto-scroll and self-message unread exclusion

## 10. Deployment and Validation

- [ ] 10.1 Enable feature flags for auto-scroll in staging environment
- [ ] 10.2 Enable feature flags for self-message unread exclusion in staging environment
- [ ] 10.3 Monitor error logs and performance metrics in staging for 24 hours
- [ ] 10.4 Perform manual smoke testing in staging: send message, receive message, verify scroll and unread behavior
- [ ] 10.5 Enable feature flags for production (or phased rollout if preferred)
- [ ] 10.6 Monitor production error logs and user feedback post-launch

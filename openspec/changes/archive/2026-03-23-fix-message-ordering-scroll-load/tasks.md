## 1. Core Message Sorting

- [x] 1.1 Ensure chat store maintains messages in ascending sequence order on load
- [x] 1.2 Update setActiveRoom() in chat.store to sort fetched messages by seq before storing
- [x] 1.3 Ensure websocket MESSAGE_SENT events are appended, not inserted at arbitrary positions
- [x] 1.4 Verify message array is never reversed during render or state updates

## 2. Scroll-to-Top Older Message Loading

- [x] 2.1 Update MessageList scroll handler to detect scroll position within 60px of top
- [x] 2.2 Implement loadingRef flag to prevent duplicate fetch requests during concurrent scrolls
- [x] 2.3 Call getMessagesBefore(roomId, oldestSeq) when scroll-to-top is triggered
- [x] 2.4 Prepend fetched older messages to the message array in correct order
- [x] 2.5 Check hasMore flag and stop fetching when backend returns empty page

## 3. Pagination State Management

- [x] 3.1 Update oldestSeqByRoom[roomId] after successful prepend of older messages
- [x] 3.2 Filter duplicate messages before prepending to avoid list pollution
- [x] 3.3 Handle edge case where websocket events arrive during fetch-in-progress
- [x] 3.4 Log pagination boundaries and detect gaps in sequence numbers for debugging

## 4. Scroll Position Restoration

- [x] 4.1 Capture scroll height and container height before prepending messages
- [x] 4.2 After prepend DOM updates, calculate new scroll offset to restore reading position
- [x] 4.3 Apply scrollTop adjustment using requestAnimationFrame for smooth restoration
- [x] 4.4 Handle edge case where scroll happens rapidly during restoration; defer or cancel restoration

## 5. Message Grouping Compatibility

- [x] 5.1 Verify groupMessages() function works correctly with ascending chronological order
- [x] 5.2 Ensure grouping logic respects time window and sender identity regardless of sort order
- [x] 5.3 Confirm attachment messages are never grouped with text messages
- [x] 5.4 Validate that grouping output maintains chronological order of groups top-to-bottom

## 6. Unit and Integration Tests

- [x] 6.1 Unit test: message array is sorted ascending by seq after setActiveRoom()
- [x] 6.2 Unit test: prepending older messages maintains ascending order
- [x] 6.3 Unit test: duplicate messages are filtered before prepend
- [x] 6.4 Integration test: scroll-to-top triggers fetch and prepends messages correctly
- [x] 6.5 Integration test: scroll position is restored after prepend
- [x] 6.6 Integration test: hasMore flag stops fetch when backend returns no messages
- [x] 6.7 Integration test: websocket MESSAGE_SENT during fetch-in-progress doesn't break state

## 7. Manual Validation

- [ ] 7.1 Smoke test: Open room, verify messages display oldest to newest (top to bottom)
- [ ] 7.2 Smoke test: Scroll to top and verify older messages load and prepend correctly
- [ ] 7.3 Smoke test: Scroll position is maintained after prepend (user doesn't jump to top)
- [ ] 7.4 Smoke test: Scroll to top multiple times, verify pagination doesn't duplicate messages
- [ ] 7.5 Smoke test: Message grouping renders correctly with new ordering
- [ ] 7.6 Smoke test: New websocket messages append to bottom while viewing room
- [ ] 7.7 Smoke test: Quickly switching between rooms and scrolling doesn't cause message display issues

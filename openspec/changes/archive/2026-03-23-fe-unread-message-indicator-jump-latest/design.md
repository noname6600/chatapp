## Context

The chat application now has a working mark-as-read system:
- Backend endpoint: `POST /api/v1/rooms/{roomId}/read` persists `room_members.lastReadSeq`
- Room list API returns `unreadCount = lastSeq - lastReadSeq` for each room
- Frontend room store tracks `lastReadSeq` per room

Currently, when users open a message list, they see all messages but have no visual cue about which messages are new since they last visited. The unread count exists in the room list (unread badge) but not in the message view itself.

The message list component currently displays messages in chronological order without any unread-boundary indicator or navigation aid for jumping to the latest message.

## Goals / Non-Goals

**Goals:**
- Display a visual indicator banner when entering a room with unread messages
- Show the exact count of unread messages (e.g., "5 unread messages")
- Provide a prominent button to jump directly to the most recent message
- Auto-hide the indicator when all messages have been read
- Update the indicator in real-time as new messages arrive
- Persist the read state across page reloads (via backend)

**Non-Goals:**
- Highlight individual unread messages with a visual marker (just the banner and jump button)
- Create a "scroll to first unread" feature (jump to latest, not first unread)
- Modify notification system or room list badge behavior
- Add server-side unread message filtering or pagination

## Decisions

### 1. Banner Component Placement
**Decision**: Render `UnreadMessageIndicator` as a sticky component positioned above the message list (below the room header).

**Rationale**: 
- Sticky positioning keeps it visible while scrolling, ensuring users always see the unread count
- Positioning above the list makes the CTA (Jump button) easy to discover
- Clear separation from message content

**Alternatives Considered**:
- Floating button in corner: Less discoverable, more intrusive
- Unread line in message list: Can't provide persistent actionable UI

### 2. Unread Count Calculation  
**Decision**: Use `roomUnreadCount` from the room store (derived from API: `lastSeq - lastReadSeq`).

**Rationale**:
- Already computed server-side, consistent with room list badge
- No need to recalculate on client
- Single source of truth from room state

**Alternatives Considered**:
- Count unread messages locally: Would require filtering message array; breaks if messages arrive out-of-order
- Calculate from `lastMessage.seq`: Doesn't account for deleted messages; fragile

### 3. Jump-to-Latest Scroll Behavior
**Decision**: Scroll to the last message in the array using `scrollIntoView({ behavior: 'smooth', block: 'nearest' })` on the last message ref.

**Rationale**:
- Native browser API, smooth and accessible
- `block: 'nearest'` prevents jumping to top if last message is already visible
- Works with virtual scrolling if implemented later

**Alternatives Considered**:
- Scroll to a specific sequence number: Requires mapping seq → DOM element; fragile
- Instant scroll: Less pleasant UX

### 4. Auto-Hide Trigger
**Decision**: Hide the indicator when `roomUnreadCount <= 0` (recomputed when room store updates or messages arrive).

**Rationale**:
- Simple, declarative logic
- Automatically triggered on `markRoomRead` API call
- Updates in real-time as new messages are fetched

**Alternatives Considered**:
- Hide on scroll to last message: User might not scroll that far; flaky
- Manual dismiss button: Require user action; clutter

### 5. Real-Time Updates
**Decision**: Use WebSocket `NewMessageEvent` to update `roomUnreadCount` in store; banner re-renders automatically.

**Rationale**:
- Messages already arrive via WebSocket
- Room store already subscribes to `NewMessageEvent`
- No additional subscriptions needed

## Risks / Trade-offs

**[Risk]** Indicator might persist after user scrolls to bottom without marking read.
→ **Mitigation**: Mark room read automatically when room opens (already implemented in `ChatPageLayout`). Clarify in copy that "read" means "visited the room."

**[Risk]** If the room's `lastSeq` changes (new message arrives) after user clicks jump button, the scroll target changes.
→ **Mitigation**: Expected behavior; new messages should append to bottom. Jump button targets the current last message at click time, which remains valid.

**[Risk]** Virtual scrolling library (if added) might not work with `scrollIntoView` on arbitrary refs.
→ **Mitigation**: Current implementation uses native DOM; switch to library-specific scroll if virtualizing. Document this as a future consideration.

**[Risk]** Unread count from API might lag if WebSocket message arrives before API fetch completes.
→ **Mitigation**: Room store optimistically increments unread count on `NewMessageEvent`. API fetch on room open will sync if needed.

## Migration Plan

1. **Create spec files** for `message-unread-indicator` and `jump-to-latest-action`
2. **Build `UnreadMessageIndicator` component** in `src/components/message/UnreadMessageIndicator.tsx`
3. **Update `MessageListView`** to integrate the banner below room header
4. **Add store action** to expose `jumpToLatestMessage()` for the banner button click handler
5. **Test**: Manual smoke test opening a room with unread count, clicking jump, sending new message
6. **Deploy**: No breaking changes; feature is purely additive

## Open Questions

- Should the indicator show "5 new messages" or "5 unread"? (Proposed: "5 unread")
- Should clicking jump to latest automatically mark the room read? (Proposed: No, let the existing logic in `ChatPageLayout` handle it)
- Do we need a visual line/divider in the message list at the unread boundary? (Proposed: Out of scope; just the banner for now)

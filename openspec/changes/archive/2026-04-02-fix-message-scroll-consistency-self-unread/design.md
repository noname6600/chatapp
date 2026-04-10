## Context

The chat application currently scrolls the message list inconsistently when:
1. User sends a message while reading older messages (unclear if and when to jump to the new message)
2. New messages arrive via WebSocket (may not scroll smoothly, creating jarring visual updates)
3. Unread count is calculated, incorrectly including the current user's own sent messages

The message list component maintains scroll position via a ref. WebSocket event handlers in `chat.store` and `room.store` process MESSAGE_SENT and other realtime events. Unread counts are calculated in `room.store` and `notification.store`.

Current state:
- Scroll position is restored after prepending older messages (scroll-position-restoration spec)
- Jump-to-latest button triggers explicit scroll on user click
- Unread count is a server-side number synced to the frontend, but frontend realtime handlers don't filter for self-messages

## Goals / Non-Goals

**Goals:**
- Auto-scroll the message list to the bottom when a new message (sent or received) arrives, with smooth animation
- Ensure scroll behavior is deterministic: always follow the latest message when new content appears
- Exclude messages from the current user from all unread count calculations, preventing false unread badges
- Maintain scroll position when user is actively reading old messages (don't force scroll if they're intentionally above latest)
- Provide accessibility: users can still navigate and use keyboard controls during scroll animation

**Non-Goals:**
- Changing the server-side unread calculation logic (backend continues to send unreadCount; frontend filters on display)
- Persisting scroll position preference or user-configurable scroll behavior
- Adding animation settings or duration customization
- Modifying message ordering or sequence guarantees

## Decisions

### Decision 1: Scroll Detection (Presence at Bottom)
**Approach**: Detect if user is "at bottom" by checking if `scrollTop + clientHeight >= scrollHeight - threshold`. Use 50px threshold to account for rounding.

**Rationale**: Cannot rely on scrollHeight equality due to browser rounding. Threshold allows minor scroll slack without triggering false positives.

**Alternatives**:
- Intersection Observer: Watches if last message element is visible. Downside: requires DOM queries.
- Current approach preferred: simpler ref-based calculation, no DOM traversal.

### Decision 2: When to Auto-Scroll
**Approach**: Auto-scroll smoothly to bottom ONLY when user is currently at the bottom (within threshold). If user has scrolled up, do not auto-scroll; let them stay in their reading context.

**Rationale**: Respects user intent. If they're reading old messages, auto-scrolling would disrupt them. Only when they're caught up should new messages automatically appear.

**Alternatives**:
- Always auto-scroll: loses user's reading context.
- Only scroll on user's own send: ignores incoming messages from others.

### Decision 3: Self-Message Filtering Location
**Approach**: Filter self-messages at the calculation point:
- **notification.store**: In `countUnreadNotifications()`, exclude notifications where `notification.senderId === currentUserId`
- **room.store**: In the MESSAGE_SENT event handler and room unread calculation, compare `event.senderId === currentUserId` before incrementing unread

**Rationale**: Centralized filtering at the source prevents unread inflation. Current user ID is available in stores via auth context.

**Alternatives**:
- Server-side filtering: Server already calculates unreadCount correctly; frontend would only need to not override it with realtime increments. Downside: less control, requires backend changes.

### Decision 4: Smooth Scroll Implementation
**Approach**: Use `Element.scrollTo({ top: scrollHeight, behavior: 'smooth' })` for native smooth scrolling.

**Rationale**: Native API, no external dependencies. Supported in modern browsers.

**Alternatives**:
- Manual animation loop: more control but more complex.
- CSS scroll-behavior property: applies globally, less targeted.

### Decision 5: Scroll Timing in Message Send Flow
**Approach**:
1. When `message` action is dispatched in `message.store`, immediately scroll to bottom
2. When MESSAGE_SENT event arrives from WebSocket, check "at bottom" before scroll
3. When new MESSAGE_SENT is processed in `chat.store`, trigger scroll if at bottom

**Rationale**: Optimistic scroll gives instant feedback. Realtime scroll respects user's reading position.

**Alternatives**:
- Defer scroll until server confirmation: delays visual feedback for send.

### Decision 6: currentUserId Availability
**Approach**: Assume `auth.store` or context provides `currentUserId`. Pass or access via hook/context in stores.

**Rationale**: Auth context is already used throughout app for user identification.

## Risks / Trade-offs

**[Risk]** Browser scroll behavior varies (momentum/inertia scrolling on mobile)
→ **Mitigation**: Test on iOS/Android. smooth behavior fallback to auto if not supported.

**[Risk]** Rapid message arrival (e.g., bulk import or spam) could trigger rapid scroll animations, causing jank
→ **Mitigation**: Batch scroll triggers within 100ms; only scroll once per batch.

**[Risk]** Self-message filtering breaks if `senderId` field is missing from WebSocket event
→ **Mitigation**: Guard with `senderId !== undefined` check; assume others' message if missing.

**[Risk]** User preference: some users may prefer not to auto-scroll (reading slower, distracted by motion)
→ **Mitigation**: Consider accessibility setting in future; for now, feature is standard. Document in UI/support.

**[Trade-off]** Auto-scroll only at bottom vs. always auto-scroll
→ Chosen: only at bottom. Trade-off: new message from others won't pull attention if user is reading old messages. Benefit: respects user's intent.

## Migration Plan

1. **Phase 1**: Deploy scroll detection and smooth scroll logic to ChatMessageList component (feature flagged)
2. **Phase 2**: Deploy self-message filtering to room.store and notification.store (feature flagged)
3. **Phase 3**: Test with real users; collect scroll behavior and unread count feedback
4. **Phase 4**: Disable feature flag; full rollout

**Rollback**: Disable feature flags; scroll behavior reverts to current; unread counts recalculate on page reload.

## Open Questions

- What is the exact threshold for "at bottom"? (Proposed: 50px)
- Should scroll animation be disabled on mobile for performance? (Consider user preference)
- Should there be a visual indicator when auto-scroll is suppressed (user is above latest)? (Consider UX)

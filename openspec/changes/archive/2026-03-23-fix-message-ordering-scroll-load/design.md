## Context

Currently, the message display in MessageList reverses message ordering, showing newest messages at the bottom instead of the top. Additionally, scroll-to-top message loading for pagination is not working, preventing users from accessing older messages.

The chat store maintains a `messagesByRoom` array that should contain messages sorted by sequence number in ascending order. The issue appears to be in how messages are sorted, displayed, and how pagination boundaries are managed during scroll events.

Backend provides `/messages/before/{seq}` endpoint for fetching older messages, and the chat store has pagination infrastructure via `oldestSeqByRoom` tracking, but the integration is incomplete.

## Goals / Non-Goals

**Goals:**
- Ensure messages are always displayed in chronological order (oldest at top, newest at bottom)
- Enable scroll-to-top detection to trigger older message fetching
- Maintain consistent pagination state and sequence number boundaries
- Restore scroll position after prepending older messages to preserve reading context
- Handle edge cases: empty history, duplicate messages, concurrent scroll events

**Non-Goals:**
- Performance optimization through message virtualization (separate effort)
- Backend pagination API changes
- Real-time sync during scroll-load operations (handled by existing websocket logic)

## Decisions

### Decision 1: Message Array Sort Order
**Choice**: Maintain messages in ascending sequence number order (oldest to newest) in the store's `messagesByRoom[roomId]` array.

**Rationale**: This matches natural chat flow. Rendering the array as-is means messages display correctly without reverse iteration. All comparison operations become simpler.

**Alternatives Considered**:
- Keep messages in descending order + reverse on render (adds complexity without benefit)
- Keep insertion order and sort on-render (performance impact for large message lists)

### Decision 2: Scroll-Load Trigger
**Choice**: Detect when scroll position is near top (within 60px), then asynchronously fetch older messages via `getMessagesBefore()`.

**Rationale**: 60px threshold gives users a window to trigger load without scrolling all the way to top. Async prevents UI blocking.

**Alternatives Considered**:
- Intersection Observer API (cleaner but adds library complexity)
- Fetch on exact top=0 (fragile, misses edge cases)

### Decision 3: Scroll Position Restoration
**Choice**: Before async prepend, capture the current scroll height. After prepend completes, restore scroll position to maintain reading context.

**Rationale**: User reading experience depends on not jumping to top after fetch completes. Height-based restoration is more reliable than seq-based.

**Alternatives Considered**:
- Let scroll jump to top (poor UX)
- Use message ID anchors (brittle with pagination)

### Decision 4: Duplicate Prevention
**Choice**: Track processed message IDs in pagination state to avoid re-displaying messages across scroll-load cycles.

**Rationale**: Network retries or edge cases can cause the same message to return from the backend twice.

**Alternatives Considered**:
- Rely on backend uniqueness (insufficient, backend can retry duplicates)
- Merge on client side (complex with seq-based pagination)

## Risks / Trade-offs

| Risk | Severity | Mitigation |
|------|----------|-----------|
| Scroll event flood during fast scrolling | Medium | Debounce scroll handler with `loadingRef` flag to prevent concurrent fetches |
| Prepending empty message pages | Low | Check `hasMore` flag; stop fetching when backend returns no messages |
| Scroll restoration miscalculation | Medium | Test with various viewport heights and message sizes; log scroll calculations for debugging |
| Messages missing from middle after partial prepend | Medium | Maintain `oldestSeqByRoom` and re-query if gaps detected |

## Migration Plan

1. No database migrations needed (stateless fix).
2. Deploy updated MessageList and chat store together.
3. Verify on dev environment with scrolling test scenarios.
4. Monitor for scroll jump regressions in production.
5. Rollback: revert MessageList and chat store files.

## Open Questions

- Should we add visual feedback (spinner) when scrolling to load? (Deferred to UX iteration)
- How should we handle messages arriving via websocket during scroll-load? (Existing deduplication should handle)

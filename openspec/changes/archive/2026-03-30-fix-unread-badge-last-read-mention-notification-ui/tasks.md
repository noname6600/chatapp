## 1. Unread State Canonicalization

- [x] 1.1 Map current FE unread and behind-latest calculations to sequence and lastReadSeq sources
- [x] 1.2 Implement deterministic unread derivation from lastReadSeq and latestSeq with non-negative clamping
- [x] 1.3 Replace ad-hoc behind-latest counter updates with canonical derived state in room/message store
- [x] 1.4 Add reconnect reconciliation hook to rehydrate unread state from backend snapshot before rendering badges

## 2. Last-Read Boundary and Indicator Consistency

- [ ] 2.1 Update unread boundary placement logic to recompute from backend lastReadSeq after pagination changes
- [x] 2.2 Ensure newest-jump indicator and unread banner use the same canonical unread source
- [x] 2.3 Prevent stale in-room state from showing overflow-like behind-latest counts
- [x] 2.4 Add guard tests for boundary stability when loading older/newer windows

## 3. Mention-Targeted Notification Delivery

- [x] 3.1 Trace mention fanout path in backend message and notification flow and identify current broad-delivery points
- [x] 3.2 Restrict mention notification emission/persistence to explicitly targeted mentionedUserIds only
- [x] 3.3 Preserve normal message notifications for non-mentioned participants without mention-type records
- [x] 3.4 Add backend tests proving non-mentioned users never receive mention-notification records/events

## 4. Notification UI Layout Stabilization

- [x] 4.1 Audit notification panel, bell badge, and room list stacking context and overflow clipping behavior
- [x] 4.2 Move notification panel into a stable overlay container with deterministic z-index contract
- [x] 4.3 Ensure notification panel open state does not overlap or hide behind room-list interactive regions
- [x] 4.4 Add FE tests for notification panel rendering and interaction while room list is visible

## 5. Realtime Sync and Event Handling

- [x] 5.1 Normalize websocket unread and notification event handling order to avoid transient badge drift
- [x] 5.2 Add convergence fetch flow after reconnect for unread and notification summary state
- [x] 5.3 Ensure mention notification UI entries are rendered only for targeted mention events
- [x] 5.4 Add integration tests for reconnect and missed-event reconciliation

## 6. Verification

- [x] 6.1 Run frontend test suites for unread indicators, room list badges, and notification UI
- [x] 6.2 Run backend tests for mention targeting and notification fanout correctness
- [ ] 6.3 Manual test: enter room with unread messages and verify badge, boundary, and newest-jump are consistent
- [ ] 6.4 Manual test: stay in room while new messages arrive and verify no invalid behind-latest spikes
- [ ] 6.5 Manual test: send message with mention and verify only mentioned user gets mention-specific notification
- [ ] 6.6 Manual test: open notification UI with room list visible and verify no overlap/clipping regression

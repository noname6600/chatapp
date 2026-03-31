## 1. Realtime Reaction Event Handling

- [x] 1.1 Locate current reaction event producer and consumer paths in chat-service and frontend websocket layer
- [x] 1.2 Ensure reaction events are consumed as partial updates keyed by messageId, not full message replacements
- [x] 1.3 Implement deterministic merge logic for reaction payloads in message store
- [x] 1.4 Verify reaction updates do not overwrite message content, sender, createdAt, or attachments

## 2. Reaction Toggle Semantics

- [x] 2.1 Implement or refine toggle behavior for same user and same emoji (add on first click, remove on second)
- [x] 2.2 Ensure rapid repeated toggles remain idempotent and do not double increment/decrement counts
- [x] 2.3 Reconcile optimistic reaction changes with server realtime events without flicker

## 3. Self Highlight UX

- [x] 3.1 Update reaction rendering component to highlight reactions containing current user
- [x] 3.2 Remove highlight immediately when user toggles reaction off
- [ ] 3.3 Verify highlight behavior in one-on-one and group chats

## 4. Store and Component Integration

- [x] 4.1 Update chat message store to apply reaction updates to target message only
- [x] 4.2 Update room preview behavior if reaction events affect last message summary
- [x] 4.3 Validate compatibility with existing message send/edit/delete flows

## 5. Testing

- [x] 5.1 Add unit test: toggle on same emoji adds then removes reaction
- [x] 5.2 Add unit test: rapid toggles settle to correct final state
- [x] 5.3 Add unit test: reaction update preserves unrelated message fields
- [ ] 5.4 Add integration test: reaction from user A appears realtime to user B
- [ ] 5.5 Add integration test: own reaction highlight toggles on/off correctly

## 6. Build and Verification

- [x] 6.1 Run frontend build with no errors
- [x] 6.2 Run frontend tests including new reaction tests
- [x] 6.3 Run TypeScript checks with no errors
- [ ] 6.4 Manual test: realtime reaction propagation across two clients
- [ ] 6.5 Manual test: own reaction highlight is visually distinct and stable

## 1. Timestamp Ownership Standardization

- [x] 1.1 Review `ChatMessage`, `ChatAttachment`, `ChatMessageMention`, and `ChatReaction` entity timestamp fields and keep server-managed `createdAt` behavior via lifecycle callbacks
- [x] 1.2 Ensure all four entities use the same timestamp-generation approach (no mixed manual vs lifecycle behavior)

## 2. Reaction Pipeline Cleanup

- [x] 2.1 Remove manual `.createdAt(Instant.now())` assignment from `PersistReactionStep`
- [x] 2.2 Verify reaction persistence still produces non-null `createdAt` through entity lifecycle callback

## 3. Message/Attachment/Mention Flow Validation

- [x] 3.1 Verify message creation flow does not depend on request time fields and persists `ChatMessage.createdAt`
- [x] 3.2 Verify attachment records persisted from `MessageAggregate` always receive non-null `createdAt`
- [x] 3.3 Verify mention records created in `PersistMentionStep` always receive non-null `createdAt`

## 4. Event Timestamp Source Consistency

- [x] 4.1 Ensure message event publish step/factory uses persisted message entity timestamp
- [x] 4.2 Ensure reaction event publish step uses persisted reaction timestamp (not request-time placeholder)

## 5. Test Coverage

- [x] 5.1 Add/update unit test for message send pipeline ensuring persisted message has non-null `createdAt`
- [x] 5.2 Add/update unit test for mention persistence ensuring saved mentions have non-null `createdAt`
- [x] 5.3 Add/update unit/integration test for reaction toggle ensuring created reactions have non-null `createdAt` without manual assignment
- [x] 5.4 Add/update test for message/reaction event payloads asserting non-null `createdAt`

## 6. Verification

- [x] 6.1 Build `chat-service` module and ensure zero compile errors
- [x] 6.2 Run related tests for message and reaction pipelines
- [ ] 6.3 Manual sanity check: send message with attachment + mentions, add reaction, verify timestamps are populated in DB/event payloads

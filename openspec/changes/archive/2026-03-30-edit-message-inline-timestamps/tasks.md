# Tasks: edit-message-inline-timestamps

## 1. Timestamp Display Logic

- [x] 1.1 Create EditedIndicator component with `blocks?: MessageBlock[]` prop
- [x] 1.2 Implement block count detection logic (single block = inline, multiple = newline)
- [x] 1.3 Add EditedIndicator to MessageContent component for TEXT messages
- [x] 1.4 Add EditedIndicator to MessageBlocks component for multi-block messages
- [x] 1.5 Test inline timestamp rendering on single-text edits
- [x] 1.6 Test newline timestamp rendering on multi-block edits
- [x] 1.7 Verify timestamp placement adapts when editing collapses/expands block count

## 2. Empty Block Filtering

- [x] 2.1 Update MessageBlocks component to filter out empty TEXT blocks
- [x] 2.2 Update MessageContent component to handle empty text blocks gracefully
- [x] 2.3 Verify filtered blocks don't create unwanted spacing in render
- [x] 2.4 Test mixed content (text + image + text) renders correctly after filtering
- [x] 2.5 Test message with only empty blocks after filtering shows no content
- [x] 2.6 Verify edited messages with intentionally deleted blocks show clean spacing

## 3. Mention Support in Edited Text

- [x] 3.1 Identify mention hook integration point in InlineEditInput
- [x] 3.2 Add `useMention()` hook to InlineEditInput component
- [x] 3.3 Implement mention autocomplete suggestion display in edit textarea
- [x] 3.4 Add mention selection handler that inserts resolved tokens
- [x] 3.5 Update editMessageApi to preserve mention tokens in serialized blocks
- [x] 3.6 Test mention autocomplete appears while typing in edit mode
- [x] 3.7 Test selected mentions render with correct styling after save
- [x] 3.8 Test multiple mentions can be added in single edit

## 4. Edit Flow & Message Update

- [x] 4.1 Verify BlockMessageEditor loads full block structure on edit initiation
- [x] 4.2 Verify InlineEditInput loads text content for single-block edits
- [x] 4.3 Add edit validation that requires non-empty content
- [x] 4.4 Implement optimistic update in MessageItem for edited messages
- [x] 4.5 Add error handling to restore message state if edit API fails
- [x] 4.6 Verify edit mode exit (Cancel/Escape) restores display mode
- [x] 4.7 Test edit submission returns to display mode with updated content
- [x] 4.8 Verify action bar buttons are re-enabled after edit completes

## 5. Backend Block Persistence

- [x] 5.1 Investigate why blocksJson is stripped from edit response (MessageMapper)
- [x] 5.2 Trace blocksJson through persistence pipeline (PersistEditedMessageStep)
- [x] 5.3 Verify MessageBlockMapper.toResponses() uses correct blocksJson field
- [ ] 5.4 Add logging to confirm blocksJson is saved to database
- [ ] 5.5 Add logging to confirm blocksJson is returned in API response
- [x] 5.6 Test end-to-end: edit multi-block message and verify blocks persist

## 6. Integration & Testing

- [x] 6.1 Run all frontend unit tests for MessageContent, MessageBlocks, InlineEditInput
- [x] 6.2 Run all backend tests for EditMessageRequest and MessageAggregate
- [x] 6.3 Manual browser test: single-text edit with inline timestamp
- [x] 6.4 Manual browser test: multi-block edit with newline timestamp
- [x] 6.5 Manual browser test: edit with mentions and verify mention styling
- [x] 6.6 Manual browser test: delete blocks during edit and verify spacing
- [x] 6.7 Manual browser test: failed edit reverts message state
- [x] 6.8 Cross-browser verification (Chrome, Firefox, Safari)

## 7. Documentation & Cleanup

- [x] 7.1 Update HELP.md or README with edit feature usage examples
- [x] 7.2 Remove any remaining debug logging from implementation
- [x] 7.3 Code review: verify EditedIndicator component matches design system
- [x] 7.4 Code review: verify mention handling matches composer patterns
- [ ] 7.5 Archive completed change when all tasks are verified complete

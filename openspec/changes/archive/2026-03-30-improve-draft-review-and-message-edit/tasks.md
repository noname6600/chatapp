## 1. Draft Review Modal Component

- [x] 1.1 Create DraftReviewModal component that displays composed message content
- [x] 1.2 Add editable text field in modal to allow draft message modification
- [x] 1.3 Implement Save/Send, Edit, and Cancel buttons with appropriate handlers
- [x] 1.4 Wire modal to message composer to receive composed content on open
- [x] 1.5 Ensure reply context (replyToMessageId) is preserved when opening draft review

## 2. Draft Review Integration

- [x] 2.1 Add "Review" button or keyboard shortcut trigger to open draft review
- [x] 2.2 Update message composer to pass current message state to draft review modal
- [x] 2.3 Wire send button in draft review modal to trigger message send with edited content
- [x] 2.4 Ensure attachments and mentions are preserved through draft review flow
- [x] 2.5 Add option to restore original draft text if user made edits then changed mind

## 3. Message Edit Inline Component

- [x] 3.1 Create inline message edit UI with editable text field for sent message content
- [x] 3.2 Add Save and Cancel controls with appropriate handlers
- [x] 3.3 Implement client-side validation (e.g., non-empty text, length limits)
- [x] 3.4 Wire inline editor to receive message data (ID, current content) on open

## 4. Message Edit Action & Integration

- [x] 4.1 Add "Edit" action button to message item row (for current user's messages only)
- [x] 4.2 Update message row rendering to conditionally show edit button only for sender
- [x] 4.3 Wire edit button to open inline editor with current message content
- [x] 4.4 Create API call function for message update endpoint (PUT /api/v1/messages/:id)
- [x] 4.5 Handle loading state and error states during message edit submission

## 5. Message Edit Indicator & Display

- [x] 5.1 Add "Edited at HH:MM" timestamp display on edited messages
- [x] 5.2 Update message rendering pipeline to include edited-at information
- [x] 5.3 Add subtle visual indicator or styling for edited messages (optional: different background or badge)
- [x] 5.4 Ensure edit timestamp is cleared/hidden for never-edited messages

## 6. Backend Message Update Endpoint

- [x] 6.1 Create PUT endpoint for message updates at /api/v1/messages/{id}
- [x] 6.2 Add authorization check to ensure only message sender can edit
- [x] 6.3 Add content validation (non-empty, length limits)
- [x] 6.4 Update message entity with new content and edited-at timestamp
- [x] 6.5 Return updated message in response with edit metadata

## 7. Backend Message Edit Context Preservation

- [x] 7.1 Ensure reply context (replyToMessageId) is preserved during edit
- [x] 7.2 Ensure message ordering/chronology is not affected by edit
- [x] 7.3 Ensure unread counts are not affected by message edits
- [x] 7.4 Add database column for edited-at timestamp if not already present

## 8. Frontend-Backend Integration

- [x] 8.1 Handle message update response from backend and update local state
- [x] 8.2 Implement optimistic update (show edited message immediately, revert on error)
- [x] 8.3 Add error handling for edit failures (permissions, message not found, validation errors)
- [x] 8.4 Refresh message from server after edit to ensure consistency

## 9. Testing - Draft Review

- [x] 9.1 Unit test: Draft review modal opens and closes correctly
- [x] 9.2 Unit test: Draft text can be edited and modified content is displayed
- [x] 9.3 Unit test: Save button sends message with edited content
- [x] 9.4 Unit test: Cancel button closes modal without sending
- [x] 9.5 Integration test: Draft review flow with actual message send
- [x] 9.6 Integration test: Reply context is preserved through draft review

## 10. Testing - Message Edit

- [x] 10.1 Unit test: Edit button appears only on current user's messages
- [x] 10.2 Unit test: Inline edit UI opens with current message content
- [x] 10.3 Unit test: Save button submits edited content to API
- [x] 10.4 Unit test: Cancel button closes inline editor without changes
- [x] 10.5 Unit test: Edited-at timestamp displays on saved edit
- [x] 10.6 Integration test: Full message edit flow (open, edit, save, verify display)
- [x] 10.7 Backend test: Message update endpoint validates authorization
- [x] 10.8 Backend test: Message update endpoint preserves reply context

## 11. Validation & Manual Testing

- [x] 11.1 Manual test: Draft review opens and closes as expected
- [x] 11.2 Manual test: Draft review text editing works smoothly
- [x] 11.3 Manual test: Edited draft sends successfully
- [x] 11.4 Manual test: Message edit action appears and opens inline editor
- [x] 11.5 Manual test: Edited message displays in conversation
- [x] 11.6 Manual test: Edited-at timestamp is visible on edited messages
- [x] 11.7 Manual test: Edit not available on other users' messages
- [x] 11.8 Manual test: Reply context preserved after edit
- [x] 11.9 Manual test: Error handling for failed edits (show friendly error message)
- [x] 11.10 Verify: Frontend and backend tests pass

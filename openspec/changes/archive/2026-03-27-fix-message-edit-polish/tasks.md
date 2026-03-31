## 1. Edited Badge Alignment Fix (Frontend)

- [x] 1.1 In `MessageItem.tsx` `MessageContent`, remove the `rounded bg-slate-50/60 px-1.5 py-1` background wrapper `<div>` that wraps the text content when `editedAt` is set
- [x] 1.2 Move the "edited \<timestamp\>" `<span>` to render inline directly after the text content without a surrounding padded block, for both the plain-text path and the structured-blocks path
- [ ] 1.3 Verify in the browser that an edited message row has the same height as a non-edited message with equivalent content

## 2. Scroll Stability After Edit Confirm (Frontend)

- [x] 2.1 In `MessageItem.tsx` `handleEditSubmit`, remove the `loadMessagesAround(m.roomId, m.messageId)` call and its surrounding try/catch block
- [ ] 2.2 Verify that after submitting an edit the chat scroll position does not jump

## 3. Disable Actions During Inline Editing (Frontend)

- [x] 3.1 In `MessageItem.tsx` `MessageActions`, add `disabled={isEditing}` to the Reply button
- [x] 3.2 Add `disabled={isEditing}` to the Delete button in `MessageActions`
- [x] 3.3 Pass `disabled={reactionLoading || isEditing}` to the `EmojiPicker` component in `MessageActions`
- [ ] 3.4 Verify that clicking Reply, Emoji picker trigger, and Delete while in edit mode does nothing

## 4. Exclude Soft-Deleted Messages from History Queries (Backend)

- [x] 4.1 In `ChatMessageRepository`, add `AND deleted = false` to the inner query of `findLatestByRoom` (native SQL)
- [x] 4.2 Add `AND deleted = false` to the inner query of `findBeforeSeq` (native SQL)
- [x] 4.3 Add `AND m.deleted = false` to the `findRange` JPQL query
- [x] 4.4 Add `AND m.deleted = false` and `AND m2.deleted = false` to both correlated subquery levels in `findLastMessages` JPQL query
- [x] 4.5 Write a `ChatMessageRepositoryDeleteFilterTest` (or equivalent) asserting that a deleted message does not appear in `findLatestByRoom` results

## 5. Validation

- [x] 5.1 Run frontend tests: `npm run test -- MessageItem.edit.test.tsx` — all pass
- [x] 5.2 Run backend chat-service tests: `.\gradlew.bat :chat-service:test` — BUILD SUCCESSFUL
- [ ] 5.3 Manual: delete a message, refresh browser, confirm it does not reappear
- [ ] 5.4 Manual: edit a message, confirm scroll position is stable and edited badge is inline
- [ ] 5.5 Manual: open inline edit on a message, confirm Reply/Emoji/Delete are visually disabled

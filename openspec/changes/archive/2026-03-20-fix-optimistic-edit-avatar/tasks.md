## 1. Investigation & Analysis

- [x] 1.1 Locate the message editing state management (likely useEdit hook, edit reducer, or message store)
- [x] 1.2 Identify where optimistic message state is constructed during edit
- [x] 1.3 Trace the flow: edit action → optimistic state creation → rendering → server response
- [x] 1.4 Identify if bug is in state creation or in the render component
- [x] 1.5 Review message object type definition (TypeScript) to confirm author fields are defined

## 2. Fix Optimistic Message State

- [x] 2.1 Update edit action/reducer to preserve original message's author metadata when creating optimistic state
- [x] 2.2 Change optimistic state structure from replacing message to: `{...originalMessage, content: newContent, isEditing: true}`
- [x] 2.3 Add type checking to ensure author metadata exists in optimistic message
- [ ] 2.4 Test with group chats: verify edited message keeps original sender's info, not current user's info
- [ ] 2.5 Test with one-on-one chats: verify edited messages still show correct author

## 3. Rendering Layer Safeguards

- [x] 3.1 Review MessageItem or message rendering component for avatar/name display logic
- [x] 3.2 Add defensive fallback to use message.author metadata instead of user context assumptions
- [ ] 3.3 Add logging or assertions to detect if author metadata is ever missing (for debugging)

## 4. Integration Testing

+ [ ] 4.1 Write unit test: editing a message preserves original author in optimistic state
+ [ ] 4.2 Write unit test: only message content changes during optimistic edit, not metadata
+ [ ] 4.3 Integration test: group chat - verify avatar/name correct when user B's message is displayed during edit
+ [ ] 4.4 Integration test: one-on-one chat - verify user's own edited messages remain attributed to them
+ [ ] 4.5 Integration test: server response reconciliation - verify author metadata in server response matches optimistic state
+ [ ] 4.6 Test edge case: rapid successive edits maintain correct author throughout

## 5. Manual Browser Testing

- [ ] 5.1 Manual test: edit message in one-on-one chat, verify correct avatar/name during optimistic phase
- [ ] 5.2 Manual test: in group chat, have user A edit user B's message view (if applicable), verify correct author display
- [ ] 5.3 Manual test: edit message, cancel edit, verify state rollback is clean with correct author
- [ ] 5.4 Manual test: edit message, send edit, verify no avatar/name flicker when server response arrives
- [ ] 5.5 Manual test: mobile browser (iOS Safari, Chrome Mobile) - verify avatar/name correct during edit

## 6. Build & Verification

+ [x] 6.1 Run frontend build (npm run build) with no errors
+ [x] 6.2 Run all unit and integration tests (ensure all pass)
+ [x] 6.3 TypeScript compilation check (no type errors on edited files)
+ [x] 6.4 Review code diff to ensure only author-metadata-related changes made
+ [ ] 6.5 Verify no console errors in development mode during message editing flow

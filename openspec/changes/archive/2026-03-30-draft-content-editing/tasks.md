## 1. State Management & Data Model

- [x] 1.1 Define TypeScript types for draft blocks (`DraftBlock`, `TextBlock`, `MediaBlock`, `DraftState`)
- [x] 1.2 Extend Zustand draft store with `blocks: DraftBlock[]`, `editingBlockId`, `editingContent` fields
- [x] 1.3 Create migration function to convert legacy `{ text, attachments }` drafts to blocks format
- [x] 1.4 Implement draft store actions: `addTextBlock()`, `addMediaBlock()`, `removeBlock()`, `editBlock()`, `saveBlockEdit()`, `cancelEdit()`
- [x] 1.5 Update draft initialization to use blocks array and apply migration on load

## 2. UI Components

- [x] 2.1 Create `TextBlock` component to render a text block with click-to-edit affordance
- [x] 2.2 Create `MediaBlock` component to render media with delete button
- [x] 2.3 Update `DraftComposer` component to render blocks array instead of flat text input + attachment list
- [x] 2.4 Add inline `<textarea>` editor in TextBlock for edit mode
- [x] 2.5 Implement visual feedback for edit mode (e.g., border highlight, button states)

## 3. Edit Mode Interaction

- [x] 3.1 Implement click handler on TextBlock to enter edit mode and focus textarea
- [x] 3.2 Implement Escape key handler to cancel edit and revert changes
- [x] 3.3 Implement blur and Enter key handlers to save text edits
- [x] 3.4 Implement click handler on delete button to remove MediaBlock
- [x] 3.5 Ensure only one TextBlock in edit mode at a time (auto-save on switch)

## 4. Message Send & Reassembly

- [x] 4.1 Update message send flow to extract text blocks (concatenate with newlines) and media blocks
- [x] 4.2 Ensure send API receives standard `{ text: string, attachments: File[] }` format (no changes to backend required)
- [x] 4.3 Handle edge cases: empty text blocks, media-only drafts, text-only drafts

## 5. Testing (Automated)

- [x] 5.1 Unit test: draft store block operations (add, remove, edit, save, cancel)
- [x] 5.2 Unit test: migration function converts legacy drafts correctly
- [x] 5.3 Component test: TextBlock renders and enters/exits edit mode
- [x] 5.4 Component test: MediaBlock renders with delete button and removes on click
- [x] 5.5 Integration test: DraftComposer renders and manages blocks correctly
- [x] 5.6 Integration test: Send flow reassembles blocks to correct format

## 6. Testing (Manual)

- [ ] 6.1 Browser test: Click to edit text block, verify textarea appears with current content
- [ ] 6.2 Browser test: Edit text and click outside (blur), verify changes saved to draft
- [ ] 6.3 Browser test: Edit text and press Escape, verify changes reverted
- [ ] 6.4 Browser test: Edit text block A, then click to edit block B, verify A saved and B focused
- [ ] 6.5 Browser test: Add media while text block is being edited, verify edit mode continues
- [ ] 6.6 Browser test: Click delete button on media block, verify media removed and surrounding text blocks intact
- [ ] 6.7 Browser test: Send draft with mixed text and media, verify message sent with correct content
- [ ] 6.8 Browser test: Load app with legacy draft format, verify it displays as blocks without user action

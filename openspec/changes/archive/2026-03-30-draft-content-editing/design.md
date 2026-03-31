## Context

Currently, drafts are modeled as a single string of text with an optional set of media attachments. Users composing messages cannot edit text content after initial input without clearing the entire draft. Media items (images, files) also cannot be individually removed without affecting the rest of the draft or restarting composition.

**Current State:**
- Draft store (Zustand) holds: `{ roomId, text: string, attachments: File[] }`
- DraftComposer displays text input + media gallery
- Editing text requires selecting all and replacing
- Removing media requires interaction with file picker or bulk management

**Constraint:** Drafts are client-side only (not persisted). Changes are transient until send.

## Goals / Non-Goals

**Goals:**
- Enable inline text editing within drafts (click-to-edit pattern)
- Allow individual media item removal from draft
- Maintain draft data integrity during edits (no state corruption)
- Preserve edit history within a draft (optional undo, but at minimum clear error states)
- Keep implementation client-side only (no server persistence)

**Non-Goals:**
- Draft auto-save or persistence to server
- Collaborative draft editing (single user per draft)
- Media reordering (may add later; out of scope for v1)
- File upload validation changes (existing validation sufficient)
- Undo/redo beyond simple item removal

## Decisions

### 1. Draft Content Structure: From Single String to Content Blocks Array

**Decision:** Model draft content as an ordered array of content items (blocks), where each block is either `{ type: 'text', id, content }` or `{ type: 'media', id, file, preview }`.

**Rationale:**
- Each block gets a unique ID for independent removal/editing
- Separates text and media conceptually, enabling targeted UI controls
- Supports future reordering, media replacement
- Enables granular state updates (edit one block, others unchanged)

**Alternative Considered:**
- Keep single string + separate media array: loses document flow context, harder to track which media goes where
- Keep current structure: doesn't scale to multi-block composition

**Impact:** Draft store shape changes; DraftComposer must parse and render blocks; message send logic extracts text and media from blocks.

### 2. Edit Mode Pattern: Inline Click-to-Edit

**Decision:** Text blocks enter edit mode on click. User sees textarea (not input) for multi-line support. Blur or Enter key saves; Escape cancels.

**Rationale:**
- Familiar pattern (Gmail, Slack)
- No modal/dialog (stays in context)
- Low friction (single click)
- Escape provides clear cancel mechanism

**Edit State:** Track `{ editingBlockId: string | null, editingContent: string }` in draft store.

### 3. Media Removal: Delete via Icon Button

**Decision:** Each media block displays a small delete (X) button on hover. Click removes the block immediately.

**Rationale:**
- Visual consistency (standard close/remove affordance)
- No confirmation dialog (easy undo via Escape in text blocks, rare mistake)
- Immediate feedback

### 4. Draft Store Extension

**Decision:** Extend Zustand draft store with:
```
{
  roomId: string,
  blocks: Array<{
    id: string,
    type: 'text' | 'media',
    content?: string,  // text blocks
    file?: File,       // media blocks
    preview?: string   // media preview URL
  }>,
  editingBlockId?: string,
  editingContent?: string
}
```

**Actions:** `addTextBlock()`, `addMediaBlock()`, `editBlock()`, `removeBlock()`, `saveBlockEdit()`, `cancelEdit()`

### 5. Send Flow: Reassemble Blocks

**Decision:** When sending, reconstruct the message from blocks: concatenate text blocks (with newlines), collect media items, send via existing message API.

**Rationale:**
- Existing backend expects `{ text, attachments }` shape
- No backend changes needed
- Clean separation: draft is composition layer, message is wire format

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| **State proliferation**: Too many edit-related fields in store | Keep `editingBlockId`/`editingContent` temporary; clear on save/cancel |
| **Text block concatenation**: newline handling between blocks | Define clear rules: blocks join with single `\n`; user can add blank blocks for spacing |
| **Ctrl+Z expectation**: Users expect undo after delete | Not implementing full undo (v1 scope). Escape in edit mode provides cancel for text edits. |
| **Mobile interaction**: Touch-friendly remove buttons | Ensure buttons large enough (at least 44x44px touch target). Preview as risk pre-launch. |
| **Large file handling**: Multiple large images in draft | Existing file size limits apply; no additional risk from block model |

## Migration Plan

**Phase 1: Add block model to store** (backwards compatible)
- New draft creation uses blocks array
- Existing drafts with old shape: migration function converts `{ text, attachments }` → `{ blocks: [{ type: 'text', content: text }, { type: 'media', file: att }, ...] }`

**Phase 2: Update DraftComposer**
- Render blocks instead of text input + media list
- Add inline edit mode for text blocks
- Add delete button for media blocks

**Phase 3: Update send flow**
- Extract blocks → text + attachments before API call
- No API change needed

**Rollback:** Migrate blocks back to old format if needed; feature is client-side only so zero server-side risk.

## Open Questions

1. **Placeholder text in blocks**: Should empty text blocks show placeholder "Add text..." to encourage composition? (vs. requiring full block add flow)
2. **Touch vs. click**: Should delete be swipe-left pattern on mobile? Or stick with X button? (defer to UI/UX review)
3. **Auto-add blank block**: After user adds media, should we auto-insert a blank text block below for continued typing? Or make user explicitly add?

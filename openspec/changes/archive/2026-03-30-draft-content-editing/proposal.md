## Why

Users composing messages in drafts often want to modify message content (text, images, media) before sending. Currently, draft content is immutable—editing requires starting over. Enabling content editing and removal in drafts improves message composition UX and reduces friction when correcting mistakes or adding/removing media.

## What Changes

- Add ability to edit text content in draft messages while composing
- Add ability to remove individual media items (images, attachments) from drafts
- Add ability to reorder or replace media content in draft composition
- Draft content becomes fully editable before sending, reducing need to discard and restart

## Capabilities

### New Capabilities

- `editable-draft-content`: Ability to modify and remove text and media items within a draft before sending. Includes inline text editing, media removal, and content reordering without sending or discarding the entire draft.

### Modified Capabilities

<!-- No existing specs require modification for this feature -->

## Impact

**Frontend:**
- Draft composition UI (DraftComposer component) — add edit/delete controls for each content block (text, image, file)
- Draft store (Zustand) — extend state to track editable content items
- Message input parsing — support mixed text/media content with individual item editing

**Backend:**
- No backend changes needed — drafts are client-side only, not persisted to server
- Draft API endpoints remain unchanged

**User-Facing Changes:**
- New edit buttons on individual draft content items
- New delete/remove buttons for media in drafts
- Ability to click on text blocks in drafts to edit inline

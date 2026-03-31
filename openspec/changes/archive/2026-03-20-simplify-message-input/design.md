## Context

The current `MessageInput.tsx` component includes inline formatting toolbar with bold/italic/strikethrough buttons. The keyboard behavior (send on Enter) is basic but lacks proper newline support. File attachment is supported but lacks visual drag-drop affordance. The component is part of the chat page layout and interacts with message store for optimistic updates.

**Current State:**
- Formatting buttons take up ~120px width
- No differentiation between Enter (send) and Shift+Enter (newline)
- File upload is button-driven only, no drag-drop
- Input styling couples text area with formatting toolbar CSS

## Goals / Non-Goals

**Goals:**
- Remove text formatting controls from UI (no bold, italic, strikethrough buttons)
- Implement keyboard shortcuts: Enter = send, Alt+Enter = newline
- Add drag-drop file/image support to the input area
- Simplify component structure and CSS (narrower component)
- Deliver cleaner, faster UX with better keyboard accessibility

**Non-Goals:**
- Implement server-side text formatting parsing or storage
- Add new message types or special content structures
- Change attachment API behavior or response format
- Modify message store or reducer logic (input remains stateless at store level)
- Add undo/redo or draft auto-save

## Decisions

### Decision 1: Remove Formatting Toolbar Completely
**Approach:** Delete formatting button elements and their event handlers. Keep text send unchanged.

**Rationale:** Bold/italic are cosmetic; real chat apps don't use inline styling for persistence. Users benefit more from clean UI than optional formatting.

**Alternatives considered:**
- Keep buttons but hide them (rejected: adds no value, maintenance burden)
- Move to right-click menu (rejected: worse discoverability, complexity)
- Move to slash-commands (rejected: out of scope for this change)

### Decision 2: Keyboard Behavior (Enter vs Alt+Enter)
**Approach:** 
- Listen to `onKeyDown` event
- If `event.key === 'Enter'` and NOT `event.altKey`: call send(), prevent default
- If `event.altKey && event.key === 'Enter'`: insert newline (allow default browser behavior)
- For Ctrl+Enter variant (Windows users): treat same as Alt+Enter

**Rationale:** Alt+Enter is standard in many chat apps. Prevents accidental sends when composing multiline messages.

**Alternatives considered:**
- Shift+Enter for newline (rejected: conflicts with some keyboard layouts, Alt is less ambiguous)
- Button toggle for "send on Enter" (rejected: adds UI complexity)

### Decision 3: Drag-Drop Image/File Support
**Approach:**
- Add `onDragOver`, `onDragLeave`, `onDrop` handlers to textarea wrapper
- On drag-over: add visual feedback (border highlight, background color)
- On drop: extract files from `event.dataTransfer.files`, validate type (image/*, application/pdf, etc.), pass to existing file upload handler
- Reuse existing attachment upload API (no changes to backend)

**Rationale:** Seamless UX, leverages existing attachment infrastructure, minimal code changes.

**Alternatives considered:**
- Create new drag-drop handler separate from input area (rejected: forces users to hit small zone)
- Toast/modal for dropped files (rejected: extra UX friction)

### Decision 4: File/Image Button Placement
**Approach:** Position file button on the right side of the input, aligned with send button area (or integrated as part of send button cluster).

**Rationale:** Right alignment matches text direction and send button, creates clear action zone.

## Risks / Trade-offs

### [Risk] Users expect old formatting to remain
**Mitigation:** This is cosmetic functionality, not a breaking API change. Document in release notes as "UI simplification" rather than loss. No user data is lost.

### [Risk] Alt+Enter may conflict on some keyboards
**Mitigation:** Test with multiple keyboard layouts. Provide fallback: also accept Ctrl+Enter for Windows/Linux users who prefer it.

### [Risk] Drag-drop area may be small on mobile
**Mitigation:** Mobile browsers handle drag-drop differently. Test on mobile browsers; fallback to button upload is always available. Consider touch-friendly gesture in future iteration.

### [Trade-off] Removing formatting toolbar saves ~120px but loses user power-user feature
**Mitigation:** Formatting was never persisted/displayed anyway (cosmetic only). Users who need formatting can use external tools before pasting. This is acceptable simplification.

## Migration Plan

1. **Phase 1 (Code):** Update MessageInput.tsx
   - Remove formatting button JSX and CSS
   - Add keyboard listeners and validation logic
   - Add drag-drop handlers and visual states
   - Update component tests

2. **Phase 2 (Testing):** Manual testing
   - Test Enter to send on desktop (Chrome, Firefox, Safari)
   - Test Alt+Enter for newline on multiple keyboards
   - Test Ctrl+Enter fallback
   - Test drag-drop with images, PDFs, other filetypes
   - Test on mobile (iOS Safari, Chrome mobile)

3. **Phase 3 (Rollout):** Deploy with v<X>.<Y>
   - Include in release notes: "Simplified message input UI"
   - Monitor for keyboard-related support tickets

**Rollback:** Revert MessageInput.tsx file and clear browser cache. Component is self-contained, no database migrations required.

## Open Questions

1. Should we support file drag-drop for multiple files at once, or single file per drop?
2. Do we want a visual counter showing number of files pending upload?
3. Should Alt+Enter work the same on Mac (vs Cmd+Enter)?

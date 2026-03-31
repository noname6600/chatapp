## Why

The current message input component has unnecessary complexity with inline text formatting controls (bold, italic, strikethrough) that clutter the UI and add maintenance burden. Users more often need keyboard-driven features (proper Enter/newline handling) and the ability to attach files/images via drag-drop. Simplifying the input to focus on core functionality improves UX and reduces cognitive load.

## What Changes

- **Remove** inline text formatting toolbar (bold, italic, strikethrough buttons and their styling)
- **Fix** keyboard behavior: Enter sends message, Alt+Enter creates new line
- **Add** drag-drop support for images and files directly to the input area
- **Simplify** the input component to show only text area and file/image send buttons (left or right positioning)
- **Keep** file attachment UI simple: drag-drop + single upload/button

## Capabilities

### New Capabilities
- `simplified-message-input`: Clean message input component with keyboard shortcuts (Enter=send, Alt+Enter=newline), drag-drop file/image support, and removal of inline text formatting controls

### Modified Capabilities
- `message-sending`: The message send action now respects the simplified input's keyboard behavior and supports drag-dropped attachments directly

## Impact

- **Affected Code**: 
  - Frontend: `chatappFE/src/components/chat/MessageInput.tsx` and related styling
  - Frontend: `chatappFE/src/types/message.ts` (message input state)
- **APIs**: No backend API changes; attachment endpoint usage remains the same
- **Dependencies**: Potentially simplifies build (fewer UI libraries if formatting was external)
- **Breaking Changes**: None. Formatting functionality removed but this was cosmetic, not core messaging capability

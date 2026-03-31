## 1. Remove Text Formatting Controls

- [x] 1.1 Delete bold, italic, strikethrough button JSX elements from MessageInput.tsx
- [x] 1.2 Remove formatting toolbar CSS classes and styles
- [x] 1.3 Delete formatting event handlers (`onBoldClick`, `onItalicClick`, etc.)
- [x] 1.4 Update component import statements and dependencies

## 2. Implement Enter-to-Send Keyboard Behavior

- [x] 2.1 Add `onKeyDown` event listener to textarea element
- [x] 2.2 Implement Enter key detection (check `event.key === 'Enter'` and `!event.altKey`)
- [x] 2.3 Call message send action when Enter pressed with empty `event.altKey`
- [x] 2.4 Prevent default form submission behavior on Enter (`event.preventDefault()`)
- [x] 2.5 Verify empty message check (don't send if input is empty/whitespace-only)

## 3. Implement Alt+Enter for Newline

- [x] 3.1 Detect Alt+Enter combination in keyboard handler (`event.altKey && event.key === 'Enter'`)
- [x] 3.2 Allow default newline insertion behavior when Alt+Enter detected
- [x] 3.3 Add Ctrl+Enter as Windows/Linux fallback (treat same as Alt+Enter)
- [ ] 3.4 Test on multiple keyboard layouts (US, European, etc.)

## 4. Implement Drag-Drop File Support

- [x] 4.1 Add `onDragOver` handler to input area wrapper with visual feedback CSS
- [x] 4.2 Add `onDragLeave` handler to remove visual feedback
- [x] 4.3 Add `onDrop` handler to extract files from `event.dataTransfer.files`
- [x] 4.4 Validate file types (accept images, PDFs, documents; reject executables)
- [x] 4.5 Integrate with existing file upload handler (reuse attachment upload logic)
- [x] 4.6 Handle multiple files in single drop (queue all for upload)

## 5. Update Related Components

- [x] 5.1 Update MessageInput component's TypeScript types if needed
- [x] 5.2 Update any parent component props or callbacks affected by input changes
- [x] 5.3 Verify message store integration (optimistic updates still work)
- [x] 5.4 Update component documentation/comments

## 6. Testing

- [x] 6.1 Write unit tests for Enter-to-send behavior
- [x] 6.2 Write unit tests for Alt+Enter newline behavior
- [x] 6.3 Write unit tests for drag-drop handlers
- [x] 6.4 Write unit tests for file type validation
- [ ] 6.5 Manual test: Enter sends message on Chrome/Firefox/Safari
- [ ] 6.6 Manual test: Alt+Enter creates newline on multiple keyboards
- [ ] 6.7 Manual test: Ctrl+Enter works as Alt+Enter fallback
- [ ] 6.8 Manual test: Drag-drop accepts images and files
- [ ] 6.9 Manual test: Drag-drop rejects unsupported file types
- [ ] 6.10 Manual test: Mobile browsers (iOS Safari, Chrome mobile) handle drag-drop gracefully

## 7. Build & Verify

- [x] 7.1 Build chatappFE without errors or warnings
- [x] 7.2 Compile TypeScript without type errors
- [x] 7.3 Run all message input related tests locally
- [ ] 7.4 Verify no console errors in development mode

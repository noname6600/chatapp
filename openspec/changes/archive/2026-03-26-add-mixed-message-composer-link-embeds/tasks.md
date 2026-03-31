## 1. Message Contract and Data Model

- [x] 1.1 Define shared ordered block schema for text and asset message content.
- [x] 1.2 Extend backend send-message request/response contracts to carry ordered blocks.
- [x] 1.3 Preserve backward compatibility for existing consumers that still rely on `content`, `attachments`, and derived `MessageType`.
- [x] 1.4 Update room-preview and notification summary derivation for mixed-content block messages.

## 2. Frontend Composer and Draft State

- [x] 2.1 Replace textarea-only draft state with an append-only mixed-content draft model that preserves authored order.
- [x] 2.2 Add inline draft preview showing ordered text and media placeholders/previews.
- [x] 2.3 Flush trailing text into a text block before newly inserted asset blocks so composition can produce `text [image] text [image]`.
- [x] 2.4 Keep reply, typing, and existing send-shortcut behavior compatible with the new draft model.

## 3. Clipboard Paste and Media Insertion

- [x] 3.1 Add `Ctrl+V` clipboard image detection in the composer.
- [x] 3.2 Append pasted images into the draft in authored sequence after flushing the current trailing text segment.
- [x] 3.3 Reuse upload-service prepare -> upload -> confirm flow for pasted media.
- [x] 3.4 Block send when any inserted media block is failed or still uploading.

## 4. Message Rendering and Transport

- [x] 4.1 Render ordered blocks in chat messages so text and media appear in authored sequence.
- [x] 4.2 Update websocket and HTTP message mapping to round-trip ordered block data.
- [x] 4.3 Keep legacy attachment-only messages rendering unchanged.
- [x] 4.4 Render URLs inside text blocks as safe clickable links without provider-specific embed cards.
- [x] 4.5 Ensure optimistic messages and server-confirmed messages reconcile without block loss or duplication.

## 5. Validation and Regression Coverage

- [x] 5.1 Add frontend tests for clipboard paste, inline preview, and mixed send payload construction.
- [x] 5.2 Add backend tests for mixed block validation and compatibility fallbacks.
- [x] 5.3 Add rendering tests for ordered text/image/link sequences.
- [x] 5.4 Add manual verification for `text [image] text [image]` composition and send.
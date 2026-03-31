## Why

The current chat composer only supports plain text plus a separate attachment tray. That is sufficient for sending text together with files, but it cannot preserve ordered mixed content such as `text [image] text [image]` and cannot accept pasted images directly from the clipboard.

Users need a composition model that matches modern chat behavior:

- paste an image with `Ctrl+V` directly into the input
- compose one message that mixes text, images, and embeds in order
- preview the draft in the same order it will be sent
- keep links readable and clickable inside mixed-content messages

Without a structured message-body contract, the current `content + attachments` model will continue to collapse all media to a detached attachment section and lose author intent.

## What Changes

- Add a structured mixed-message composer model that supports ordered content blocks for text and uploaded images/files.
- Add clipboard paste support so pasted images are inserted into the draft and uploaded through the existing upload-service flow.
- Add an inline draft preview surface that shows mixed content in order, for example `text [image] text [image]`.
- Extend message sending contracts so the client can submit ordered message blocks instead of only plain text plus a flat attachment array.
- Preserve safe plain-link rendering inside text blocks without introducing provider-specific embed unfurling in this change.

## Capabilities

### New Capabilities
- `mixed-message-composer`: Compose, preview, and submit one ordered message containing text segments, media blocks, and pasted-image content.

### Modified Capabilities
- `message-sending`: Message send contracts and validation must support ordered mixed-content payloads while preserving existing text-only and attachment-only behavior.

## Impact

- Frontend: `MessageInput`, file upload hooks, draft state, preview components, message rendering, and room preview derivation.
- Backend: message request/response contracts, message validation, persistence model for ordered content blocks, and preview generation.
- Upload flow: clipboard-pasted images reuse upload-service preparation and confirmation.
- Testing: unit/integration coverage for paste handling, ordered preview rendering, mixed send payloads, plain-link rendering, and backward compatibility with existing messages.
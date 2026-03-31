## Context

Current chat composition is modeled as:

- one textarea for message text
- one detached attachment queue
- one send action that submits `content` plus `attachments`

That model can represent:

- text only
- attachments only
- text plus attachments

It cannot represent author-defined ordering across content boundaries. A message such as `Here is the first screenshot [image] and here is the second one [image]` loses its sequence because attachments are rendered outside the text flow.

The frontend already recognizes `MIXED` as a message type, but the request contract and renderer still behave as `content + attachments`, not as an ordered document.

## Goals

- Preserve the authored order of text and uploaded assets inside one message.
- Support `Ctrl+V` image paste directly into the composer.
- Show a draft preview that mirrors the eventual render order.
- Keep URLs readable and clickable inside text blocks.
- Keep compatibility for existing text-only, attachment-only, and text-plus-attachment messages.

## Non-Goals

- Full rich-text editing with arbitrary formatting marks.
- Provider-specific embed support or URL unfurling.
- Replacing existing upload-service infrastructure.

## Proposed Model

Represent a sent message as an ordered list of blocks.

Candidate block types:

- `text`: freeform user text
- `asset`: uploaded image/video/file reference with upload-service metadata

For this MVP, only `text` and `asset` blocks are sent. URL embed unfurling is explicitly deferred.

Illustrative payload shape:

```json
{
  "roomId": "...",
  "replyToMessageId": "...",
  "blocks": [
    { "type": "text", "text": "Here is the first screenshot" },
    { "type": "asset", "attachment": { "publicId": "chat/attachments/id-1", "url": "https://res.cloudinary.com/..." } },
    { "type": "text", "text": "and here is the second one" },
    { "type": "asset", "attachment": { "publicId": "chat/attachments/id-2", "url": "https://res.cloudinary.com/..." } }
  ],
  "clientMessageId": "..."
}
```

The server can continue deriving:

- `type`: `TEXT`, `ATTACHMENT`, or `MIXED`
- `content`: plain-text summary or canonical extracted text for compatibility
- `attachments`: normalized asset references for legacy consumers

But the ordered `blocks` field becomes authoritative for render order.

## Composer Flow

1. User types text into the composer.
2. User pastes an image with `Ctrl+V` or attaches/drops media.
3. The composer flushes the current trailing text segment, then appends one or more asset blocks in authored order.
4. The asset uploads through upload-service prepare -> Cloudinary upload -> confirm.
5. The draft preview renders the same ordered blocks shown in the draft model.
6. On send, the client submits the structured block payload.

## Clipboard Paste Policy

- If clipboard contains one or more image files, the composer SHALL queue them as asset blocks.
- If clipboard contains text and image content together, the composer SHALL preserve typed text and append image blocks without discarding either.
- If image upload fails, the affected block SHALL surface an error and block final send until removed or retried.

## Link Policy

- URLs pasted or typed into text remain part of the text content.
- The renderer keeps those URLs clickable.
- Provider-specific metadata unfurling is deferred to a future change.

## Migration and Compatibility

- Existing messages without `blocks` remain renderable using current `content` and `attachments` fields.
- New mixed-content messages should expose compatibility summaries for room list previews and notifications.
- Preview generation should summarize blocks into concise text such as `Here is the first screenshot [Image] and here is the second one [Image]`.

## Risks

- Request/response contract expansion across frontend, websocket, and backend persistence layers.
- Caret-position behavior can become unstable if block insertion is implemented as ad hoc textarea mutation.
- Append-only composition is less flexible than a full rich editor, but it is substantially smaller and safer to ship first.

## Recommended Rollout

1. Introduce block model and backward-compatible APIs.
2. Implement append-only composer draft preview and paste-image support.
3. Implement renderer support for ordered asset blocks.
4. Backfill room-preview and notification summary logic for mixed-content messages.
5. Evaluate full caret-position insertion and link unfurling in a follow-up change.
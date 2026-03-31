## Context

Reply behavior needs corrected visual semantics: the reply snippet should be a distinct block above message content, styling should be clean (no border), only the reply message should be highlighted (not the original), the sender label should be unambiguous, and jump-to-original must load message context from the backend when the target is outside the loaded window.

## Goals / Non-Goals

**Goals:**
- Render reply snippet as a block element above message content, not inline in the text flow.
- Remove border/ring styling from the reply preview widget.
- Apply highlight only to the reply message row; do not highlight the original message row.
- Add a clear "Replying to [name]" label inside the reply snippet.
- When clicking a reply snippet and the original is not found in the DOM, call `getMessagesAround` to load context and then scroll to it.
- Preserve fallback rendering for missing/deleted originals and deduped reply send behavior.

**Non-Goals:**
- Redesigning full bubble styles unrelated to reply context.
- Changing backend message schema or adding new reply endpoint contracts beyond the `/around` query.
- Introducing threaded sub-views beyond existing linear room timeline.

## Decisions

1. Render reply snippet as a `<div>` block above message content, separated by a small gap.
   - Rationale: clear visual separation from the message body; avoids ambiguity of inline text mixing with reply attribution.
   - Alternative considered: keep inline. Rejected because user explicitly requested newline layout.

2. Remove `ring-1` / border from the reply preview widget; use only background color and left-accent for visual grouping.
   - Rationale: reduces clutter; focus stays on content.
   - Alternative considered: keep subtle border. Rejected per user requirement.

3. Highlight only the reply message row (not the original).
   - Rationale: the reply is the new content the user is focused on; highlighting the original alongside it creates noise and confusion.
   - Alternative considered: dual row highlight. Rejected per user requirement to only mark the new reply message.

4. Label reply snippet as "Replying to [name]" with a ↩ icon or similar affordance.
   - Rationale: plain sender name alone is ambiguous about direction; explicit label removes confusion.
   - Alternative considered: just show avatar + name. Rejected because relationship direction was unclear.

5. Add `GET /api/v1/messages/around?roomId&messageId&limit` backend endpoint; add `getMessagesAround` in `chat.service.ts`; add `loadMessagesAround(roomId, messageId)` action in `ChatProvider` that replaces the room's current message window with the around-result.
   - Rationale: jump-to-original must work even when original is outside the loaded history window; this requires a server-side context fetch by messageId.
   - Alternative considered: frontend-only using seq range. Rejected because client does not always know the target's seq.

6. Preserve existing optimistic dedupe reconciliation by `clientMessageId`.
   - Rationale: interactive linking depends on one stable rendered node per logical reply.
   - Alternative considered: rely on post-render dedupe only by messageId. Rejected due to optimistic/server ID mismatch.

## Risks / Trade-offs

- [Context window replacement] `loadMessagesAround` replaces the in-memory window, which loses scroll position continuity. → Mitigation: after replacement, scroll to target and apply temporary jump-target highlight.
- [Missing original] If the backend cannot find the original (deleted), `getMessagesAround` fails. → Mitigation: catch error and display "cannot load the original message" fallback.
- [Grouped layout] Block reply snippet adds vertical height to message rows. → Mitigation: keep snippet compact with `line-clamp-1` and constrained max width.

## Migration Plan

1. Add `GET /messages/around` backend endpoint and service method.
2. Add `getMessagesAround` in `chat.service.ts` and `loadMessagesAround` in `chat.store.tsx`.
3. Update `MessageItem`: reply snippet becomes a block div above content; remove border; add "Replying to [name]" label.
4. Update `MessageList`: remove highlight from original message; keep highlight only on reply message; wire `handleJumpToMessage` to call `loadMessagesAround` when target is not in DOM.
5. Update `ReplyPreview` (composer): apply matching no-border block layout.
6. Add/adjust tests and verify TypeScript compile.

## Open Questions

- Should `loadMessagesAround` preserve or discard previous room history (currently: replaces to keep window bounded)?
- Should jump action show a toast/notification when context is loaded from server?
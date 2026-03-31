## Context

Current message history responses return reactions as aggregate `(emoji, count)` values and do not include whether the authenticated user has reacted to each emoji. Frontend optimistic/realtime logic depends on ownership to choose correct add/remove behavior. Without ownership from history, initial UI state is uncertain and first-toggle behavior can diverge from server truth.

The backend already has reaction rows keyed by `(message_id, user_id, emoji)` and a toggle pipeline that emits realtime reaction updates. The missing part is query-time ownership projection in message history responses.

## Goals / Non-Goals

**Goals:**
- Add per-emoji ownership for the current user in message history responses.
- Preserve current aggregate count semantics.
- Keep toggle and published reaction event behavior idempotent and aligned with ownership.
- Maintain backward compatibility for existing consumers.

**Non-Goals:**
- Redesigning websocket event schema in this change.
- Introducing client-side deduplication policies in backend scope.
- Changing auth/session model.

## Decisions

1. Extend reaction response DTO with optional ownership flag.
- Decision: Add `reactedByMe` (boolean) to reaction response model used by message history APIs.
- Rationale: Directly maps to frontend need and avoids additional round trips.
- Alternative considered: New endpoint for "my reactions by message".
  - Rejected: adds extra request, synchronization complexity, and larger integration surface.

2. Compute ownership at query layer using current user context.
- Decision: Add repository projection/query that returns `(messageId, emoji, count, reactedByMe)` or equivalent join/exists-based ownership per emoji for the current user.
- Rationale: Ownership should be determined at source-of-truth query time, not inferred in mapper.
- Alternative considered: Mapper-level post-processing using full reaction row fetch.
  - Rejected: inefficient for large histories and duplicates existing query logic.

3. Preserve response compatibility.
- Decision: Keep `emoji` and `count` unchanged; add new field as additive.
- Rationale: Non-breaking for current consumers that ignore unknown fields.
- Alternative considered: Replace reaction object shape.
  - Rejected: unnecessary breaking change.

4. Verify idempotent toggle consistency through tests.
- Decision: Add/extend tests for repeated toggle sequences and ownership transitions in history and command paths.
- Rationale: Guards against regressions where ownership or count drift under rapid toggles.
- Alternative considered: Rely only on integration smoke tests.
  - Rejected: insufficient coverage for edge transitions.

## Risks / Trade-offs

- [Risk] Ownership query may increase DB complexity for large message windows.
  - Mitigation: Use indexed `EXISTS`/join patterns scoped by `messageIds` + `currentUserId`; benchmark query plan.

- [Risk] Null/absent ownership values can be misinterpreted by clients.
  - Mitigation: Emit explicit boolean for every returned emoji row.

- [Risk] Eventual consistency gap between history read and realtime updates.
  - Mitigation: Maintain existing realtime stream; ensure history response is internally consistent at read time.

## Migration Plan

1. Add/extend projection and repository query for ownership-aware reaction summaries.
2. Add `reactedByMe` to reaction response DTO and mapper wiring.
3. Update message query service to supply `currentUserId` into reaction summary query.
4. Add tests for response mapping and toggle idempotent transitions.
5. Deploy backend update; frontend can consume additive field without coordinated downtime.
6. Rollback strategy: revert DTO/query changes; additive field removal returns prior behavior.

## Open Questions

- Should ownership-aware projection be reused by all message list endpoints, or only latest/before endpoints initially?
- Should reaction-updated websocket payload also include post-toggle aggregate snapshot in a follow-up change?

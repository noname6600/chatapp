## Context

`ChatMessage`, `ChatAttachment`, `ChatMessageMention`, and `ChatReaction` all carry `createdAt`. The current implementation is mixed:
- message/attachment/mention rely on JPA `@PrePersist` hooks
- reaction currently sets `createdAt` explicitly in `PersistReactionStep` and also has `@PrePersist`

This inconsistency makes timestamp ownership unclear and leads to bugs where application flow expects a timestamp before persistence (even though request DTOs do not carry any time field). The correct source of truth should be server persistence lifecycle, not request payload.

## Goals / Non-Goals

**Goals:**
- Standardize timestamp generation across all four entities as server-managed persistence concern
- Guarantee non-null `createdAt` after persistence for all entities in message/reaction flows
- Ensure event payloads always use persisted entity timestamps
- Remove duplicated timestamp assignment logic from pipeline steps
- Add regression tests covering timestamp assignment and event publication timing

**Non-Goals:**
- Client-provided timestamp support
- Changing timezone representation from `Instant`
- Changing existing business semantics of edit/delete timestamps

## Decisions

### 1. Single source of truth: entity lifecycle for `createdAt`
Use entity-level `@PrePersist` as the canonical creator of `createdAt` for message, attachment, mention, and reaction. Pipeline/application code MUST NOT assign `createdAt` manually.

Alternative considered: assign `Instant.now()` in each pipeline step before save.
Rejected due to duplication, drift risk, and harder consistency guarantees.

### 2. Reaction pipeline cleanup
Remove explicit `.createdAt(Instant.now())` in `PersistReactionStep` so reaction follows the same pattern as other entities.

Alternative considered: keep explicit assignment for reaction only.
Rejected because it preserves inconsistency and makes behavior dependent on call path.

### 3. Event publishing must use persisted entities only
Publish steps must read timestamps from persisted entities (`context.getSavedMessage()`, saved reaction entity) to ensure non-null/accurate event values.

Alternative considered: using context request-time or pre-save objects.
Rejected because timestamps are finalized at persistence time.

### 4. Defensive test coverage for null-time regressions
Add tests that verify:
- Request DTOs can omit timestamps
- Persisted message/attachment/mention/reaction have non-null `createdAt`
- Event payload timestamps are populated from persisted records

## Risks / Trade-offs

- [Risk] Slight timestamp value shift compared to pre-save `Instant.now()` in reaction path -> Mitigation: all timestamps become persistence-aligned, which is desired and more consistent.
- [Risk] Existing tests may assert pre-save timestamp behavior -> Mitigation: update tests to assert non-null and monotonic flow constraints, not exact object-construction time.
- [Risk] If persistence lifecycle is bypassed in future custom SQL writes, timestamps may be null -> Mitigation: keep writes through repositories and add integration tests.

## Migration Plan

1. Remove manual reaction timestamp assignment in pipeline step
2. Verify all entity `@PrePersist` hooks remain active and consistent
3. Update event-publish steps/tests to use persisted entities
4. Run module tests and compile checks
5. Deploy as backward-compatible server-only fix (no API contract changes)

## Open Questions

- Should we migrate to Spring Data auditing (`@CreatedDate`) for all entities in future for cross-module consistency? (not required for this change)

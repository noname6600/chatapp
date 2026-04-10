## Context

Private chat startup depends on fetching basic participant profile data through internal HTTP clients. The current consumer DTO mapping is strict and fails when upstream services add non-breaking JSON fields (for example, `username`). This causes deserialization exceptions and blocks private chat creation from friend-list click flow.

Constraints:
- Preserve current required profile fields for chat startup (`accountId`, `displayName`, `avatarUrl`).
- Avoid introducing breaking API changes between user-service, friendship-service, and chat-service.
- Keep behavior backward and forward compatible for additive response fields.

## Goals / Non-Goals

**Goals:**
- Make private-chat startup resilient to additive fields in upstream profile JSON.
- Keep strict validation of required fields while ignoring unknown optional fields.
- Add regression tests for friend-click private chat startup path.

**Non-Goals:**
- Redesign profile APIs or rename existing profile fields.
- Introduce cross-service schema registry as part of this fix.
- Change frontend private-chat interaction behavior.

## Decisions

1. Use tolerant DTO deserialization on profile-consumer models in private-chat startup path.
Rationale: Annotating the specific consumer DTO (or equivalent local mapping configuration) with ignore-unknown semantics addresses this incident with minimal blast radius.
Alternatives considered:
- Global ObjectMapper `FAIL_ON_UNKNOWN_PROPERTIES=false`: rejected due to broad side effects.
- Strict shared schema contract enforcement before runtime: rejected for incident-level response time.

2. Preserve required-field semantics for profile data used to start private chat.
Rationale: Ignoring unknown fields must not mask missing required fields (`accountId`, `displayName`, `avatarUrl`) that private chat logic depends on.
Alternatives considered:
- Making all fields nullable and validating later: rejected due to weaker correctness and harder diagnostics.

3. Add regression tests at service/integration boundaries for additive-field payloads.
Rationale: This failure happened at runtime decode stage, so tests must cover real decoding path with representative JSON payloads.
Alternatives considered:
- Unit tests only on DTO parsing helpers: rejected because they may miss Feign/decoder integration behavior.

## Risks / Trade-offs

- [Risk] Overly permissive deserialization could hide upstream contract drift. -> Mitigation: keep required field assertions and add structured warning logs when required fields are absent.
- [Risk] Fix applied in one consumer path but not others. -> Mitigation: inventory profile consumer DTOs in private chat startup chain and add coverage for each decode boundary involved.
- [Trade-off] Localized DTO-level fix is fast but may duplicate annotations across services. -> Mitigation: track follow-up refactor to shared tolerant profile DTO conventions if repeated.

## Migration Plan

- Implement DTO deserialization tolerance in the private-chat startup consumer path.
- Add/update tests for additive profile JSON fields and for required-field preservation.
- Deploy as backward-compatible patch release.
- Rollback strategy: revert DTO mapping and tests if unexpected side effects appear; no data migration required.

## Open Questions

- Which exact service boundary currently throws the decode exception in production path (chat-service direct call vs friendship-service mediated call)?
- Should consumer services emit a compatibility metric when unknown profile fields are encountered?

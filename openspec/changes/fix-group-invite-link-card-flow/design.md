## Context

The invite experience spans multiple layers (chat send pipeline, room endpoints, message rendering, room header actions, and user profile popup actions). Current behavior has three critical failures:

- Invite card sends can be rejected by message validation when the message body does not contain text/attachment blocks.
- Invite endpoint requests can miss controller mapping and fall through to static-resource handling.
- Invite actions are fragmented and confusing (separate “send invite card” action plus “invite members”, and “invite to server” wording in profile popup).

The requested behavior is a unified group invite flow where invite entry points consistently emit a joinable invite card that includes join link/code context.

## Goals / Non-Goals

**Goals:**
- Make ROOM_INVITE-only messages valid in backend send validation.
- Ensure invite endpoint mapping is explicit and reachable, with no static-resource fallback for API paths.
- Consolidate room-header invite UX so “Invite Members” is the invite-card sending trigger.
- Convert profile popup “Invite to server” into “Invite to group” with group-room hover/flyout selection.
- Standardize invite card display as a link-oriented card with join-code context and one-click join behavior.
- Preserve idempotent join outcomes (joined/unavailable/joinable) on card actions.

**Non-Goals:**
- Replacing existing room membership authorization policy.
- Introducing a new room-invite transport model outside message cards.
- Redesigning unrelated chat composer or message list layouts.
- Changing gateway routing strategy globally beyond necessary invite path alignment.

## Decisions

### 1. Treat ROOM_INVITE blocks as first-class message content in validation
- Decision: update message validation to accept a message whose non-empty content is a valid ROOM_INVITE block (roomId required; metadata optional).
- Rationale: invite cards are semantic content, not attachments. Requiring extra text/attachment creates false validation failures.
- Alternatives considered:
  - Force a text fallback for every invite card: rejected due to noisy UX and brittle client coupling.
  - Bypass validation for invite paths only: rejected because it fragments message contract rules.

### 2. Keep invite-card send and join routing explicit under API controllers
- Decision: ensure invite-related POST endpoints are declared on controller paths under `/api/v1/...` and covered by tests so missing mappings fail fast.
- Rationale: current no-resource fallback indicates request path is not matched by controller mappings.
- Alternatives considered:
  - Handle fallback via resource handler customization: rejected because API requests should not rely on static-resource pipeline behavior.

### 3. Consolidate room-header invite UX into one source action
- Decision: remove the separate “Send Invite Card” option and wire “Invite Members” to emit invite card payloads.
- Rationale: two parallel invite actions are confusing and create duplicated logic.
- Alternatives considered:
  - Keep both actions with different labels: rejected due to duplicate intent and higher maintenance.

### 4. Use link/code-oriented invite card presentation
- Decision: invite cards display join link semantics with code context (human-readable and clickable join action).
- Rationale: requested UX prioritizes quick click-to-join with clear code-based group context.
- Alternatives considered:
  - Plain textual room metadata card: rejected because it obscures the join affordance.

### 5. Add profile-popup group invite selector as a contextual action
- Decision: rename popup action to “Invite to group”, and on hover/flyout show current user’s eligible group rooms; selecting one sends invite card.
- Rationale: aligns language with actual domain and provides direct cross-surface invite flow.
- Alternatives considered:
  - Modal-only room picker: rejected as slower and heavier for quick invite action.

## Risks / Trade-offs

- [Risk] ROOM_INVITE validation broadening may admit malformed payloads if checks are too loose -> Mitigation: enforce `roomId` required and keep strict block-type validation.
- [Risk] UI consolidation may break users who relied on removed action placement -> Mitigation: preserve “Invite Members” location and update label/tooltips for clarity.
- [Risk] Hover/flyout group list may become stale if room store not loaded -> Mitigation: load/sync groups before displaying selectable invite targets and show empty-state fallback.
- [Risk] Endpoint remapping can regress FE calls if path contracts differ across services/gateway -> Mitigation: add targeted integration/contract tests for invite send/join endpoints.

## Migration Plan

1. Update backend send validation and endpoint mappings for invite paths.
2. Update frontend action wiring (room header + profile popup selector).
3. Update invite-card rendering to link/code presentation while preserving join-state handling.
4. Run targeted FE/BE tests for invite send, render, join, and endpoint routing.
5. Manual validation: invite from room header and profile popup both produce joinable invite cards.

Rollback:
- Revert frontend invite-entry wiring and keep prior action surface.
- Revert validation/path changes to previous known-good behavior if routing regressions occur.

## Open Questions

- Should invite card display room invite code directly, deep link only, or both in the same card?
- In profile popup, should group list include private rooms user owns, or strictly group rooms?
- Should invite send from profile popup open confirmation/toast after dispatch, or remain silent success?
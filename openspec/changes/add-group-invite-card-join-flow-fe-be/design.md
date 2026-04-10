## Context

The current chat flow supports text/media messages but does not provide a built-in room invite mechanism inside conversation. Users must manually share room details, which is inconsistent and lowers room join conversion. This change spans frontend and backend:

- FE must compose and render a structured invite card message block.
- BE must validate invite-target room visibility and allow idempotent join from the card action.
- Existing message and room join contracts must remain backward compatible for non-invite messages and current join flows.

Constraints:
- Reuse existing room membership and authorization model.
- Keep invite flow safe for private/closed rooms (no unauthorized joins).
- Preserve existing message timeline behavior and optimistic send patterns.

## Goals / Non-Goals

**Goals:**
- Add an in-chat action to send group invite cards.
- Persist invite card as structured message content and broadcast through existing message realtime flow.
- Render invite cards consistently in FE with room summary and actionable join button.
- Implement join-from-card endpoint/command with authorization and idempotency.
- Provide deterministic card states: joinable, already joined, unavailable/denied.

**Non-Goals:**
- Replacing existing join-by-code or direct add-member administrative flows.
- Implementing long-lived public invite links beyond message-scoped invite metadata.
- Building a full invitation management dashboard (accept/decline history, revoke list UI).
- Cross-tenant federation or external-share invitation handling.

## Decisions

### 1. Represent invite as structured message block in existing message payload
- Decision: Add a new message block type (e.g., `ROOM_INVITE`) with room metadata (`roomId`, `roomName`, `roomAvatar`, optional `memberCount`).
- Rationale: Keeps invite cards first-class in timeline and reuses current message persistence/realtime/event pipelines.
- Alternative considered: Separate invitation entity with timeline references. Rejected due to higher sync complexity and dual-source rendering concerns.

### 2. Keep join action server-authoritative and idempotent
- Decision: FE calls a dedicated join endpoint with `roomId` from invite payload; BE validates requester eligibility and returns success even if already member.
- Rationale: Prevents duplicate membership rows and avoids client race conditions on repeated clicks.
- Alternative considered: Client-side membership pre-check only. Rejected because it is non-authoritative and race-prone.

### 3. Reuse existing room membership policy checks
- Decision: Join-from-card passes through the same room policy checks used by room join logic (visibility, block rules, room type constraints).
- Rationale: Ensures consistent security semantics and avoids policy divergence.
- Alternative considered: Dedicated invite-policy bypass. Rejected for security risk and policy drift.

### 4. FE render contract is data-driven with graceful degradation
- Decision: Message renderer detects `ROOM_INVITE` block and renders a card; if payload is incomplete, card enters unavailable state rather than crashing.
- Rationale: Resilient rendering for stale/deleted/malformed data and backward compatibility in mixed deployments.
- Alternative considered: Strict render failure on invalid payload. Rejected because it can break timeline rendering.

### 5. Keep payload backward compatible
- Decision: Extend message schema in an additive way so legacy clients ignore unknown block types.
- Rationale: Supports incremental FE/BE rollout without hard coordination.
- Alternative considered: versioned message API cutover. Rejected for unnecessary migration overhead.

## Risks / Trade-offs

- [Risk] Invite payload can become stale (room renamed/deleted) -> Mitigation: resolve live room state on join attempt and show unavailable state when invalid.
- [Risk] Unauthorized users could attempt join using copied payload -> Mitigation: enforce server-side authorization and membership policy checks on every join request.
- [Risk] Repeated clicks or network retries create duplicate operations -> Mitigation: idempotent join command and stable response contract.
- [Risk] FE/BE rollout mismatch for new block type -> Mitigation: additive schema and fallback renderer behavior.
- [Trade-off] Embedding room metadata in message improves UX but duplicates snapshot data -> Accepted; metadata is treated as display snapshot while join uses authoritative room lookup by `roomId`.

## Migration Plan

1. Add backend support for invite-card payload parsing/storage and realtime propagation.
2. Add join-from-card endpoint/command with policy checks and idempotent behavior.
3. Add FE invite-card composer action and message renderer support.
4. Roll out FE join action states and navigation integration.
5. Validate with integration tests (send invite -> recipient sees card -> click join -> room entry).

Rollback:
- FE: disable invite send action and hide invite card action rendering.
- BE: keep additive payload fields tolerated but stop creating invite blocks; join endpoint can be disabled while existing flows remain intact.

## Open Questions

- Should invite cards expire automatically after a configured time window?
- Are private DM contexts allowed to send group invite cards, or only group channels?
- Should blocked relationships prevent join-from-card even when room policy allows join?
- Should invite cards show dynamic member count from live room data or fixed snapshot from message payload?

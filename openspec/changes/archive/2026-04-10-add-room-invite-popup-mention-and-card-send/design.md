## Context

Room invite UX is currently fragmented: users must manually discover invite code, manually decide whom to invite, and do not have an integrated way to send a structured invite card into the conversation. The requested behavior combines three coordinated concerns in one popup opened from room actions: shareable room code, user discovery with mention-compatible identity context, and one-click invite card emission.

This is cross-cutting because it spans frontend modal state, room/user data loading, message composition/send semantics, and invite card rendering/join behavior in the chat timeline.

## Goals / Non-Goals

**Goals:**
- Provide a popup from room "more" actions that shows current room invite code and copy affordance.
- Provide a user list with identity context (display name, username, about) suitable for mention-aware invite targeting.
- Provide invite button actions that send a room invite card message with room metadata needed for join flow.
- Keep invite-card sending compatible with existing message pipeline and chat rendering patterns.

**Non-Goals:**
- Rebuild the existing mention parser/token model in message composer.
- Redesign the entire room header or room settings surface.
- Introduce a new transport mechanism outside existing message send APIs.
- Change authorization rules for who is allowed to invite members beyond current room policies.

## Decisions

1. Add a dedicated frontend invite modal state and trigger from room more-menu.
Rationale: Isolates invite workflow from composer and keeps interactions discoverable without overloading main chat surface.
Alternatives considered:
- Inline panel in room sidebar: rejected due to constrained space and persistent clutter.
- Reusing generic profile modal: rejected because room-code copy and invite-card send actions are room-scoped.

2. Use existing room/user APIs where possible; extend response shape only where required (for example `about` visibility in invite list rows).
Rationale: Minimizes backend churn and rollout risk.
Alternatives considered:
- New dedicated invite-candidates endpoint: deferred unless current APIs cannot provide needed fields efficiently.

3. Model invite action as message-sending extension with invite-card payload.
Rationale: Keeps timeline history/audit behavior and recipient interaction aligned with existing message delivery semantics.
Alternatives considered:
- Out-of-band notification-only invite: rejected because request explicitly needs invite card in room conversation.

4. Keep mention-compatibility at identity layer (username/displayName cues) rather than literal @token insertion in this popup flow.
Rationale: Popup sends invite cards, not plain text mentions; identity consistency still matters for targeting and user recognition.
Alternatives considered:
- Force composer prefill with @mentions from popup: rejected as extra interaction step and mismatch with one-click invite-card goal.

## Risks / Trade-offs

- [Risk] API payload mismatch between frontend invite-card payload and backend message validation. -> Mitigation: define explicit card payload contract in spec and add end-to-end tests.
- [Risk] Large user lists could degrade popup performance. -> Mitigation: enforce search/filter and pagination/limit in candidate loading.
- [Risk] About text visibility/privacy concerns in popup list. -> Mitigation: reuse current profile visibility rules and avoid exposing fields unavailable elsewhere.
- [Trade-off] Reusing message pipeline reduces new infrastructure but adds conditional logic for invite-card type. -> Mitigation: isolate type-specific validation/mapper paths.

## Migration Plan

- Implement frontend modal entrypoint and local state behind room action trigger.
- Implement invite candidate list + copy-code behavior.
- Add invite-card send payload support in frontend and backend message handling.
- Add/adjust message rendering and join action handling for invite cards.
- Roll out as backward-compatible feature; if issues occur, disable frontend trigger and ignore invite-card type on backend.

## Open Questions

- Should invite candidate list include only friends, all known users, or all non-members discoverable by username?
- Is invite action single-recipient at a time or multi-select bulk send in first iteration?
- Should invite-card join action validate room access only at click time or also at send time for each target?

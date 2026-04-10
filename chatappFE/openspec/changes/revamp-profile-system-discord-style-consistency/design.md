## Context

`ProfileSettingsPage.tsx` and `UserPopup.tsx` already contain useful profile behaviors, but they diverge in visual hierarchy, interaction priorities, and action semantics. The app also has multiple entry points for profile identity surfaces (avatar/name/mention), and those do not yet guarantee one consistent profile-card contract. The requested UX requires both a visual redesign and an interaction-system decision that standardizes self-vs-other outcomes without introducing new backend contracts.

## Goals / Non-Goals

**Goals:**
- Deliver a Discord-like profile settings experience while preserving existing editable profile/security capabilities.
- Standardize profile card structure across avatar/icon/name/mention click entry points.
- Enforce deterministic interaction behavior:
  - self click presents direct settings entry action
  - other click presents mini-chat input (text + emoji/icon capable), with Enter launching full chat context.
- Reuse existing private-chat flow (`startPrivateChatApi`, room reload, `setActiveRoom`, navigation to `/chat`) to minimize architecture churn.

**Non-Goals:**
- Building a new standalone direct-message protocol or backend API.
- Replacing the entire chat composer with a rich editor in the mini profile input.
- Reworking unrelated room/friend list architectures.
- Introducing a brand-new global theming system beyond targeted profile-surface consistency.

## Decisions

### Decision: Define a shared profile-surface contract used by all profile entry points
Profile overlays/cards should use the same structure, identity fields, and action zones regardless of entry source (avatar/name/mention). This avoids source-specific UI drift.

Alternative considered: keep separate layout variants for each entry source. Rejected because that perpetuates inconsistency and violates the requested "exactly look like that profile in all system" goal.

### Decision: Keep self-vs-other behavior explicit and split at the overlay action layer
Self-profile overlays should prioritize a direct navigation button to profile settings. Other-user overlays should prioritize mini chat entry and support Enter-to-chat transition.

Alternative considered: same action set for self and others. Rejected because the user requested distinct behavior, and self-chat is lower-value than immediate settings entry.

### Decision: Reuse existing private-chat and chat-store orchestration for mini-chat transition
For other-user mini chat, pressing Enter should use the current private-chat room activation path and then navigate to `/chat`, preserving existing room state behavior.

Alternative considered: send transient mini messages without room transition. Rejected because requirement explicitly asks for Enter to jump to chat and aligns with existing architecture.

### Decision: Extend `discord-ui-features` rather than creating a disconnected styling spec
The visual language requirement belongs in the shared design capability already used for Discord-like polish, while behavior-specific requirements belong in new profile-focused capabilities.

Alternative considered: keep style rules isolated only in new profile capability. Rejected because style consistency is cross-surface and should remain tied to the centralized visual capability.

## Risks / Trade-offs

- [Risk] Unified profile surfaces may require touching many components at once. → Mitigation: define a reusable profile card contract and migrate entry points incrementally behind tests.
- [Risk] Mini-chat Enter behavior may conflict with input composition edge cases. → Mitigation: guard key handling to trigger only on valid Enter and preserve IME-safe behavior where applicable.
- [Risk] Discord-like styling could diverge from existing pages if not tokenized consistently. → Mitigation: reuse existing spacing/typography primitives and central profile presentation utilities.
- [Risk] Interaction split for self vs other could regress existing popup actions. → Mitigation: add targeted tests for self button routing and other-user mini-chat flow.

## Migration Plan

1. Implement profile settings redesign and shared profile-card primitives in frontend components.
2. Apply shared profile rendering and action model to profile overlay entry points.
3. Wire Enter key in other-user mini chat to existing private-chat activation + chat navigation flow.
4. Run targeted UI tests for profile settings, popup behavior, and cross-entry rendering consistency.
5. Rollback strategy: revert profile surface component changes while preserving existing API/store contracts.

## Open Questions

- Should the mini chat support only Unicode emoji input via keyboard/paste initially, or include an explicit emoji picker icon in first pass?
- Which mention-rendering surfaces are highest priority for parity in phase one (message body mentions, room member lists, notification mentions)?

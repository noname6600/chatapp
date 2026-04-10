## Why

The new room member list UI feels visually inconsistent and less usable than the previous version, while the user wants to keep the current room list unchanged. This change restores a cleaner, more familiar member-list presentation and interaction model without altering room list behavior.

## What Changes

- Introduce a dedicated room member list UI capability that defines layout, typography, spacing, and interaction behavior for member entries.
- Keep room list capabilities unchanged; no requirement in this change modifies room list grouping, sorting, unread badges, or room-item visuals.
- Define clear behavior for member row rendering (avatar, display name, username, presence indicator, role badge where available) and empty/loading states.
- Ensure member list behavior remains responsive and accessible on desktop and mobile widths.

## Capabilities

### New Capabilities
- `room-member-list-ui`: Defines requirements for the room member list visual structure, interaction rules, and responsive behavior.

### Modified Capabilities
- None

## Impact

- Frontend member-list components and styles in `chatappFE/src/components` and `chatappFE/src/pages`.
- Potential updates to shared UI tokens/styles used by member list only.
- No backend API changes and no data contract changes.
- No changes to existing room list capabilities in `openspec/specs/room-list-*`.

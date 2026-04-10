## Context

The current room member list redesign reduced perceived readability and visual quality for users who preferred the previous layout. The requested direction is explicit: keep the room list as-is and focus only on room member list UI improvements.

The change is frontend-only and does not require API contract updates. Existing presence and member data sources already provide the needed fields (display name, username, avatar, presence state, and membership context).

## Goals / Non-Goals

**Goals:**
- Restore a cleaner, familiar room member list visual hierarchy.
- Ensure member rows are easy to scan (avatar, primary name, secondary metadata, presence/role chips).
- Preserve current room list behavior and visuals with no side effects.
- Define responsive and accessibility expectations for keyboard and screen-reader usage.

**Non-Goals:**
- Reworking room list sections, sorting, unread badges, or room tile styling.
- Changing backend endpoints, payload shapes, or presence semantics.
- Introducing new third-party UI frameworks.

## Decisions

### Decision: Isolate styling and structure changes to room member list components only
- **Why:** Prevent accidental regressions to room list UI and existing sidebar behavior.
- **Alternative considered:** shared list component refactor for room list + member list; rejected because it increases risk and scope.

### Decision: Define explicit member row information architecture
- **Why:** Prior UI complaints were about aesthetics and scannability; a stable row contract (primary and secondary text + right-side status affordances) keeps implementation and QA objective.
- **Alternative considered:** purely visual rewrite without layout rules; rejected because requirements would be subjective and hard to validate.

### Decision: Keep data flow unchanged and style via existing FE module boundaries
- **Why:** User asked for UI rollback-style improvements, not behavior changes.
- **Alternative considered:** adding server-provided presentation flags; rejected as unnecessary coupling.

## Risks / Trade-offs

- [Risk] The "old look" expectation may be interpreted differently by stakeholders -> Mitigation: encode concrete UI acceptance criteria in spec scenarios (spacing, typography hierarchy, and states).
- [Risk] CSS updates could unintentionally affect other lists if selectors are too broad -> Mitigation: use member-list scoped class names/module styles and visual regression checks.
- [Risk] Responsive changes might hide metadata on narrow viewports -> Mitigation: specify required fallback order and truncation behavior in tasks/tests.

## Migration Plan

1. Identify member-list UI entry points and isolate style scope.
2. Implement new row layout and state styling (default, hover, active, loading, empty).
3. Validate desktop and mobile rendering and keyboard navigation.
4. Run FE lint/tests and targeted manual QA in chat room view.
5. Release as frontend-only change with no backend rollout dependency.

## Open Questions

- Should role badges (owner/admin/member) always render, or only when role differs from default member?
- Should presence text labels be visible by default or only shown via tooltip while keeping color/indicator dot always visible?

## 1. Discover and isolate room member list scope

- [ ] 1.1 Identify room member list rendering components/hooks in `chatappFE/src` and confirm room list components are separate
- [ ] 1.2 Add or refine component-level style scope so member list CSS changes cannot affect room list selectors

## 2. Implement room member row redesign

- [ ] 2.1 Update member row structure to consistently render avatar, primary display name, secondary username/metadata, and right-aligned status or role affordance
- [ ] 2.2 Add overflow handling for long names (truncate/ellipsis) while preserving alignment of status/role indicators
- [ ] 2.3 Apply visual refinements (spacing, typography hierarchy, hover/active states) to match the intended old-style readability

## 3. State handling and responsiveness

- [ ] 3.1 Implement deterministic loading placeholder state for room member list data fetch
- [ ] 3.2 Implement explicit empty-state message when no members are available
- [ ] 3.3 Validate narrow viewport behavior so avatar and primary name remain visible and rows remain tappable

## 4. Regression and acceptance checks

- [ ] 4.1 Verify room list behavior remains unchanged (grouping, sorting, unread badges, and room-item visuals)
- [ ] 4.2 Add/update frontend tests (component or integration) for member row hierarchy, truncation, and loading/empty states
- [ ] 4.3 Run FE lint/tests and perform manual QA in room view for desktop and mobile breakpoints

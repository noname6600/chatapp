## Context

The chat frontend currently has page-shell and chat-content responsibilities split across multiple containers, which causes inconsistent scroll ownership and makes scroll-up pagination behavior fragile. Header, profile identity, and logout are distributed across different areas, which reduces navigation clarity and is not optimized for small screens.

## Goals / Non-Goals

**Goals:**
- Establish a single chat shell structure where the left sidebar owns navigation and identity controls.
- Remove top header usage for chat page and move account/session actions into the sidebar.
- Ensure sidebar behavior is responsive with explicit open/close controls on small viewports.
- Isolate chat message scrolling to the message list container so outer wrappers do not interfere with upward pagination and scroll restoration.

**Non-Goals:**
- No backend API or websocket protocol changes.
- No redesign of message bubble rendering, reactions, or composer behavior.
- No global design-system refactor outside chat shell and sidebar.

## Decisions

- Use sidebar-as-shell-navigation pattern for chat page.
Rationale: centralizes identity, room navigation, and session actions in one predictable area and removes duplicated header concerns.
Alternative considered: keep top header and only add sidebar controls. Rejected because it preserves split responsibility and does not address shell complexity.

- Place profile block at top and logout action at fixed bottom of sidebar.
Rationale: profile visibility is immediate, while logout remains consistently discoverable and separated from room actions.
Alternative considered: keep logout in dropdown/header. Rejected due to poorer discoverability and mobile ergonomics.

- Use responsive sidebar state with explicit toggle button and overlay behavior on narrow screens.
Rationale: allows room content focus on mobile while still providing quick navigation access.
Alternative considered: always-visible compressed sidebar. Rejected due to limited message area and reduced usability.

- Define strict scroll ownership boundaries: page/layout container remains non-scrolling for chat content; message list container owns vertical scroll.
Rationale: prevents nested scroll conflicts and makes upward pagination trigger conditions deterministic.
Alternative considered: permit both page and list scroll. Rejected because it causes ambiguous scrollTop state and pagination regressions.

## Risks / Trade-offs

- [Risk] Sidebar toggle state may become inconsistent across route changes. -> Mitigation: scope sidebar open state to chat page lifecycle and reset on room/page transitions.
- [Risk] Fixed bottom logout area may overlap content on very short viewports. -> Mitigation: use flex column with reserved footer area and overflow handling in middle section.
- [Risk] Removing header may regress existing shortcuts/actions. -> Mitigation: inventory current header actions and migrate required controls to sidebar before removal.
- [Risk] Scroll isolation may affect unread-boundary positioning. -> Mitigation: validate unread boundary and jump-to-latest behavior after container refactor.

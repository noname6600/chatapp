## Context

Chat message rendering currently mixes room/list orchestration and single-item UI concerns across `MessageList` and `MessageItem`. This causes broad prop surfaces (`allMessages`, user maps, room-wide values) to leak into item rendering and makes pagination, grouping, and reply-resolution behavior harder to test independently.

## Goals / Non-Goals

**Goals:**
- Establish explicit ownership boundaries between list-level orchestration and single-message rendering.
- Keep list-level concerns (`setActiveRoom`, pagination, scroll lifecycle, grouping, item derivations) in `MessageList`.
- Keep `MessageItem` focused on rendering a single message and local interaction state.
- Reduce high-coupling props passed from list to item.
- Introduce fine-grained UI subcomponents under `MessageItem` for maintainability.

**Non-Goals:**
- Changing backend APIs or websocket event contracts.
- Changing established pagination behavior or grouping rules.
- Rewriting chat store architecture beyond what is necessary for boundary enforcement.

## Decisions

1. Move all list-wide orchestration to `MessageList`.
- Rationale: room initialization, pagination, and grouping require full-list context and should have one owner.
- Alternative considered: keep mixed ownership and add helper utilities. Rejected because ownership ambiguity would remain.

2. Restrict `MessageItem` inputs to single-item presentation data.
- Rationale: avoids room/list coupling and simplifies isolated rendering tests.
- Alternative considered: continue passing `allMessages`, `users`, and room context. Rejected due to prop bloat and hidden dependencies.

3. Resolve reply relationships before rendering item.
- Rationale: reply lookup requires list context and should be derived in list layer (or a list-scoped hook), then passed as `repliedMessage`.
- Alternative considered: item-side `find(...)` on full list. Rejected due to repeated work and leaky list dependency.

4. Read user/current-account context directly in item layer when needed.
- Rationale: avoids parent prop drilling for global presentation context.
- Alternative considered: always pass user/current account via props. Rejected due to unnecessary wiring and broader callsites.

5. Split `MessageItem` UI responsibilities into smaller presentation components.
- Rationale: improves composability and keeps each unit focused (`MessageAvatar`, `MessageHeader`, `MessageReplyPreview`, `MessageContent`, `MessageEditor`, `MessageActions`).
- Alternative considered: keep one large item component. Rejected because complexity is already high.

## Risks / Trade-offs

- [Refactor regression risk] Moving responsibilities can break edge-case behavior. → Mitigation: preserve existing behavioral tests and add boundary-focused tests.
- [Temporary duplication] During migration, logic may exist in both places briefly. → Mitigation: incremental migration checklist with remove-after-move steps.
- [Store coupling drift] Item-level store reads can expand over time. → Mitigation: define allowed store reads and review in PRs.

## Migration Plan

- Introduce boundary contract and update props between list and item.
- Move reply-resolution and grouping-derived flags to list layer.
- Decompose item UI into subcomponents while preserving output behavior.
- Remove deprecated list-wide props from item callsites.
- Update/add tests covering ownership boundaries and unchanged pagination/grouping outcomes.

## Open Questions

- Should reply resolution live directly in `MessageList` or in a dedicated list-level hook for reuse?
- Which item subcomponents should own memoization versus relying on parent-level memoization?

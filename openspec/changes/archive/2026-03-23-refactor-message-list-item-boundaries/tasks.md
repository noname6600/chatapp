## 1. Responsibility Boundary Refactor

- [x] 1.1 Move/keep room initialization, pagination trigger logic, scroll lifecycle handling, grouping derivation, and reply-target resolution in `MessageList`.
- [x] 1.2 Remove list-wide logic from `MessageItem` (no pagination/scroll/grouping ownership or room-level orchestration).
- [x] 1.3 Reduce `MessageList` -> `MessageItem` props to item-scoped data (`message`, grouping flags, optional resolved reply message).

## 2. MessageItem UI Decomposition

- [x] 2.1 Extract avatar/header/reply-preview/content/editor/actions into focused subcomponents under chat message item UI.
- [x] 2.2 Keep local item UI state (hover/edit/action visibility) in `MessageItem` or item subcomponents only.
- [x] 2.3 Replace legacy prop-drilled user/current-account dependencies with appropriate item-layer store reads where needed.

## 3. Contract and Behavior Validation

- [x] 3.1 Add/update tests proving grouping and pagination behavior remain list-owned and behaviorally unchanged.
- [x] 3.2 Add/update tests proving `MessageItem` no longer requires list-wide inputs such as full message arrays.
- [x] 3.3 Run frontend test/build validation for refactored message list and item boundaries and fix any failures.

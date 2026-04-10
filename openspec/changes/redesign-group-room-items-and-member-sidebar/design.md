## Context

**Current state:**

- `GroupRoomItem.tsx`: Renders group rooms as `44×44px` icon tiles (`flex-row flex-wrap` grid in `RoomList.tsx`). Only the avatar is visible; the name appears in a hover tooltip. No last message preview is shown.
- `PrivateRoomItem.tsx`: Renders DM rooms as a full-width list row with avatar (left), name + last-message preview (right). This is clean and scannable.
- `RoomMembersSidebar.tsx`: Has a working show/hide toggle, but it is a bare `<button>` with text "Hide/Show Users" positioned `absolute right-2 top-2`, creating a clunky overlay experience. Member rows show display name + status text label + dot but lack visual polish.

**Goal:** bring group room items to parity with DM rows in scannability, and give the member sidebar a proper icon-based toggle.

## Goals / Non-Goals

**Goals:**
- `GroupRoomItem` renders as a compact list row (avatar + name + last message preview) instead of the icon tile.
- `RoomList` Groups section layout switches from `flex-row flex-wrap` to vertical list to match DM section.
- `RoomMembersSidebar` toggle becomes an icon button (`PanelRightClose`/`PanelRightOpen` from lucide-react already in project deps) integrated into the chat header area, not floated absolutely.
- Member rows get tighter spacing, a subtler status dot, and the status text label removed (dot only).

**Non-Goals:**
- Changing section collapse/expand logic, sort order, unread badge behavior, or any DM row appearance.
- Adding new API calls or store fields (the `lastMessage` field already exists on `Room` type).
- Any backend changes.

## Decisions

### Decision: Re-use `PrivateRoomItem` structure (not extract shared component) for group rows
- **Why:** Group and private items still need different avatar shapes (square rounded-xl for groups vs `rounded-full` for DMs) and badge styling. A full extraction would create coupling to two diverging layouts. Copying the row pattern and adjusting per-item differences is lower risk.
- **Alternative:** Extract `RoomListRow` base component shared by both — rejected for now to keep scope minimal; can be done as a follow-up refactor.

### Decision: Move member sidebar toggle icon to chat header bar
- **Why:** The absolute-positioned text button sits on top of content and interferes with first-item visibility. Moving the icon to the chat header (where it becomes a `ChevronRight`/`ChevronLeft` or `PanelRightOpen`/`PanelRightClose` icon) is consistent with common chat-app patterns.
- **Alternative:** Keep it in `RoomMembersSidebar` but as a sticky top-bar icon instead of absolute-positioned — acceptable fallback if header integration is more complex.

### Decision: Remove status text label from member rows (dot only)
- **Why:** Status labels ("ONLINE", "OFFLINE", "AWAY") are verbose and take horizontal space in an already narrow sidebar. The colored dot already communicates the same information.
- **Alternative:** Tooltip on hover showing full status text — optional enhancement for later.

## Risks / Trade-offs

- [Risk] `GroupRoomItem` row width may be inconsistent if `RoomList.tsx` flex container is not updated → Mitigation: task 2.2 explicitly updates `RoomList` Groups section container from `flex-row flex-wrap` to `flex-col`.
- [Risk] Icon toggle position change in member sidebar may require parent layout adjustments (chat shell container) → Mitigation: fallback to sticky-top-bar icon inside `RoomMembersSidebar` if header integration is blocked.
- [Risk] Group rooms may not have `lastMessage` populated on first load → Mitigation: `lastMessage` preview is already optional (`lastMessage?: LastMessagePreview | null`); component renders gracefully when absent.

## Migration Plan

1. Update `GroupRoomItem.tsx` to list-row layout.
2. Update `RoomList.tsx` Groups section container class from wrap to column.
3. Update `RoomMembersSidebar.tsx`: replace text toggle with icon, remove status text labels, improve row spacing.
4. Wire toggle state up to chat shell if moving icon to header, or keep internal to sidebar with sticky placement.
5. Run FE lint + existing tests; add/adjust any breaking tests.
6. Manual QA in chat view.

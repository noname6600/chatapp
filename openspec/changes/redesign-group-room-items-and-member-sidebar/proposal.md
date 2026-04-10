## Why

The group room section of the room list currently renders rooms as small icon-only tiles (avatar + tooltip), giving no visual context about recent activity or room name at a glance. Meanwhile, direct messages already use a clear row layout (avatar + name + last message preview). The inconsistency makes the sidebar feel unpolished and hard to scan. Additionally, the room member sidebar toggle is a plain text button placed absolutely in the corner, which looks out-of-place and could be mistaken for a menu action. This change unifies the room list row style and upgrades the member sidebar toggle to a proper icon control.

## What Changes

- **Group room items** (`GroupRoomItem`): Switch from icon-tile layout (avatar only + hover tooltip) to a compact list-row layout matching `PrivateRoomItem` — showing avatar on the left, group name as primary text, and last message preview (sender name + content snippet) as secondary text.
- **Room member sidebar** (`RoomMembersSidebar`): Replace the absolute-positioned text "Hide/Show Users" button with a clean icon toggle (e.g., `PanelRightClose`/`PanelRightOpen` from lucide-react), styled consistently with the rest of the chat shell chrome. Improve visual styling of member rows (grouped by presence, better spacing, role badge refinement).
- No changes to room list grouping logic, sorting, unread badge behavior, section collapsing, or DM list layout.

## Capabilities

### New Capabilities
- `group-room-item-row-layout`: Defines the list-row visual contract for group room items in the sidebar (avatar + name + last message preview).
- `room-member-sidebar-toggle`: Defines the icon-driven show/hide control for the room members panel.

### Modified Capabilities
- `room-list-collapsible-sections`: The visual style of group room items within the collapsible Groups section is changing (from icon tiles to list rows). The collapsing behavior itself is unchanged, but the item layout requirement inside the section is new.

## Impact

- `chatappFE/src/components/rooms/GroupRoomItem.tsx` — full component rewrite to row layout
- `chatappFE/src/components/chat/RoomMembersSidebar.tsx` — toggle button replaced with icon, member rows polished
- `chatappFE/src/components/rooms/RoomList.tsx` — Groups section layout changes from `flex-row flex-wrap` to vertical list (matching DM section)
- No backend changes, no API contract changes, no store changes

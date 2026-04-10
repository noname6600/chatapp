## 1. Group room item — row layout

- [x] 1.1 Rewrite `GroupRoomItem.tsx` to a full-width list row: avatar (square, `rounded-xl`) on the left, group name as primary text, and last message preview (`room.lastMessage.senderName: content`) as secondary text when available; preserve unread badge
- [x] 1.2 Add active state (left-border accent + background tint) and hover state to `GroupRoomItem`, matching the pattern in `PrivateRoomItem`
- [x] 1.3 Handle missing `lastMessage` gracefully (render only group name with no secondary text)

## 2. Room list — Groups section layout

- [x] 2.1 In `RoomList.tsx`, change the Groups section item container from `flex flex-row flex-wrap gap-2` to `flex flex-col` so group rows render vertically (matching the DM section layout)
- [x] 2.2 Remove the `bg-gray-50/60` tinted wrap background from the Groups section content area if it no longer fits the row layout

## 3. Room member sidebar — icon toggle

- [x] 3.1 In `RoomMembersSidebar.tsx`, replace the absolute-positioned text button ("Hide/Show Users") with a `PanelRightClose`/`PanelRightOpen` icon button from `lucide-react`
- [x] 3.2 Position the icon toggle in the chat header bar (or as a sticky top element inside the sidebar panel) so it does not overlap message content
- [x] 3.3 Wire the icon variant: show `PanelRightClose` when panel is open, `PanelRightOpen` when collapsed

## 4. Room member sidebar — row polish

- [x] 4.1 Remove the inline status text label (`ONLINE`/`AWAY`/`OFFLINE`) from `MemberRow` — keep the `OnlineDot` indicator only
- [x] 4.2 Tighten member row spacing and refine typography (group title font size, row gap) for a cleaner grouped-by-presence look

## 5. Verification

- [x] 5.1 Run `npm run lint` (or equivalent) in `chatappFE` and fix any lint errors
- [ ] 5.2 Run `npm test` and confirm existing room list and member sidebar tests pass
- [ ] 5.3 Manual QA: verify group section shows list rows with name + last message; member panel toggle icon works; no status text labels visible in member rows; DM rows and section collapse behavior unchanged

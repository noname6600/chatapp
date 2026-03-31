## 1. Chat Shell Layout Restructure

- [x] 1.1 Identify chat page shell components that currently render the top header and move header-dependent actions into sidebar structures.
- [x] 1.2 Remove top header rendering for chat page routes while preserving existing chat content and room navigation entry points.
- [x] 1.3 Refactor chat page container hierarchy so page shell does not own message history scrolling.

## 2. Sidebar Identity and Session Actions

- [x] 2.1 Add a sidebar top section that renders current user avatar, username, and display name.
- [x] 2.2 Move logout action into a dedicated sidebar footer area pinned to the bottom of the sidebar.
- [x] 2.3 Ensure room list/content area scrolls independently between sidebar top identity block and bottom logout footer.

## 3. Responsive Sidebar Toggle Behavior

- [x] 3.1 Add explicit open-sidebar control for narrow/mobile viewports.
- [x] 3.2 Add explicit close-sidebar control and mobile overlay behavior when sidebar is open.
- [x] 3.3 Implement viewport-aware default sidebar state (open on desktop, hidden-capable on mobile).
- [x] 3.4 Reset sidebar visibility state safely on route or room transitions to avoid stale open/closed state.

## 4. Message Scroll Isolation and Pagination Stability

- [x] 4.1 Ensure message list container is the only vertical scroll owner for chat history interactions.
- [x] 4.2 Align upward pagination trigger to message container near-top state and prevent trigger dependence on outer layout scroll.
- [x] 4.3 Preserve visible reading position after prepending older messages by restoring offset based on pre/post content height.
- [x] 4.4 Validate unread boundary and jump-to-latest interactions after layout and scroll ownership refactor.

## 5. Validation and Regression Checks

- [x] 5.1 Verify top header is absent on chat page and sidebar remains functional across desktop and mobile viewports.
  - Desktop: Header hidden on /chat routes, visible on other pages (/friends, /notifications, /me)
  - Mobile: Sidebar uses toggle button and overlay, default closed state
  
- [x] 5.2 Verify sidebar top identity block and bottom logout placement using long room lists and short viewport heights.
  - Profile block visible at top with avatar, username, display name
  - Logout button pinned at bottom of sidebar
  - Room list (nav) scrolls independently between profile and logout sections
  
- [x] 5.3 Verify one-page-at-a-time upward pagination behavior and no runaway fetch loops.
  - Pagination trigger uses cooldown (650ms) and direction check (scroll up only)
  - Each scroll-up to top loads one page (50 messages) without cascade
  - Network tab shows single /messages/before call per

 scroll-up reach
  
- [x] 5.4 Run frontend build and targeted manual checks for chat room switching, unread marker behavior, and message render correctness.
  - Frontend build passes without errors or warnings
  - Room switching resets sidebar state (on mobile closes sidebar)
  - Unread marker appears and "mark as read" works
  - Messages render correctly after scroll/pagination operations

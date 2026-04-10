## Context

`FriendsPage.tsx` currently renders a simple tab row and a vertical list of `FriendRow` items, while `ProfileSettingsPage.tsx` already establishes the stronger carded shell, panel spacing, and content hierarchy the product wants. The friends page also has a `handleChat` placeholder that only logs to the console, even though the frontend already has a working private-chat launch path through `startPrivateChatApi`, `useChat().setActiveRoom`, and chat navigation in adjacent surfaces such as `UserPopup` and notification-driven entry points.

This change spans page layout, row interaction behavior, and coordination across the friend, room, chat, and router layers. It benefits from a design artifact because the UX goal is not just a restyle; it also needs a clear action model so row clicks, right-side buttons, pending request actions, and direct-chat launch remain predictable.

## Goals / Non-Goals

**Goals:**
- Give the friends page a settings-like shell with stronger visual hierarchy, summary context, and more polished list presentation.
- Make friend cards actionable so users can open a direct chat from the row itself or from a dedicated right-side primary action.
- Reuse existing private-chat creation and active-room wiring instead of inventing a parallel direct-message flow.
- Preserve current pending request and add-friend behavior while bringing those sections into the same visual language.
- Keep action targets unambiguous so secondary actions like remove, accept, decline, or cancel do not conflict with row-level click behavior.

**Non-Goals:**
- Redesigning the chat page itself or the room list experience.
- Introducing new backend endpoints, room data contracts, or friendship event types.
- Replacing the existing add-friend recommendation/search logic.
- Migrating the page into the unfinished feature-module architecture proposed by archived refactor work.

## Decisions

### Decision: Reuse the existing private-chat launch path from the friends page
Friend-page direct chat should call `startPrivateChatApi(userId)`, trigger any needed room-list refresh, set the active room through the chat store, and navigate to `/chat`. This keeps behavior aligned with other entry points and avoids new API or store abstractions.

Alternative considered: add a dedicated friend-page-only direct-message action in the friend store. Rejected because chat-room creation is already owned by room/chat flows, and duplicating that orchestration would create another state path to maintain.

### Decision: Use settings-page visual parity as a layout reference, not a literal clone
The friends page should borrow the structural cues that work in settings: a rounded main shell, bordered content panels, clearer section headers, and sidebar-style summary cards. It should still remain purpose-built for list browsing and actions instead of copying the profile/security tab pattern verbatim.

Alternative considered: only restyle `FriendRow` and leave the page shell unchanged. Rejected because the current page-level structure is part of why the friends area feels visually behind the settings page.

### Decision: Make the friend card itself open chat, while preserving explicit right-side controls
For established friends, clicking the main card surface should open direct chat, and a dedicated right-side chat button should do the same for discoverability. Secondary controls such as more-menu/remove must stop propagation so they do not accidentally launch chat.

Alternative considered: only the chat icon should launch chat. Rejected because the user explicitly wants the friend item itself to be clickable, and a broader hit area makes the page faster to use.

### Decision: Keep pending and add-friend flows in the same shell with section-specific action patterns
Pending rows and add-friend panels should visually match the upgraded surface styling, but their interaction model should stay task-oriented: accept/decline/cancel/add remain button-driven instead of row-click-to-chat.

Alternative considered: unify all row types under one click interaction. Rejected because pending requests and suggestions do not map cleanly to a direct-chat primary action.

## Risks / Trade-offs

- [Risk] Row-level click behavior can conflict with embedded buttons or menus. → Mitigation: define a strict event-boundary pattern where secondary action controls stop propagation and only friend-card primary surfaces launch chat.
- [Risk] Starting a private chat may feel inconsistent if room refresh and navigation timing differ from other entry points. → Mitigation: mirror the existing `startPrivateChatApi` + `rooms:reload` + `setActiveRoom` pattern already used in the app.
- [Risk] Styling parity with settings could drift into copy-paste UI rather than a fit-for-purpose friends surface. → Mitigation: reuse layout principles and tokens, but keep summaries, tabs, and cards tailored to friend-management tasks.
- [Risk] The page upgrade may need broader testing because it touches navigation and async actions. → Mitigation: include focused UI tests for row actions, direct-chat launch, and tab/section rendering states.

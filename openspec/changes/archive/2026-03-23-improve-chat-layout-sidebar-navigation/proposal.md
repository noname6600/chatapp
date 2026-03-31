## Why

The current chat page layout mixes shell responsibilities between page-level and chat-level containers, which causes scroll behavior bugs and weak responsive behavior on smaller screens. The UI also places profile and logout actions in inconsistent locations, making navigation and session actions less discoverable.

## What Changes

- Remove the top header from the chat page layout and move shell controls into the left sidebar.
- Move logout action into the left navigation area and pin it to the bottom section of the sidebar.
- Add a top profile block in the sidebar showing avatar, username, and display name.
- Make the left sidebar responsive with explicit open and hide toggle controls for narrow viewports.
- Refactor layout container structure so chat message scrolling is isolated inside the chat content region and no longer coupled to outer page wrappers.

## Capabilities

### New Capabilities
- `chat-shell-layout-navigation`: Defines chat page shell behavior, including no top header, sidebar profile section at top, and logout action placement at sidebar bottom.
- `responsive-sidebar-toggle`: Defines responsive sidebar open and close behavior, including toggle controls and viewport-appropriate default states.
- `chat-scroll-container-isolation`: Defines scroll ownership boundaries so chat message list scrolling is scoped to the message container and not page-level layout wrappers.

### Modified Capabilities
- None.

## Impact

- Affected code: frontend layout and chat page components, likely under chatappFE/src/layouts, chatappFE/src/pages, and chatappFE/src/components.
- UX impact: navigation and account controls become more predictable and mobile-friendly.
- Behavior impact: reduces scroll conflicts that currently interfere with scroll-up pagination/loading.
- No backend API changes expected.

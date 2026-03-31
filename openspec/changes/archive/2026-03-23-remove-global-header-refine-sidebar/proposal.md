## Why

The global header was conditionally hidden on chat page but is still rendered and taking space on other pages (Friends, Notifications, Profile). Users benefit from a cleaner, more consistent shell that prioritizes content and sidebar navigation for all pages. Additionally, the sidebar needs explicit persistent hide/show controls for better space management across viewport sizes.

## What Changes

- Remove top header component globally (no longer render Header in MainLayout)
- Add persistent sidebar collapse/expand button visible in all states
- Refactor MainLayout to give more vertical space to content since header is removed
- Ensure sidebar controls improve discoverability for showing/hiding navigation

## Capabilities

### New Capabilities
- `persistent-sidebar-collapse`: Defines explicit sidebar collapse/expand button behavior that persists across page navigation and is accessible in all viewport sizes.

### Modified Capabilities
- `responsive-sidebar-toggle`: Extend to include persistent collapse button separate from mobile-only toggle behavior.

## Impact

- Affected code: MainLayout, Sidebar components
- Breaking change: Header removed globally - any hardcoded header dependencies will break
- UX impact: More vertical space for content, cleaner visual hierarchy
- No backend API changes

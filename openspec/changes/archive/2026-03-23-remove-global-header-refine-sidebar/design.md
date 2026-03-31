## Context

Currently, MainLayout conditionally hides the Header only for chat routes, but renders it for all other pages. This consumes vertical space globally and creates inconsistent shell patterns. The Sidebar was recently enhanced with mobile toggle but lacks desktop-friendly collapse button for space management.

## Goals / Non-Goals

**Goals:**
- Remove Header component globally from MainLayout (all routes)
- Add explicit persistent collapse/expand button to Sidebar for all viewport sizes
- Maximize content area by eliminating fixed-height header
- Improve consistency: same shell structure across all pages

**Non-Goals:**
- No changes to page content or layout depth
- No redesign of Header component itself; simply remove it
- No changes to websocket or backend concerns

## Decisions

- Remove Header entirely from MainLayout rather than conditional rendering.
Rationale: Eliminates dead code and ensures consistent behavior. Previously checking route on every render added maintenance burden.
Alternative: Keep conditional. Rejected because spread logic across layout.

- Add persistent sidebar collapse button visible in all states (desktop and mobile).
Rationale: Provides space savings on narrow desktop viewports and improves discoverability compared to mobile-only toggle.
Alternative: Keep only mobile toggle. Rejected because desktop users with narrow windows lose convenience.

- Collapse button should be always visible (not modal-only) for accessibility.
Rationale: Ensures users can always restore sidebar if accidentally hidden.

## Risks / Trade-offs

- [Risk] Users who expect Header on non-chat pages will not find it. → Mitigation: All shell actions (logout) now in Sidebar; document change in release notes.
- [Risk] Collapsed sidebar state persists across page changes, may confuse users. → Mitigation: Consider auto-expand on narrow mobile, manual control on desktop.
- [Risk] Simultaneous mobile toggle + desktop collapse may create UI complexity. → Mitigation: Keep both; mobile toggle for full hide, desktop for partial collapse to icon-only.

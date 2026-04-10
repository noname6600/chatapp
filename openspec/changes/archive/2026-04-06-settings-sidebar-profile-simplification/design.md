## Context

The existing settings change introduced profile/security/recovery sections and shared profile rendering logic, but UX feedback indicates the account flow is still too fragmented. Users want profile editing to be immediately available in Settings, without a separate recovery tab competing for space, and want profile preview output to match what they see when opening profile from chat avatar/name interactions.

Current state constraints:
- Settings and profile preview components already exist and are wired to auth/user profile APIs.
- Multiple entrypoints can open profile context (settings route, avatar click, mention/profile popups in chat surfaces).
- Data fields are available (`displayName`, `username`, `avatarUrl`, `backgroundColor`, `aboutMe`) but not all surfaces render with the same mapping/fallback.

## Goals / Non-Goals

**Goals:**
- Simplify Settings information architecture by removing the Account Recovery tab.
- Keep profile as a first-class settings section and ensure edit-profile controls are always visible when user is authenticated.
- Ensure `aboutMe` is displayed in profile summary/preview and not hidden by mode toggles.
- Standardize profile preview presentation so avatar/name entrypoints in chat and settings show equivalent identity output (avatar, display name, username/about me, background fallback behavior).

**Non-Goals:**
- Replacing backend profile data model or introducing new profile fields.
- Reworking authentication token flows beyond existing verification/reset endpoints.
- Full redesign of chat profile popup layout unrelated to parity requirements.

## Decisions

- Remove Account Recovery tab from Settings navigation.
Rationale: recovery actions are infrequent and can live under Security or dedicated auth routes; removing the tab reduces complexity and improves discoverability of frequently used profile controls.
Alternative: keep tab but collapse by default. Rejected because users still face extra navigation choice and visual clutter.

- Make Edit Profile action persistently visible in settings profile section.
Rationale: eliminates hidden state and ensures users can always enter edit flow for profile fields.
Alternative: keep conditional visibility based on ownership/edit mode. Rejected because settings route is already self-scoped and ownership is implied.

- Introduce one shared profile-view model/resolver for preview rendering parity.
Rationale: centralizing display mapping (avatar fallback, background fallback, display name/username/about me precedence) prevents divergence between settings preview and chat-triggered profile views.
Alternative: patch each surface separately. Rejected due to regression risk and repeated logic.

- Keep recovery flows on dedicated auth routes (`/forgot-password`, `/auth/reset-password`) and expose them from login/security affordances rather than a top-level settings tab.
Rationale: aligns action frequency with navigation depth and keeps settings focused on persistent account preferences.
Alternative: embed full recovery forms inside settings. Rejected to avoid mixing auth-sensitive one-time flows into persistent settings layout.

## Risks / Trade-offs

- [Risk] Removing the recovery tab could reduce discoverability for users who expect it in Settings.
  → Mitigation: keep clear recovery entry in login and security surface text/actions.

- [Risk] Forcing always-visible edit controls could create visual density on small screens.
  → Mitigation: maintain compact button style and reuse existing responsive layout breakpoints.

- [Risk] Preview parity may expose latent differences in data shape across chat/profile surfaces.
  → Mitigation: use a typed shared resolver and add targeted UI tests for key entrypoints.

## Migration Plan

1. Remove the Settings recovery tab and related tab-state branches in frontend.
2. Make profile edit action unconditionally visible in profile section.
3. Extend profile summary/preview to always include about-me text with fallback.
4. Refactor chat/profile preview entrypoints to consume shared profile display resolver.
5. Run frontend regression checks for settings, sidebar identity block, and profile entrypoint rendering.
6. Validate manual UX flows: settings profile edit, avatar/name click in chat, mention/profile popup parity.

Rollback:
- Re-enable previous tab definition and old preview mapping via git revert if parity changes cause UI regressions.

## Open Questions

- Should Security contain an explicit inline link/button to forgot-password routes after recovery tab removal?
- For profile popups with limited space, should about-me be truncated to one line or follow multiline clamp rules?
- Should parity include exact typography/spacing, or strictly data/value parity with existing component-specific layout?

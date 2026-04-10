## Why

Account and identity controls are fragmented across the app, and core account-security capabilities (email/password login, email verification, forgot password) are missing while sidebar/profile navigation is inconsistent. This change centralizes account settings, improves profile discoverability and consistency, and adds essential auth flows in auth-service.

## What Changes

- Replace the sidebar "Notifications" navigation entry with "Settings" and move it to the end of the sidebar navigation list.
- Add a Settings page with account-management sections: profile summary card (avatar, display name, profile background), edit-profile entrypoint, display name update, username update (user-service), password change, email verification trigger, and account email linking behavior.
- Ensure profile entrypoints are consistent: clicking avatar/name in supported surfaces opens the same profile page.
- Add email/password authentication support in auth-service while preserving current Google login support.
- Add email verification flow in auth-service so one email maps to one account identity.
- Add forgot-password flow (request reset + reset confirmation) and integrate it into the login page.
- Fix profile background rendering consistency so profile header background matches intended design/system source.

## Capabilities

### New Capabilities
- `settings-account-center`: Settings page and account controls in a unified sidebar destination.
- `account-profile-editing`: Edit display name, username, and profile presentation from settings/profile flows.
- `email-password-auth-verification`: Email/password auth, email verification, and single-account-per-email identity behavior in auth-service.
- `forgot-password-recovery`: End-to-end password recovery flow integrated with login UX.
- `profile-entrypoint-consistency`: Consistent clickable avatar/name routing to a single profile destination across app surfaces.

### Modified Capabilities
- `chat-shell-layout-navigation`: Sidebar navigation labels/order updated to include Settings as the final item and remove Notifications as a first-class nav item.
- `rich-presence-status`: Presence/profile affordances updated so avatar/name profile navigation remains consistent with the new settings/profile interaction model.

## Impact

- Frontend: sidebar navigation, settings route/page, login page, profile header/background rendering, shared avatar/name click behavior, account settings forms.
- Backend user-service: username update/search and validation integration points for settings/profile updates.
- Backend auth-service: email/password auth, email verification, password reset token flows, account identity linking constraints by email.
- Integrations: email delivery capability for verification and password reset messages.
- Security: password policy enforcement, token expiry, replay prevention, and ownership checks for profile/account updates.
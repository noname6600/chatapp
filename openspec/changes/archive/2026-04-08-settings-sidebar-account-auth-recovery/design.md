## Context

The app currently has fragmented account controls: navigation still exposes Notifications in sidebar while account/security operations are spread or missing. Profile access patterns are inconsistent (avatar/name interactions differ by surface), profile background rendering is inconsistent, and authentication is Google-only with no local credential recovery path. The requested scope spans frontend shell/navigation, profile/settings UX, user-service username/profile updates, and auth-service identity/security flows (email/password login, verification, password change, forgot password).

## Goals / Non-Goals

**Goals:**
- Introduce a unified Settings destination in sidebar and place it as the final navigation item.
- Provide an account center UI for profile and security operations.
- Support local email/password identity in auth-service alongside Google.
- Enforce single-account-per-email behavior with verification flow.
- Deliver forgot-password and reset flow integrated into login UX.
- Normalize profile entrypoints and profile background rendering.

**Non-Goals:**
- Replacing Google auth provider.
- Building full account deletion/data export workflows.
- Redesigning unrelated chat/friends/notification domain behavior.

## Decisions

- Use a dedicated Settings page as account control hub.
Rationale: consolidates profile/security operations and reduces scattered UX.
Alternative: keep controls in multiple pages. Rejected due to discoverability inconsistency.

- Keep user profile data authority in user-service (display name, username, avatar/background metadata).
Rationale: preserves service boundaries and minimizes duplicate profile state.
Alternative: move profile write APIs into auth-service. Rejected due to coupling.

- Add local credential auth to auth-service while preserving social login path.
Rationale: enables forgot-password and verification while staying backward compatible.
Alternative: keep social-only and implement pseudo-password in frontend. Rejected for security and correctness.

- Introduce email verification and password reset using signed one-time tokens with expiry.
Rationale: standard secure account lifecycle behavior.
Alternative: code-based OTP only. Rejected for higher delivery/UX complexity at current scope.

- Make avatar/name interactions route to one canonical profile page; expose status/security/profile edits from settings/profile actions.
Rationale: consistent mental model and fewer interaction surprises.
Alternative: keep surface-specific behavior. Rejected due to user confusion.

## Risks / Trade-offs

- [Risk] Existing users with Google-only accounts may encounter identity collisions when adding email/password. → Mitigation: explicit account-linking rules keyed by verified email and safe conflict handling.
- [Risk] Email delivery failures can block verification/reset. → Mitigation: retry-safe token issuance, resend endpoints, and clear error UX.
- [Risk] Sidebar interaction changes may disrupt existing user habits. → Mitigation: preserve visual hierarchy and provide clear Settings label placement at list end.
- [Risk] Auth-service complexity increases with multiple credential types. → Mitigation: isolate providers behind a unified auth policy and add targeted integration tests.

## Migration Plan

1. Add backend auth/user endpoints behind backward-compatible contracts.
2. Add frontend Settings route and sidebar label/order update.
3. Ship profile/security forms with feature flags or staged rollout.
4. Enable email verification + reset email delivery in staging, then production.
5. Monitor login success/failure, reset completion, and verification conversion.

## Open Questions

- Should profile background be uploaded media (like avatar) or selected from predefined themes?
- Should username changes require cooldown/history rules?
- For Google-linked accounts, is password creation optional or required when same verified email exists?
- Should email change itself require re-verification before taking effect?
## 1. Sidebar and Routing Updates

- [x] 1.1 Replace sidebar Notifications item with Settings label in sidebar navigation config
- [x] 1.2 Reorder sidebar navigation so Settings is rendered as the final primary item
- [x] 1.3 Add or update frontend route for Settings page and ensure authenticated access guard
- [x] 1.4 Remove legacy navigation paths that expect Notifications as standalone sidebar item

## 2. Settings Account Center UI

- [x] 2.1 Create Settings page shell with sections for Profile, Security, and Account Recovery
- [x] 2.2 Add profile summary card showing avatar, display name, and profile background preview
- [x] 2.3 Add Edit Profile button in settings profile section and wire it to profile edit flow
- [x] 2.4 Add form/actions for display name update and username update with validation feedback
- [x] 2.5 Add password change form with current password/new password/confirm password validation
- [x] 2.6 Add email verification section with send/resend verification action and status indicator

## 3. Profile Entry and Visual Consistency

- [x] 3.1 Standardize avatar click behavior to navigate to canonical profile page in sidebar/user identity surfaces
- [x] 3.2 Standardize display-name click behavior to navigate to the same canonical profile page
- [x] 3.3 Ensure profile page exposes clear edit-profile action for own profile
- [x] 3.4 Fix profile background source mapping so profile page and settings summary render identical background
- [x] 3.5 Add fallback background logic shared by settings/profile surfaces for missing/invalid metadata

## 4. user-service Account Profile APIs

- [x] 4.1 Verify or add backend endpoint for updating display name with authentication and ownership checks
- [x] 4.2 Verify or add backend endpoint for updating username with uniqueness validation
- [x] 4.3 Enforce username format and conflict handling with deterministic error responses
- [x] 4.4 Add/update tests for display name and username update flows (success, invalid, conflict)

## 5. auth-service Email/Password and Identity Linking

- [x] 5.1 Add email/password credential model and secure password hashing policy
- [x] 5.2 Add email/password login endpoint that coexists with Google login
- [x] 5.3 Enforce one-account-per-verified-email identity constraint across providers
- [x] 5.4 Implement account-linking behavior for provider logins sharing same verified email
- [x] 5.5 Add email verification token issuance, persistence, expiry, and consumption endpoints
- [x] 5.6 Integrate verification-email delivery adapter and resend flow
- [x] 5.7 Add/change password endpoint with strong policy validation and audit-safe responses
- [x] 5.8 Add auth-service tests for login, linking, verification success/failure, and duplicate-email prevention

## 6. Forgot Password Recovery Flow

- [x] 6.1 Add forgot-password request endpoint issuing one-time reset token with expiry
- [x] 6.2 Add password-reset confirmation endpoint consuming reset token and updating password
- [x] 6.3 Ensure forgot-password request responses do not leak account existence
- [x] 6.4 Invalidate used/expired reset tokens and block replay attempts
- [x] 6.5 Add email template/delivery path for password reset links
- [x] 6.6 Add auth-service tests for request/reset success, invalid token, expired token, replay prevention

## 7. Login Page Integration

- [x] 7.1 Add Forgot Password action/link to login page UI
- [x] 7.2 Add forgot-password request page/form and submission states
- [x] 7.3 Add reset-password page/form handling token from email link
- [x] 7.4 Redirect successful password reset back to login and show success notice
- [x] 7.5 Ensure Google login option remains available and unaffected

## 8. Frontend Integration and State Refresh

- [x] 8.1 Add settings/profile store actions to refresh user identity data after profile edits
- [x] 8.2 Ensure sidebar identity block reflects updated display name/avatar immediately after save
- [x] 8.3 Ensure username updates propagate to profile and settings views consistently
- [x] 8.4 Ensure verification/password state changes update settings UI without full reload

## 9. Validation and Regression Coverage

- [ ] 9.1 Frontend test: sidebar renders Settings as final item and Notifications removed from primary nav
- [ ] 9.2 Frontend test: avatar/name click navigates to canonical profile route consistently
- [ ] 9.3 Frontend test: profile background displays consistently in profile and settings summary
- [ ] 9.4 Backend test: username/display-name updates enforce auth and uniqueness constraints
- [ ] 9.5 Backend test: email/password login + Google coexist with single-email identity behavior
- [ ] 9.6 Backend test: verification and forgot-password token lifecycles (issue, consume, expire, replay block)
- [ ] 9.7 End-to-end manual check: change display name/username, change password, verify email, reset password from login flow
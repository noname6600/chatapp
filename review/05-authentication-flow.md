# 05. Authentication Flow

## Components Involved
- FE auth API client: `chatappFE/src/api/auth.service.ts`
- Gateway auth route: `gateway-service/src/main/resources/application.yaml`
- Auth endpoints: `auth-service/controller/AuthController.java`
- Core auth orchestration: `auth-service/service/impl/AuthService.java`
- Local login/register: `LocalAuthService`
- Token issuance/refresh: `TokenService` + `TokenServiceFacade`

## Register Flow
1. FE calls `POST /api/v1/auth/register`.
2. Gateway routes to auth-service.
3. `AuthController.register` validates payload.
4. `LocalAuthService.register` checks existing account by email.
5. New account path: account row persisted, identity provider linked (`LOCAL`), account-created event scheduled after commit.
6. `AuthSessionService.issueTokens` -> `TokenServiceFacade.issue`.
7. Access token generated (JWT RS256, `kid` header).
8. Refresh token generated random -> SHA256 hash persisted.
9. Response returns access/refresh + expiry.

## Login Flow
1. FE calls `POST /api/v1/auth/login`.
2. `LocalAuthService.login` verifies password hash and enabled state.
3. `AuthSessionService.issueTokens` issues new token pair.
4. FE stores tokens in localStorage (`auth.store.tsx`).

## Google OAuth Exchange
1. FE sends auth code to `/api/v1/auth/oauth/google/exchange`.
2. `AuthService.exchangeGoogleOAuthCode` delegates to browser oauth service.
3. Account linkage through `OAuthAuthService` and identity-provider rows.
4. Token pair issued as standard session.

## Refresh Token Flow
1. FE interceptor (`base.api.ts`) catches eligible 401.
2. Calls `refreshTokenApi` -> `POST /api/v1/auth/refresh`.
3. `TokenServiceFacade.refresh`:
   - hash incoming refresh token
   - load by hash
   - reject invalid/revoked/expired
   - revoke current token atomically (`revokeIfNotRevoked`)
   - issue brand new token pair
4. If token replay/race detected (`updated == 0`), revoke all account refresh tokens.

## Logout Flows
- `POST /logout`: revoke single refresh token if still active.
- `POST /logout-all`: revoke all active refresh tokens by account id.

## JWT Validation Path
- Gateway validates bearer JWT (issuer + timestamp validator with skew).
- Services also validate JWT (resource server jwk set URI).
- Auth service internal JWT filter handles auth endpoints requiring principal.

## Email Verification & Password Recovery
- Verification token issued hashed, expiry 24h.
- Confirmation marks token used and sets `Account.emailVerified=true`.
- Forgot/reset password similarly uses hashed tokens and one-time usage semantics.

## Security Properties
Strengths:
- RS256 signing with key id.
- refresh tokens stored hashed, not plaintext.
- refresh token rotation and replay handling.

Weaknesses:
- no explicit token binding to device fingerprint.
- persistent localStorage token storage in FE increases XSS impact surface.

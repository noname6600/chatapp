## JWT Validation Alignment (Task 3.1)

### Compared Components
- Token issuer: auth-service (`TokenService`, `auth.jwt.*`)
- Token validator: gateway-service (`SecurityConfig`, `spring.security.oauth2.resourceserver.jwt.*`)

### Initial Mismatches Observed
- Gateway allowed auth bootstrap path `/api/auth/**` while auth controller and FE use `/api/v1/auth/**`.
- Gateway did not have explicit configurable clock-skew tolerance in security configuration.
- Gateway 401 logging did not expose structured reason codes for diagnosis.

### Alignment Applied
- Added versioned auth bootstrap path handling in gateway route/security config.
- Added `gateway.security.jwt.clock-skew-seconds` and wired JWT timestamp validation to use it.
- Added structured gateway auth rejection logging reason codes:
  - `missing_token`
  - `expired_token`
  - `invalid_signature`
  - `claim_mismatch`
  - `route_misclassified`

### Remaining Notes
- Issuer/audience claims are not enforced currently because auth-service access tokens do not set explicit issuer/audience claims yet.
- If issuer/audience hardening is needed, add claims in auth-service token generation and enable matching validators in gateway.

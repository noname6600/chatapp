# Auth Service Review

**Service**: Authentication & Authorization (Port 8081)  
**Primary Responsibility**: User registration, login, JWT token issuance, refresh tokens  
**Tech Stack**: Spring Boot, Spring Security, Spring Data JPA, Postgres  

---

## Compile Correctness

### ✅ Compilation Status
```
./gradlew.bat :auth-service:compileJava --no-daemon
```
**Result**: BUILD SUCCESSFUL (with deprecation warning on ResendEmailClient - LOW priority)

### Dependencies
- Spring Security ✅
- Spring Data JPA ✅
- PostgreSQL driver ✅
- JWT libraries (jjwt, jwk-provider) ✅

---

## Runtime Startup

### Required Environment Variables
```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/auth_db
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=<password>
JWT_SECRET=<signing-key-min-256-bits>
JWT_EXPIRATION_SECONDS=3600
JWT_REFRESH_EXPIRATION_SECONDS=604800
RESEND_API_KEY=<email-service-api-key>
SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092
```

### 🔴 BLOCKER: JWT Configuration Incomplete

**Issue**: JWT secret may not be properly configured or tested

**Location**: `auth-service/src/main/java/com/example/auth/configuration/SecurityConfig.java`

**Action Items**:
1. Verify JWT secret is loaded from environment (not hardcoded)
2. Ensure secret is at least 256 bits (for HS256)
3. Test JWT token generation and validation

**Verification**:
```bash
# After startup, check JWT is valid
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "testuser", "password": "testpass"}'
# Should return: {"access_token": "eyJhbGc...", "refresh_token": "..."}
```

---

## Service Boundaries

### Responsibility Ownership

**✅ Owns**:
- User registration/account creation
- Password hashing and storage
- Login validation
- JWT token generation
- Token refresh logic
- Token revocation/logout
- Password reset flow

**✅ Does NOT own**:
- User profiles (user-service owns)
- User search (user-service owns)
- Authorization checks (gateway/service-level)

### Potential Issues
- **Registration may trigger Kafka event** (USER_ACCOUNT_CREATED) → verify message is published after DB commit
- **Password reset flow** → verify token expiration and use-once semantics

---

## 🟡 MEDIUM: Event Publishing Timing

### Issue
When user registers, a Kafka event (e.g., `USER_ACCOUNT_CREATED`) may be published

### Current Pattern
```java
@Transactional
public void register(RegisterRequest req) {
    User user = new User(req);
    userRepository.save(user);
    // ❓ Is kafkaTemplate.send() called here?
    kafkaTemplate.send("user.events", new AccountCreatedEvent(user));
}
```

**Problem**: Event published before transaction commits → consumer reads uncommitted data

### Correct Pattern
```java
@Transactional
public void register(RegisterRequest req) {
    User user = new User(req);
    userRepository.save(user);
    // ... after save, register callback ...
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                kafkaTemplate.send("user.events", new AccountCreatedEvent(user));
            }
        }
    );
}
```

### Action
1. Audit auth-service registration flow for publish timing
2. Fix if not already using TransactionSynchronization
3. Verify in integration test

### Scope: SERVICE-ONLY (or COMMON-OPTIONAL if abstracted)

---

## API Correctness

### Endpoints

| Endpoint | Method | Auth | Status Codes |
|----------|--------|------|--------------|
| `/api/auth/register` | POST | ❌ Public | 201, 400, 409 |
| `/api/auth/login` | POST | ❌ Public | 200, 401 |
| `/api/auth/refresh` | POST | ❌ Public | 200, 401 |
| `/api/auth/logout` | POST | ✅ JWT | 200, 401 |
| `/api/auth/password-reset` | POST | ❌ Public | 200, 400 |
| `/.well-known/jwks.json` | GET | ❌ Public | 200 |

### Request/Response DTOs

**Register Request**:
```json
{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "secure123"
}
```

**Register Response**:
```json
{
  "userId": "uuid",
  "username": "john_doe",
  "email": "john@example.com"
}
```

**Login Request**:
```json
{
  "username": "john_doe",
  "password": "secure123"
}
```

**Login Response**:
```json
{
  "access_token": "eyJhbGc...",
  "refresh_token": "...",
  "expires_in": 3600,
  "token_type": "Bearer"
}
```

### ✅ Validations Present
- Username/email uniqueness
- Password strength
- Email format validation

### 🟡 MEDIUM: Missing Idempotency on Register

**Issue**: If register request is retried (due to network timeout), second attempt creates duplicate user

**Recommendation**: Use idempotency key header or ensure database unique constraint + graceful error handling

---

## 🔴 HIGH: Security - JWT Validation Gaps

### Issue 1: Offline JWT Validation Missing Signature Verification

**Location**: `common-security/src/main/java/com/example/common/security/JwtDecoder.java` or service-local decoder

**Problem**: Local JWT decoder may not verify RSA-256 signature; only checks claims

**Risk**: JWT forgery possible if secret is not properly validated

### Evidence to Check
```java
// ❌ Bad
public Claims validateToken(String token) {
    return Jwts.parserBuilder()
        .build()  // No signing key!
        .parseClaimsJwt(token)
        .getBody();
}

// ✅ Good
public Claims validateToken(String token) {
    return Jwts.parserBuilder()
        .setSigningKey(secretKey)
        .build()
        .parseClaimsJws(token)  // Validates signature
        .getBody();
}
```

### Fix Scope: COMMON-REQUIRED (if centralized in common-security)  
### Risk: LOW  
### Verification
```bash
# Generate invalid token (wrong signature)
# Should throw JwtException, not parse successfully
```

---

### Issue 2: Refresh Token Rotation Not Enforced

**Question**: Does refresh token become invalid after use?

**Correct Behavior**:
1. User calls `/refresh` with old refresh token
2. System issues new access token + NEW refresh token
3. Old refresh token is marked invalid
4. Subsequent use of old refresh token → 401 Unauthorized

**Risk**: If old refresh token can be reused, stolen token allows indefinite account access

### Action
Verify refresh token implementation:
```java
// ✅ Correct
public void refresh(String oldRefreshToken) {
    RefreshToken rt = refreshTokenRepo.findByToken(oldRefreshToken);
    if (rt == null || rt.isRevoked()) throw 401;
    rt.setRevoked(true);  // Invalidate old token
    RefreshToken newRt = createRefreshToken();
    // ... issue new access token with newRt
}
```

### Scope: SERVICE-ONLY

---

## Database

### Entities
- **User** table: id, username, email, password_hash, created_at, updated_at
- **RefreshToken** table: id, user_id, token, revoked, expires_at

### Constraints

| Table | Constraint | Purpose |
|-------|-----------|---------|
| User | UNIQUE(username) | Prevent duplicate usernames |
| User | UNIQUE(email) | Prevent duplicate emails |
| RefreshToken | FK(user_id) → User | Referential integrity |

### 🟡 MEDIUM: Missing Indexes

**Queries**:
- Find user by username → `SELECT * FROM user WHERE username = ?`
- Find user by email → `SELECT * FROM user WHERE email = ?`
- Find refresh token → `SELECT * FROM refresh_token WHERE token = ?`

**Recommendation**: Add indexes:
```sql
CREATE INDEX idx_user_username ON user(username);
CREATE INDEX idx_user_email ON user(email);
CREATE INDEX idx_refresh_token_token ON refresh_token(token);
```

**Scope**: SERVICE-ONLY  
**Risk**: LOW (index-only, no code change)

---

## Cross-Service Communication

### Outbound Calls
None (auth-service is self-contained)

### Exception: Kafka Event Publishing
- Publishes `USER_ACCOUNT_CREATED` after registration
- Publishes `PASSWORD_RESET_REQUESTED` after password reset request
- Publishes `LOGIN_SUCCESS` (optional, for audit)

### Inbound Calls
- Gateway calls `/api/auth/login` to authenticate users
- user-service may call to verify JWT (redundant if gateway does it)

---

## Kafka

### Events Published
- `user.events` topic: USER_ACCOUNT_CREATED, PASSWORD_RESET_REQUESTED

### Consumer Dependencies
- **Notification service** subscribes to USER_ACCOUNT_CREATED (send welcome email)
- **Presence service** may subscribe to PASSWORD_RESET_REQUESTED

### Verification
```bash
# Verify event is published after registration
docker-compose exec kafka kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 \
  --topic user.events \
  --from-beginning
```

---

## Redis

### N/A
Auth service does not use Redis in standard setup

### Exception: Token Blacklist
If token blacklist is stored in Redis instead of database:
- `/logout` adds token to Redis set with TTL = token expiration
- `/validate` checks Redis blacklist before accepting token

**Verification**: Check for Redis integration in SecurityConfig

---

## WebSocket

### N/A
Auth service does not manage WebSocket connections

---

## Error Handling

### Expected Errors

| Scenario | Status | Response |
|----------|--------|----------|
| Invalid login | 401 | `{"error": "Invalid credentials"}` |
| Username exists | 409 | `{"error": "Username already registered"}` |
| Invalid email | 400 | `{"error": "Invalid email format"}` |
| Token expired | 401 | `{"error": "Token expired"}` |
| Invalid refresh token | 401 | `{"error": "Invalid or revoked refresh token"}` |

### 🟡 MEDIUM: Error Messages Too Verbose

**Issue**: Error responses may leak information
- "Username already registered" tells attacker valid usernames exist
- "Invalid email format" leaks email validation rules

### Recommendation (LOW Priority)
- Register/login errors: Generic "Invalid credentials" (don't distinguish username vs. password)
- Public errors: Minimal info

---

## Docker Deployment

### Service Definition
```yaml
auth-service:
  image: chatapp-auth:latest
  ports:
    - "8081:8081"
  environment:
    SERVER_PORT: 8081
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/auth_db
    SPRING_DATASOURCE_USERNAME: postgres
    SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
    JWT_SECRET: ${JWT_SECRET}
    JWT_EXPIRATION_SECONDS: 3600
    JWT_REFRESH_EXPIRATION_SECONDS: 604800
    RESEND_API_KEY: ${RESEND_API_KEY}
    SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
  depends_on:
    postgres:
      condition: service_healthy
    kafka:
      condition: service_healthy
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
    interval: 30s
    timeout: 10s
    retries: 3
```

---

## Testing

### 🟡 MEDIUM: Missing Security Tests

**Test Coverage Gaps**:
1. **JWT signature validation**: Attempt JWT with invalid signature → should fail
2. **Refresh token rotation**: Old token should not work after refresh
3. **Password reset**: Token should be one-time use, expire after 24h
4. **Concurrency**: Simultaneous register with same username → only one succeeds

### Recommended Test Cases
```java
@Test
public void testRegisterWithDuplicateUsername_Returns409() { }

@Test
public void testLoginWithInvalidPassword_Returns401() { }

@Test
public void testRefreshToken_InvalidsOldToken() { }

@Test
public void testJwtWithInvalidSignature_FailsValidation() { }

@Test
public void testPasswordReset_TokenExpires24Hours() { }
```

---

## Summary

| Issue | Severity | Scope | Action |
|-------|----------|-------|--------|
| JWT signature verification | HIGH | COMMON-REQUIRED | Add to JwtDecoder |
| Refresh token rotation | HIGH | SERVICE-ONLY | Verify implementation |
| Event publishing timing | MEDIUM | SERVICE-ONLY | Audit & fix if needed |
| Missing DB indexes | MEDIUM | SERVICE-ONLY | Add indexes on username/email/token |
| Actuator unauthenticated | HIGH | SERVICE-ONLY | Require auth (same as gateway) |
| Error message verbosity | LOW | SERVICE-ONLY | Generic error messages |
| Security tests | MEDIUM | SERVICE-ONLY | Add JWT/refresh/password reset tests |

---

## Verification Commands

```bash
# Compile
./gradlew.bat :auth-service:compileJava --no-daemon

# Run tests
./gradlew.bat :auth-service:test --no-daemon

# Startup
docker-compose up auth-service -d
docker-compose logs auth-service

# Register
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "testuser", "email": "test@example.com", "password": "pass123!"}'

# Login
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "testuser", "password": "pass123!"}'
```

---

**Next**: Read 05-user-service-review.md

# Security Review

**Focus**: JWT, auth, CORS, file uploads, actuator exposure, userId spoofing  

---

## 🔴 HIGH: Actuator Endpoints Unauthenticated

**Issue**: `/actuator/**` endpoints accessible without authentication in all services

**Location**: Each service's `SecurityConfig.java`

**Affected Services**: gateway, auth, user, chat, presence, friendship, notification, upload, realtime-edge

**Exposed Endpoints**:
- `/actuator/env` - Environment variables (may contain secrets)
- `/actuator/configprops` - Configuration properties
- `/actuator/metrics` - Internal metrics
- `/actuator/health/readiness` - System readiness
- `/actuator/threaddump` - Active threads
- `/actuator/heapdump` - Memory dump

**Fix** (apply to all services):
```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(authz -> authz
        .requestMatchers("/actuator/health/readiness", "/actuator/health/liveness")
            .permitAll()  // ✅ Public endpoints OK
        .requestMatchers("/actuator/**")
            .hasRole("ADMIN")  // ✅ Require auth
        .requestMatchers("/api/**")
            .authenticated()
    );
    return http.build();
}
```

**Scope**: SERVICE-ONLY per service  
**Risk**: LOW  
**Severity**: HIGH  
**Verification**:
```bash
# Should return 401
curl http://localhost:8080/actuator/env

# Should return 200
curl http://localhost:8080/actuator/health
```

---

## 🔴 HIGH: JWT Validation Missing Signature Verification

**Issue**: Offline JWT validation doesn't verify RSA/HS256 signature

**Location**: `common-security/src/main/java/com/example/common/security/JwtDecoder.java` or service-local decoders

**Broken Code Pattern**:
```java
// ❌ Bad - no signature verification
public Claims validateToken(String token) {
    return Jwts.parserBuilder()
        .build()  // No signing key!
        .parseClaimsJwt(token)  // ⚠️ Unverified
        .getBody();
}
```

**Fix**:
```java
// ✅ Good - signature verified
public Claims validateToken(String token) {
    return Jwts.parserBuilder()
        .setSigningKey(getSigningKey())  // RSA public key or HS256 secret
        .build()
        .parseClaimsJws(token)  // ✅ Verified
        .getBody();
}
```

**Impact**: Token forgery possible (attacker creates valid-looking JWT)

**Scope**: COMMON-REQUIRED  
**Risk**: LOW to fix (should only be changed in central location)  
**Verification**:
```java
@Test
public void testJwtWithInvalidSignature_FailsValidation() {
    String fakeToken = "eyJhbGc...";  // Invalid signature
    assertThrows(JwtException.class, () -> jwtDecoder.validate(fakeToken));
}
```

---

## 🟠 HIGH: UserId Spoofing Risk

**Issue**: userId taken from request body instead of JWT

**Example**:
```java
// ❌ Bad - trusts request body
@PostMapping("/profile")
public void updateProfile(@RequestBody UpdateProfileRequest req) {
    // req.userId could be ANY user ID!
    profileService.update(req.userId, req.profile);
}

// ✅ Good - extracts from JWT
@PostMapping("/profile")
public void updateProfile(
        @AuthenticationPrincipal JwtAuthenticationToken auth,
        @RequestBody UpdateProfileRequest req) {
    String userId = auth.getName();  // From JWT
    profileService.update(userId, req.profile);
}
```

**Verification**: Audit all endpoints that accept userId:
- `/api/users/{userId}/profile` - ✅ Path parameter OK (verified in service)
- `/api/users/profile` - ✅ Extract from JWT
- `/api/chat/rooms/{roomId}/messages` - ✅ senderId from JWT
- `/api/notifications/mark-read` - ✅ userId from JWT

**Scope**: SERVICE-ONLY (each endpoint)  
**Risk**: MEDIUM (requires careful audit)  
**Severity**: HIGH

---

## CORS Configuration

### 🟡 MEDIUM: Duplicate CORS Setup

**Issue**: Both gateway AND individual services configure CORS

**Current**: Common module provides `CorsProperties`, services use it

**Recommendation**: Centralize at gateway only; remove from services

**Expected Gateway CORS**:
```yaml
cors:
  allowed-origins:
    - http://localhost:3000  # Frontend dev
    - https://chatapp.example.com  # Production
  allowed-methods: GET,POST,PUT,DELETE,OPTIONS
  allowed-headers: Authorization,Content-Type
  allow-credentials: true
```

**Verification**: Only gateway should respond with `Access-Control-Allow-Origin` headers

---

## 🟡 MEDIUM: File Upload Security

### Path Traversal (Covered in 10-upload-service-review.md)

### File Type Validation
**Issue**: Weak content-type checking

**Fix**: Validate file signatures (magic bytes)

---

## 🟡 MEDIUM: Refresh Token Security

**Issue**: Refresh token may be reusable after one use (forgetting to revoke)

**Expected**:
1. POST `/api/auth/refresh` with old refresh token
2. Server marks old token as revoked
3. Future use of old token → 401 Unauthorized

**Verification**: Check `auth-service` implementation

---

## CSRF Protection

**Current Status**: CSRF disabled (expected for stateless REST API)

**Verification**: `http.csrf().disable()` is intentional

---

## WebSocket Handshake Authentication

### Realtime Edge Service

**Issue**: JWT validation during WebSocket upgrade

**Expected Behavior**:
1. Client connects with `Authorization: Bearer <token>` header
2. `JwtHandshakeInterceptor` extracts & validates token
3. Invalid token → connection rejected

**Verification**:
```bash
# Should fail
wscat -c ws://localhost:8089/ws

# Should succeed
wscat -c "ws://localhost:8089/ws" -H "Authorization: Bearer <valid-token>"
```

**Scope**: SERVICE-ONLY (realtime-edge-service)  
**Risk**: HIGH if missing

---

## Cross-Service Auth (Internal Endpoints)

### Issue: Can external clients call internal endpoints?

**Example**: Can unauthorized user call `POST /internal/user/{userId}/profile` (intended for internal services only)?

**Expected**: Internal endpoints require service-to-service auth (API key or mTLS)

**Current**: Not documented; need to verify

**Verification**: Check for `@InternalEndpoint` annotation or `/internal/**` path pattern with special auth

---

## Error Message Leakage

### Potential Issues
- "Username already registered" (leaks that user exists)
- "Invalid password for user X" (leaks user validation)
- Stack traces in error responses (leaks internal structure)

**Fix**: Generic error messages for public endpoints
```java
// ❌ Bad
{
  "error": "User john_doe not found"
}

// ✅ Good
{
  "error": "Invalid credentials"
}
```

---

## Rate Limiting

### Not Configured?
**Issue**: No rate limiting → brute force attacks possible

**Example**: Attacker tries 1,000,000 password combinations per second

**Fix**: Add rate limiting to public endpoints (gateway level)
```yaml
spring:
  cloud:
    gateway:
      routes:
      - id: auth
        predicates:
        - Path=/api/auth/login
        filters:
        - name: RequestRateLimiter
          args:
            redis-rate-limiter.replenishRate: 10
            redis-rate-limiter.burstCapacity: 20
```

**Scope**: GATEWAY or COMMON-OPTIONAL  
**Risk**: MEDIUM (Resilience4j + Redis needed)

---

## Summary

| Issue | Severity | Scope | Action |
|-------|----------|-------|--------|
| Actuator exposed | HIGH | SERVICE-ONLY | Require auth on all services |
| JWT signature not verified | HIGH | COMMON-REQUIRED | Fix in JwtDecoder |
| UserId spoofing risk | HIGH | SERVICE-ONLY | Audit & fix endpoints |
| File upload path traversal | HIGH | SERVICE-ONLY | Validate publicId |
| Weak file type validation | HIGH | SERVICE-ONLY | Check magic bytes |
| Refresh token not revoked | HIGH | SERVICE-ONLY | Verify auth-service |
| WebSocket handshake auth | MEDIUM | SERVICE-ONLY | Verify realtime-edge |
| No rate limiting | MEDIUM | GATEWAY | Add RequestRateLimiter |
| Error message verbosity | LOW | SERVICE-ONLY | Generic messages |

---

**Next**: Read 15-database-transaction-consistency-review.md

# User Service Review

**Service**: User Profiles (Port 8082)  
**Primary Responsibility**: User profiles, avatars, user search, profile updates  
**Tech Stack**: Spring Boot, Spring Data JPA, Postgres, Redis cache  

---

## Quick Assessment

| Item | Status | Notes |
|------|--------|-------|
| Compilation | ✅ | Deprecation warning in UserProfileService (LOW) |
| Bean Wiring | ✅ | RedisCacheConfig present but may be commented out (verify) |
| Service Boundaries | ✅ | Clear (profiles only, not auth) |
| API | ✅ | REST endpoints properly named |
| Security | 🟠 | Actuator exposed, userId spoofing risk |
| Database | 🟠 | No @EntityGraph on search queries (N+1 risk) |
| Cache | 🟠 | May have stale user data in Redis |

---

## Key Findings

### 🔴 HIGH: Cache Invalidation Not Distributed

**Issue**: When user profile is updated in one instance, Redis cache is cleared locally but other instances don't know

**Example**: 
1. Service-1: User updates avatar → clears local Redis cache
2. Service-2: Still serves old avatar from its Redis cache

**Location**: `user-service/src/main/java/com/example/user/service/impl/UserProfileService.java`

**Fix Options**:
- **Option 1**: Publish event to Redis pub/sub when cache invalidates (then all instances clear)
- **Option 2**: Use shorter TTL on cache (e.g., 5 minutes vs. 1 hour)
- **Option 3**: Don't cache user profiles (accept database hit)

**Scope**: SERVICE-ONLY  
**Risk**: MEDIUM (requires testing)  

---

### 🟡 MEDIUM: N+1 Query on User Search

**Issue**: Search query may load all users without fetching related fields efficiently

**Location**: Check for `@Query` methods in `UserRepository`

**Example N+1 Risk**:
```java
// ❌ Bad - N+1 queries
List<User> users = userRepository.findByNameContaining("john");
for (User u : users) {
    String avatarUrl = u.getAvatar().getUrl();  // Separate query per user!
}

// ✅ Good - Single query with join
@EntityGraph(attributePaths = {"avatar"})
List<User> findByNameContaining(String name);
```

**Fix**: Add `@EntityGraph` annotations  
**Scope**: SERVICE-ONLY  
**Risk**: LOW  

---

### 🟠 HIGH: Actuator Endpoints Exposed

(Same as gateway - see 03-gateway-service-review.md)

**Fix**: Secure `/actuator/**` endpoints  

---

### 🟡 MEDIUM: User Existence Check

When user is deleted, can other services still query them?

**Expected**: GET /users/{userId} → 404 Not Found  
**Risk**: If deleted user remains in cache, ghost user returns

**Fix**: Ensure cache invalidation on user deletion

---

## Verification

```bash
# Compile
./gradlew.bat :user-service:compileJava --no-daemon

# Test user profile fetch + cache hit
docker-compose up user-service -d
curl -H "Authorization: Bearer <token>" http://localhost:8082/api/users/profile
```

---

**Priority Fixes**: HIGH cache invalidation + actuator security  
**Estimated Effort**: 2-4 hours  

**Next**: Read 06-chat-service-review.md

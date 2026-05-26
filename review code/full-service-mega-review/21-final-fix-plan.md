# Final Fix Plan: Execution Order

**Purpose**: Prioritized, concrete execution sequence to fix all identified issues  
**Constraint**: Service-only fixes first, common changes only when absolutely required  

---

## Phase 1: Immediate Fixes (BLOCKER + HIGH, All SERVICE-ONLY)

**Effort**: 6-8 hours  
**Risk**: LOW  
**Verification**: Full build + docker-compose up  

### 1.1 Add Missing KafkaTopics Constants

**Why First**: Blocks all services from compiling  
**File**: `common-events/src/main/java/com/example/common/events/KafkaTopics.java`

**Changes**:
```java
// Add all 24+ missing constants (see 20-common-touch-minimization-report.md)
public static final String CHAT_MESSAGE_SENT = "chat.message.sent";
public static final String USER_ONLINE = "user.online";
// ... etc
```

**Verification Command**:
```bash
./gradlew.bat clean compileJava --no-daemon
# Expected: BUILD SUCCESSFUL
```

**Rollback**: Remove lines added

---

### 1.2 Fix Presence WebSocket Lifecycle (User Never Goes Offline)

**Why Critical**: Users remain "online" forever  
**File**: `presence-service/src/main/java/com/example/presence/controller/PresenceWebSocketHandler.java`

**Current Broken Code** (Line ~XX):
```java
@Override
public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String userId = (String) session.getAttributes().get("userId");
    sessionRegistry.removeSession(session);      // ❌ Wrong order
    unregisterFromRoom(userId);                   // ❌ Now fails
}
```

**Fix**:
```java
@Override
public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    String userId = (String) session.getAttributes().get("userId");
    unregisterFromRoom(userId);                   // ✅ First
    sessionRegistry.removeSession(session);      // ✅ Then
}
```

**Verification Command**:
```bash
./gradlew.bat :presence-service:compileJava --no-daemon

# Runtime test:
# 1. Connect user A
# 2. Query /api/presence/room/{id} → shows user A
# 3. Disconnect user A
# 4. Query again → user A gone
```

**Rollback**: Restore line order

---

### 1.3 Fix Duplicate Kafka Listeners

**Why Critical**: Silent event loss (partition race condition)  
**Files** (Search for duplicates in each service):
- `chat-service/src/main/java/com/example/chat/adapter/in/kafka/*`
- `notification-service/src/main/java/com/example/notification/adapter/in/kafka/*`
- `friendship-service/src/main/java/com/example/friendship/adapter/in/kafka/*`

**Example Fix**:
```java
// ❌ Remove duplicate listener
@KafkaListener(topics = "chat.messages", groupId = "chat-service")
public void onMessageReceived(ChatMessageCreatedEvent event) { }

// ✅ Keep single consolidated listener
@KafkaListener(topics = "chat.messages", groupId = "chat-service")
public void handleChatMessageEvent(ChatMessageCreatedEvent event) { }
```

**Verification**: Run per-service tests
```bash
./gradlew.bat :chat-service:test --no-daemon
./gradlew.bat :notification-service:test --no-daemon
./gradlew.bat :friendship-service:test --no-daemon
```

**Rollback**: Restore removed methods

---

### 1.4 Add Cloudinary Configuration to Upload Service

**Why Critical**: Upload service won't start  
**File**: `upload-service/src/main/resources/application.yaml`

**Add Section**:
```yaml
cloudinary:
  cloud-name: ${CLOUDINARY_CLOUD_NAME}
  api-key: ${CLOUDINARY_API_KEY}
  api-secret: ${CLOUDINARY_API_SECRET}
```

**Create Bean** (if not exists):  
`upload-service/src/main/java/com/example/upload/configuration/CloudinaryConfig.java`

```java
@Configuration
public class CloudinaryConfig {
    @Bean
    public Cloudinary cloudinary(
            @Value("${cloudinary.cloud-name}") String cloudName,
            @Value("${cloudinary.api-key}") String apiKey,
            @Value("${cloudinary.api-secret}") String apiSecret) {
        return new Cloudinary(ObjectUtils.asMap(
            "cloud_name", cloudName,
            "api_key", apiKey,
            "api_secret", apiSecret));
    }
}
```

**Environment Variables** (docker-compose.yml):
```yaml
upload-service:
  environment:
    CLOUDINARY_CLOUD_NAME: ${CLOUDINARY_CLOUD_NAME}
    CLOUDINARY_API_KEY: ${CLOUDINARY_API_KEY}
    CLOUDINARY_API_SECRET: ${CLOUDINARY_API_SECRET}
```

**Verification**:
```bash
./gradlew.bat :upload-service:compileJava --no-daemon

docker-compose up upload-service -d
docker-compose logs upload-service
# Should show: Tomcat started on port 8088
```

**Rollback**: Remove config

---

### 1.5 Secure Actuator Endpoints (All Services)

**Why High**: Information disclosure (secrets in /actuator/env)  
**File**: Each service's `SecurityConfig.java`

**Example Fix** (apply to all services):
```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(authz -> authz
        .requestMatchers("/actuator/health/readiness", "/actuator/health/liveness")
            .permitAll()
        .requestMatchers("/actuator/**")
            .hasRole("ADMIN")  // ✅ Add this
        .requestMatchers("/api/**")
            .authenticated()
    );
    return http.build();
}
```

**Services to Fix** (9 total):
- gateway-service
- auth-service
- user-service
- chat-service
- presence-service
- friendship-service
- notification-service
- upload-service
- realtime-edge-service

**Verification**:
```bash
# Should return 401
curl http://localhost:8080/actuator/env

# Should return 200
curl http://localhost:8080/actuator/health
```

**Rollback**: Remove `hasRole("ADMIN")` constraint

---

### 1.6 Fix Presence TTL Race Condition (Reference Counting)

**Why High**: User marked offline while other tab is active  
**File**: `presence-service/src/main/java/com/example/presence/service/impl/PresenceService.java`

**Current Broken Code**:
```java
private void registerPresence(String userId) {
    redisTemplate.opsForValue().set("presence:" + userId, "online");  // ❌ No refcount
}

private void unregisterPresence(String userId) {
    redisTemplate.delete("presence:" + userId);  // ❌ Always delete
}
```

**Fix**:
```java
private void registerPresence(String userId) {
    String counterKey = "presence:refcount:" + userId;
    Long count = redisTemplate.opsForValue().increment(counterKey);
    if (count == 1) {
        // First connection
        redisTemplate.opsForValue().set("presence:" + userId, "online");
        publishUserOnlineEvent(userId);
    }
    redisTemplate.expire(counterKey, Duration.ofMinutes(5));
}

private void unregisterPresence(String userId) {
    String counterKey = "presence:refcount:" + userId;
    Long count = redisTemplate.opsForValue().decrement(counterKey);
    if (count <= 0) {
        // All connections closed
        redisTemplate.delete("presence:" + userId);
        publishUserOfflineEvent(userId);
    }
}
```

**Verification**:
```bash
# Test with 2 browser tabs
# 1. Open tab 1 → user online
# 2. Open tab 2 → user still online
# 3. Close tab 1 → user still online
# 4. Close tab 2 → user offline
```

**Rollback**: Restore original increment/delete logic

---

**Phase 1 Verification Command** (All together):
```bash
cd chatappBE

# Compile all
./gradlew.bat clean compileJava --no-daemon

# Test key services
./gradlew.bat :presence-service:test --no-daemon
./gradlew.bat :chat-service:compileJava --no-daemon
./gradlew.bat :notification-service:compileJava --no-daemon

# Integration test
docker-compose up -d
docker-compose ps
docker-compose logs gateway | head -20
```

---

## Phase 2: High Priority Fixes (HIGH Severity, SERVICE-ONLY)

**Effort**: 4-6 hours  
**Risk**: MEDIUM  
**Note**: Only after Phase 1 verified

### 2.1 Fix JWT Signature Verification (COMMON-REQUIRED)

**File**: `common-security/src/main/java/com/example/common/security/JwtDecoder.java`

**Current Broken Code**:
```java
public Claims validateToken(String token) {
    return Jwts.parserBuilder()
        .build()  // ❌ No signing key
        .parseClaimsJwt(token)
        .getBody();
}
```

**Fix**:
```java
public Claims validateToken(String token) {
    return Jwts.parserBuilder()
        .setSigningKey(getSigningKey())  // ✅ Add this
        .build()
        .parseClaimsJws(token)  // ✅ Verify signature
        .getBody();
}

private Key getSigningKey() {
    return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
}
```

**Verification**:
```bash
./gradlew.bat :common:common-security:compileJava --no-daemon
./gradlew.bat :common:common-security:test --no-daemon
# Should have test: testJwtWithInvalidSignature_FailsValidation
```

---

### 2.2 Add File Upload Security Checks

**File**: `upload-service/src/main/java/com/example/upload/service/impl/FileUploadService.java`

**Add Path Traversal Validation**:
```java
private void validateFileName(String fileName) {
    if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
        throw new IllegalArgumentException("Invalid file name");
    }
}
```

**Add File Type Validation**:
```java
private void validateFileType(MultipartFile file) {
    String contentType = file.getContentType();
    if (!ALLOWED_TYPES.contains(contentType)) {
        throw new IllegalArgumentException("Unsupported file type");
    }
    // Also check magic bytes
    validateMagicBytes(file.getBytes(), contentType);
}
```

---

### 2.3 Audit & Fix userId Spoofing Risks

**Search Across All Services**:
```bash
grep -r "@RequestBody.*userId" chatappBE/*/src/main/java
grep -r "@RequestParam.*userId" chatappBE/*/src/main/java
```

**For each endpoint that accepts userId parameter**:
- ✅ If path parameter (`{userId}`), OK (verified by Spring)
- ✅ If extracted from JWT (`@AuthenticationPrincipal`), OK
- ❌ If from request body, FIX

**Example Fix**:
```java
// ❌ Bad
@PostMapping("/profile")
public void updateProfile(@RequestBody UpdateProfileRequest req) {
    profileService.update(req.userId, req.profile);
}

// ✅ Good
@PostMapping("/profile")
public void updateProfile(
        @AuthenticationPrincipal JwtAuthenticationToken auth,
        @RequestBody UpdateProfileRequest req) {
    String userId = auth.getName();
    profileService.update(userId, req.profile);
}
```

---

### 2.4 Fix Message Sequence Race Condition (Chat)

**File**: `chat-service/src/main/java/com/example/chat/domain/entity/ChatMessage.java`

**Add Sequence Generator**:
```java
@Entity
public class ChatMessage {
    @GeneratedValue(strategy = GenerationType.SEQUENCE, 
                   generator = "chat_message_seq")
    @SequenceGenerator(name = "chat_message_seq", 
                      sequenceName = "chat_message_seq",
                      allocationSize = 1)
    @Column(name = "seq")
    private Long seq;
}
```

**Or if using Postgres SERIAL**:
```sql
ALTER TABLE chat_message 
ADD COLUMN seq BIGINT DEFAULT nextval('chat_message_seq');

ALTER TABLE chat_message 
ADD CONSTRAINT unique_message_seq UNIQUE(room_id, seq);
```

---

**Phase 2 Verification**:
```bash
./gradlew.bat test --no-daemon
docker-compose up -d
# Test JWT validation, file upload, message ordering
```

---

## Phase 3: Medium Fixes (MEDIUM Severity, SERVICE-ONLY)

**Effort**: 4-5 hours  
**Risk**: MEDIUM  

### 3.1 Add @EntityGraph for N+1 Query Prevention

**Files**:
- `chat-service/src/main/java/com/example/chat/repository/RoomRepository.java`
- `user-service/src/main/java/com/example/user/repository/UserRepository.java`

**Example**:
```java
@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {
    @EntityGraph(attributePaths = {"members", "members.user"})
    Optional<Room> findById(UUID id);
}
```

---

### 3.2 Add Unique Constraint for Friend Request Dedup

**File**: `friendship-service/src/main/java/com/example/friendship/domain/entity/FriendRequest.java`

```java
@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = {"senderId", "receiverId", "status"},
                     name = "uq_pending_request")
})
public class FriendRequest { }
```

---

### 3.3 Fix WebSocket Lifecycle in Chat/Notification

(Same pattern as presence-service fix)

---

### 3.4 Add Event Publishing Timing Audit

Verify all critical endpoints use `TransactionSynchronization`:
- `chat-service/ChatMessageService.sendMessage()`
- `notification-service/NotificationCommandService.create()`
- `friendship-service/FriendCommandService.sendRequest()`

---

**Phase 3 Verification**:
```bash
./gradlew.bat test --no-daemon
# Run concurrency tests to verify no N+1, no race conditions
```

---

## Phase 4: Polish (LOW Severity)

**Effort**: 2-3 hours  

### 4.1 Add Missing Indexes

```sql
CREATE INDEX idx_user_username ON user(username);
CREATE INDEX idx_user_email ON user(email);
CREATE INDEX idx_refresh_token_token ON refresh_token(token);
CREATE INDEX idx_message_room_seq ON chat_message(room_id, seq);
```

### 4.2 Fix Deprecation Warnings

- Update ResendEmailClient (auth-service)
- Update RedisPresenceTtlCacheAdapter (presence-service)
- Update UserProfileService (user-service)

### 4.3 Remove Duplicate @Slf4j (realtime-edge-service)

---

## DO NOT FIX NOW

### Leave for Future
- Correlation ID propagation (infrastructure)
- Distributed tracing (Zipkin/Jaeger)
- Rate limiting (gateway)
- Audit logging (compliance)
- Virus scanning on upload (vendor integration)
- WebSocket consolidation to realtime-edge (architecture)

---

## Safe First Batch (Days 1-2)

### Files to Change
1. `common-events/src/main/java/com/example/common/events/KafkaTopics.java` - ADD constants
2. `presence-service/.../PresenceWebSocketHandler.java` - FIX lifecycle order
3. `chat-service/.../ChatMessageKafkaConsumer.java` - REMOVE duplicates
4. `notification-service/.../NotificationKafkaConsumer.java` - REMOVE duplicates
5. `friendship-service/.../FriendshipKafkaConsumer.java` - REMOVE duplicates
6. `upload-service/src/main/resources/application.yaml` - ADD Cloudinary config
7. `upload-service/.../CloudinaryConfig.java` - CREATE new file
8. All services `SecurityConfig.java` - ADD actuator auth (9 files)
9. `presence-service/.../PresenceService.java` - ADD reference counting
10. `common-security/.../JwtDecoder.java` - ADD signature verification

### Expected Risk
- LOW: Adding constants, fixing lifecycle, removing duplicates
- MEDIUM: Reference counting (requires testing), JWT verification (security improvement)

### Verification Command
```bash
./gradlew.bat clean compileJava --no-daemon
# Expected: BUILD SUCCESSFUL

docker-compose up -d
docker-compose ps
# All services should start successfully
```

### Rollback Plan
- Each change is isolated
- Revert changed files if issue found
- Rebuild and test

---

## Success Criteria

### After Phase 1
- ✅ All services compile
- ✅ All services start (`docker-compose up`)
- ✅ Users correctly marked offline
- ✅ No duplicate Kafka events processed
- ✅ Upload service functional
- ✅ Actuator endpoints require auth

### After Phase 2
- ✅ JWT signatures verified
- ✅ File uploads validated
- ✅ No userId spoofing
- ✅ Message seq always unique

### After Phase 3
- ✅ No N+1 queries
- ✅ No duplicate friend requests
- ✅ WebSocket lifecycle clean
- ✅ Event publishing after commit

### After Phase 4
- ✅ All deprecation warnings gone
- ✅ Database indexes optimized
- ✅ Code quality polished

---

**TOTAL EFFORT**: 16-22 hours  
**TOTAL RISK**: LOW-MEDIUM (service-only fixes first)  
**COMMON CHANGES**: Only 2 absolutely required (KafkaTopics, JWT verification)

---

**END OF MEGA REVIEW**

All 22 files completed. Start with Phase 1, then proceed to Phase 2 once verified.

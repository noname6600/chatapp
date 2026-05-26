# Test Coverage Review

**Focus**: Unit tests, integration tests, WebSocket tests, concurrency tests, test quality  

---

## Phase 6 Work: Restored Tests

**Status**: ✅ Friendship and notification tests restored

**Tests Verified**:
- `FriendCommandServiceTest` - PASSING
- `FriendCommandServiceAfterCommitPublicationTest` - PASSING
- `InternalFriendControllerTest` - PASSING
- `NotificationKafkaEventApplicationServiceTest` - PASSING
- `NotificationKafkaConsumersTest` - PASSING

**Verification**:
```bash
./gradlew.bat :friendship-service:test --no-daemon
./gradlew.bat :notification-service:test --no-daemon
```

---

## Test Gaps

### 🟡 MEDIUM: Missing WebSocket Tests

**Issue**: WebSocket handler behavior not tested

**Current**: Likely no tests for:
- WebSocket handshake auth
- Connection lifecycle
- Message subscription/unsubscription
- Multi-client broadcast
- Disconnect cleanup

**Recommended Tests**:
```java
@SpringBootTest
@AutoConfigureWebTestClient
public class PresenceWebSocketTest {
    @Test
    public void testWebSocketConnectWithValidJwt_Success() { }
    
    @Test
    public void testWebSocketConnectWithoutJwt_Rejected() { }
    
    @Test
    public void testUserGoesOnline_OtherUsersNotified() { }
    
    @Test
    public void testDisconnect_UserMarkedOffline() { }
    
    @Test
    public void testMultiTabDisconnect_UserStillOnline() { }  // Reference counting
}
```

**Scope**: SERVICE-ONLY  
**Risk**: MEDIUM (WebSocket testing is non-trivial)

---

### 🟡 MEDIUM: Missing Concurrency Tests

**Issue**: Race conditions not tested

**Current**: Likely no tests for:
- Concurrent message sends (seq collision)
- Concurrent friend request + cancel
- Concurrent block + accept

**Recommended Tests**:
```java
@Test
public void testConcurrentMessageSend_AllHaveUniqueSeq() {
    ExecutorService exec = Executors.newFixedThreadPool(10);
    List<Future<?>> futures = new ArrayList<>();
    
    for (int i = 0; i < 100; i++) {
        futures.add(exec.submit(() -> {
            chatMessageService.sendMessage(testRoom, "message " + i);
        }));
    }
    
    futures.forEach(f -> f.get());  // Wait for all
    
    List<ChatMessage> messages = messageRepository.findByRoomId(testRoom);
    Set<Long> seqs = new HashSet<>(messages.stream()
        .map(ChatMessage::getSeq)
        .collect(Collectors.toSet()));
    
    assertEquals(100, seqs.size());  // All unique
}
```

**Scope**: SERVICE-ONLY  
**Risk**: MEDIUM

---

### 🟡 MEDIUM: Missing Kafka Consumer Tests

**Issue**: Consumer behavior not fully tested

**Current**: Phase 6 tests restored, verify they pass

**Test for**:
- Duplicate event handling (idempotency)
- Dead letter topic routing
- Consumer group rebalance

**Recommended**:
```java
@Test
public void testKafkaEvent_Duplicate_HandledIdempotently() {
    // Publish same event twice
    kafkaTemplate.send("chat.messages", event1);
    kafkaTemplate.send("chat.messages", event1);  // Duplicate
    
    // Verify only one notification created
    Thread.sleep(1000);  // Wait for processing
    long count = notificationRepository.count();
    assertEquals(1, count);
}
```

**Scope**: SERVICE-ONLY  
**Risk**: MEDIUM

---

## Integration Test Coverage

### 🟡 MEDIUM: End-to-End Flows Not Tested

**Example Missing Test**:
```java
@SpringBootTest
public class EndToEndMessageFlowTest {
    @Test
    public void testSendMessageFlow() {
        // 1. Register user
        User user = authService.register(...);
        
        // 2. Create room
        Room room = chatService.createRoom(...);
        
        // 3. Send message
        ChatMessage msg = chatService.sendMessage(room, "Hello");
        
        // 4. Verify Kafka event published
        // 5. Verify notification created
        // 6. Verify other users see message
    }
}
```

**Scope**: SERVICE-ONLY  
**Risk**: LOW (if existing tests cover this)

---

## Test Fixtures & Factories

### 🟡 LOW: Duplicate Test Data Creation

**Issue**: Each test may create similar data

**Recommendation**: Use test fixtures/factories
```java
@Component
public class TestDataFactory {
    public User createTestUser(String username) { }
    public Room createTestRoom(String name) { }
    public ChatMessage createTestMessage(Room room, String text) { }
}
```

**Scope**: SERVICE-ONLY  
**Risk**: LOW (code quality)

---

## Flaky Test Detection

### Question: Are tests flaky?

**Symptoms**:
- Test passes sometimes, fails randomly
- Usually due to:
  - Timing assumptions (sleep + race)
  - DB state from previous test
  - Kafka offset/timing issues

**Prevention**:
- Use TestContainers for isolated DB/Kafka per test
- Avoid Thread.sleep()
- Use awaitility or similar for eventual consistency

---

## Controller/API Tests

### 🟡 MEDIUM: Missing Controller Validation Tests

**Example**:
```java
@Test
public void testRegisterWithShortPassword_Returns400() {
    mockMvc.perform(post("/api/auth/register")
        .contentType(APPLICATION_JSON)
        .content("{\"username\":\"test\",\"password\":\"short\"}")
    ).andExpect(status().isBadRequest());
}
```

**Verification**: Check if validation tests exist for each service

---

## Mutation Testing

### DO-NOT-FIX-NOW

**Tool**: PIT (Pitest)  
**Benefit**: Detects weak tests (tests that pass even with code changes)

**Not critical but useful for quality assessment**

---

## Security Tests

### 🟡 MEDIUM: Missing Security-Specific Tests

**Example**:
```java
@Test
public void testUserCannotUpdateAnotherUsersProfile() {
    String otherUserId = "uuid-other";
    mockMvc.perform(put("/api/users/" + otherUserId + "/profile")
        .header("Authorization", "Bearer " + myToken)
        .content("{\"bio\":\"hacked\"}")
    ).andExpect(status().isForbidden());
}

@Test
public void testJwtWithInvalidSignature_Rejected() {
    String fakeJwt = "eyJhbGc...";  // Invalid signature
    mockMvc.perform(get("/api/users/profile")
        .header("Authorization", "Bearer " + fakeJwt)
    ).andExpect(status().isUnauthorized());
}
```

**Scope**: SERVICE-ONLY  
**Risk**: LOW

---

## TestContainers

### Current Usage: Unknown

**Recommendation**: Use for isolated test environments
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <scope>test</scope>
</dependency>
```

**Benefits**:
- Real Postgres, Redis, Kafka (not mocked)
- Isolated per test
- Tests reflect production behavior

---

## Summary

| Issue | Severity | Scope | Action |
|-------|----------|-------|--------|
| WebSocket tests missing | MEDIUM | SERVICE-ONLY | Add WebSocket integration tests |
| Concurrency tests missing | MEDIUM | SERVICE-ONLY | Add concurrent scenario tests |
| Kafka consumer tests incomplete | MEDIUM | SERVICE-ONLY | Verify phase 6 restored tests |
| Security tests missing | MEDIUM | SERVICE-ONLY | Add permission + JWT validation tests |
| Test fixtures duplicated | LOW | SERVICE-ONLY | Create TestDataFactory |
| E2E flow tests missing | LOW | SERVICE-ONLY | Add end-to-end test |

---

**Next**: Read 19-deployment-docker-nginx-config-review.md

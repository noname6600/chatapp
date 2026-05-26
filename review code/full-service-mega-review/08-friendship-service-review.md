# Friendship Service Review

**Service**: Friend Management (Port 8086)  
**Responsibility**: Friend requests, accept/decline, friend list, blocking  
**Tech Stack**: Spring Boot, Spring Data JPA, Postgres, Kafka  

---

## Status Summary

| Category | Status | Notes |
|----------|--------|-------|
| Compilation | ✅ | BUILD SUCCESSFUL |
| Bean Wiring | ✅ | KafkaProducerAdapterConfig present |
| Tests Restored | ✅ | FriendCommandServiceTest fixed in phase 6 |
| Kafka Constants | 🔴 | Missing FRIEND_REQUEST_* constants |
| Actuator | 🔴 | Exposed unauthenticated |

---

## Critical Issues

| Issue | Severity | Scope | Action |
|-------|----------|-------|--------|
| Kafka constants missing | BLOCKER | COMMON-REQUIRED | Add to KafkaTopics |
| Race: accept vs. cancel | HIGH | SERVICE-ONLY | Test concurrent scenarios |
| Race: block vs. accept | HIGH | SERVICE-ONLY | Test concurrent scenarios |
| Actuator exposed | HIGH | SERVICE-ONLY | Require auth |

---

## 🔴 BLOCKER: Kafka Topic Constants Undefined

**Missing Constants**:
- `KafkaTopics.FRIEND_REQUEST_SENT`
- `KafkaTopics.FRIEND_REQUEST_ACCEPTED`
- `KafkaTopics.FRIEND_REQUEST_DECLINED`
- `KafkaTopics.USER_BLOCKED`

**Fix**: Add to `common-events/KafkaTopics.java`:
```java
public static final String FRIEND_REQUEST_SENT = "friendship.request.sent";
public static final String FRIEND_REQUEST_ACCEPTED = "friendship.request.accepted";
public static final String FRIEND_REQUEST_DECLINED = "friendship.request.declined";
public static final String USER_BLOCKED = "friendship.user.blocked";
```

**Scope**: COMMON-REQUIRED  
**Risk**: LOW

---

## 🟠 HIGH: Race Condition - Accept vs. Cancel

**Scenario**:
1. User A sends friend request to User B
2. User B: Accept request (thread 1)
3. User A: Cancel request (thread 2)
4. Result: Inconsistent state (friendship exists? request exists?)

**Fix**: Add transaction-level locking or unique constraints

**Correct Implementation**:
```java
@Transactional
public void acceptRequest(String requestId) {
    FriendRequest req = requestRepo.findById(requestId)
        .orElseThrow(() -> new NotFoundException());
    
    if (!req.getStatus().equals(PENDING)) {
        throw new IllegalStateException("Request not pending");
    }
    
    req.setStatus(ACCEPTED);
    requestRepo.save(req);  // Update status
    
    Friendship friendship = new Friendship(req.getSender(), req.getReceiver());
    friendshipRepo.save(friendship);
}

@Transactional
public void cancelRequest(String requestId) {
    FriendRequest req = requestRepo.findById(requestId)
        .orElseThrow(() -> new NotFoundException());
    
    if (!req.getStatus().equals(PENDING)) {
        throw new IllegalStateException("Request already processed");
    }
    
    req.setStatus(CANCELLED);
    requestRepo.save(req);
}
```

**Verification**: Run concurrent test
```java
@Test
public void testConcurrentAcceptAndCancel_OnlyOneSucceeds() {
    ExecutorService exec = Executors.newFixedThreadPool(2);
    // Thread 1: accept
    // Thread 2: cancel
    // Verify only one succeeds
}
```

**Scope**: SERVICE-ONLY  
**Risk**: MEDIUM (concurrency testing required)

---

## 🟠 HIGH: Race Condition - Block vs. Accept

**Scenario**:
1. User B receives friend request from User A
2. User B: Block User A (thread 1)
3. User B: Accept request (thread 2)
4. Result: Friendship created with blocked user (bad)

**Fix**: Check block status before accepting

```java
@Transactional
public void acceptRequest(String requestId) {
    FriendRequest req = requestRepo.findById(requestId).orElseThrow();
    
    // Check if sender is blocked
    if (blockService.isBlocked(req.getReceiver(), req.getSender())) {
        throw new IllegalStateException("Cannot accept request from blocked user");
    }
    
    // ... rest of accept logic
}

@Transactional
public void blockUser(String userId, String blockedUserId) {
    // Also cancel any pending requests with this user
    requestRepo.deleteByReceiverAndSenderAndStatus(userId, blockedUserId, PENDING);
}
```

**Scope**: SERVICE-ONLY  
**Risk**: MEDIUM

---

## Phase 6 Work Verification

**Tests Restored**: FriendCommandServiceTest passes ✅

**Verify**:
```bash
./gradlew.bat :friendship-service:test --tests "com.example.friendship.service.impl.FriendCommandServiceTest" --no-daemon
```

Should show: BUILD SUCCESSFUL

---

## Verification

```bash
./gradlew.bat :friendship-service:compileJava --no-daemon
./gradlew.bat :friendship-service:test --no-daemon
```

---

**Priority**: Fix BLOCKER (Kafka constants) + HIGH (race conditions)  
**Estimated Effort**: 3-4 hours

**Next**: Read 09-notification-service-review.md

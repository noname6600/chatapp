# Database, Transaction & Consistency Review

**Focus**: JPA/Hibernate, transaction boundaries, N+1 queries, constraints, race conditions  

---

## Shared Database Architecture

**Setup**: Single Postgres instance, separate schemas per service

**Benefit**: Services can be deployed independently  
**Risk**: No cross-service transaction guarantees (eventual consistency required)

---

## 🟠 HIGH: N+1 Query Problems

### Chat Service: Room Queries

**Issue**: Loading room with participants triggers N+1 queries

**Broken Code**:
```java
// ❌ Bad - N+1 queries
Room room = roomRepository.findById(roomId);  // 1 query
for (RoomMember member : room.getMembers()) {  // N queries
    String username = member.getUser().getUsername();
}
```

**Fix**: Use `@EntityGraph`
```java
@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {
    @EntityGraph(attributePaths = {"members", "members.user"})
    Optional<Room> findById(UUID id);
}
```

**Or FetchType.EAGER**:
```java
@Entity
public class Room {
    @OneToMany(fetch = FetchType.EAGER)  // Load all members upfront
    List<RoomMember> members;
}
```

**Scope**: SERVICE-ONLY (chat-service)  
**Risk**: LOW  
**Verification**: Check Hibernate query log for number of queries

---

### User Service: Search Queries

**Issue**: `findByNameContaining()` may not fetch related avatar data

**Fix**: Apply @EntityGraph to search methods

---

## Transaction Boundaries

### 🟡 MEDIUM: Unclear @Transactional Scope

**Issue**: Not all data mutations are wrapped in transactions

**Example**:
```java
// ❓ Is this transactional?
public void sendMessage(ChatMessage msg) {
    chatRepository.save(msg);
    kafkaTemplate.send(...);  // Publishing outside transaction?
}

// ✅ Clear
@Transactional
public void sendMessage(ChatMessage msg) {
    chatRepository.save(msg);
    // Kafka sends AFTER transaction
    TransactionSynchronizationManager.registerSynchronization(...);
}
```

**Verification**: All public service methods that mutate data should have `@Transactional`

**Scope**: SERVICE-ONLY  
**Risk**: LOW

---

## 🔴 HIGH: Message Sequence Race Condition

**Issue**: Two messages sent concurrently may have same seq value

**Broken Code**:
```java
@Transactional
public void sendMessage(ChatMessage msg) {
    long maxSeq = messageRepository.findMaxSeqByRoomId(msg.getRoomId());
    msg.setSeq(maxSeq + 1);  // ❌ Race condition!
    messageRepository.save(msg);
}
```

**Fix**: Use database sequence
```java
@Entity
public class ChatMessage {
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "chat_seq")
    @SequenceGenerator(name = "chat_seq", sequenceName = "chat_message_seq", allocationSize = 1)
    private Long seq;
}
```

**Or use Postgres sequence directly**:
```sql
CREATE SEQUENCE chat_message_seq;
ALTER TABLE chat_message ADD COLUMN seq BIGINT DEFAULT nextval('chat_message_seq');
```

**Scope**: SERVICE-ONLY (chat-service)  
**Risk**: MEDIUM (requires migration)  
**Severity**: HIGH

---

## Unique Constraints

### Friend Request Deduplication

**Issue**: Duplicate friend requests possible (no UNIQUE constraint)

**Expected**:
```sql
ALTER TABLE friend_request ADD CONSTRAINT unq_pending_request 
  UNIQUE(sender_id, receiver_id, status) 
  WHERE status = 'PENDING';
```

**Or in JPA**:
```java
@Entity
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = {"senderId", "receiverId", "status"})
})
public class FriendRequest { }
```

**Risk**: Duplicate requests stored → UI confusion

**Scope**: SERVICE-ONLY (friendship-service)  
**Risk**: LOW  

---

### Username/Email Uniqueness

**Status**: ✅ Should have UNIQUE constraints

**Verification**: Check auth-service User entity

---

## Concurrency & Locking

### Optimistic Locking

**Use Case**: User updates profile while another update in progress

**Implementation**:
```java
@Entity
public class User {
    @Version
    private Long version;  // Hibernate optimistic lock
}
```

**Behavior**: If two concurrent updates, second fails with `OptimisticLockingFailureException`

**Verification**: Check if services use @Version annotation where needed

---

## Cascade & Orphan Removal

### Room Deletion

**Issue**: If room is deleted, are messages deleted too?

**Expected**:
```java
@Entity
public class Room {
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    List<ChatMessage> messages;
}
```

**Verification**: Check cascade settings in Room entity

---

## Indexes

### Query Performance Indexes

**Required** (verify present):

**auth-service**:
- `idx_user_username` on User(username)
- `idx_user_email` on User(email)
- `idx_refresh_token_token` on RefreshToken(token)

**chat-service**:
- `idx_message_room_id_seq` on ChatMessage(room_id, seq) - for pagination
- `idx_room_member_room_user` on RoomMember(room_id, user_id)

**friendship-service**:
- `idx_friend_request_receiver` on FriendRequest(receiver_id, status)
- `idx_friend_request_sender` on FriendRequest(sender_id, status)

**Verification**: Check schema or migration scripts

---

## Soft Deletes

### Deleted Messages

**Question**: When message is deleted, is row removed or soft-deleted?

**Current Assumption**: Soft-delete (row remains, marked as deleted)

**Risk if Hard-Delete**: Reactions/replies to deleted message point to non-existent row

---

## Foreign Keys

### Referential Integrity

**Check**:
- All `user_id` foreign keys → User table
- All `room_id` foreign keys → Room table
- All `message_id` foreign keys → ChatMessage table

**Verification**: `CONSTRAINT fk_* FOREIGN KEY (...)` in schema

---

## SQL Injection & Prepared Statements

**Status**: ✅ Using Spring Data JPA (protected)

**Verification**: No raw SQL queries with string concatenation

---

## N+1 in WebSocket

### Scenario: Broadcast Message to Room

**Broken**:
```java
Room room = roomRepository.findById(roomId);  // 1 query
for (RoomMember member : room.getMembers()) {  // N queries
    messagingTemplate.convertAndSendToUser(
        member.getUserId(), "/queue/messages", msg);
}
```

**Fix**: Use @EntityGraph or fetch all members upfront

---

## Summary

| Issue | Severity | Scope | Action |
|-------|----------|-------|--------|
| N+1 queries (Room/Message) | HIGH | SERVICE-ONLY | Add @EntityGraph |
| Message seq race condition | HIGH | SERVICE-ONLY | Use DB sequence |
| Duplicate friend requests | MEDIUM | SERVICE-ONLY | Add UNIQUE constraint |
| Weak transaction boundaries | MEDIUM | SERVICE-ONLY | Apply @Transactional |
| Missing indexes | MEDIUM | SERVICE-ONLY | Add performance indexes |
| Missing cascade settings | LOW | SERVICE-ONLY | Verify cascade behavior |
| Concurrency testing | MEDIUM | SERVICE-ONLY | Add load/concurrency tests |

---

**Next**: Read 16-api-contract-review.md

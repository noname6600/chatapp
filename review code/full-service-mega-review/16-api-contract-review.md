# API Contract Review

**Focus**: REST endpoint design, DTOs, validation, status codes, consistency  

---

## Endpoint Naming Convention

**Pattern**: `{method} /api/{service}/{resource}` or `/api/{service}/{resource}/{id}/{action}`

**Examples**:
- `GET /api/users/{userId}/profile` - Read user profile
- `PUT /api/users/{userId}/profile` - Update profile
- `POST /api/chat/rooms` - Create room
- `GET /api/chat/rooms/{roomId}/messages?limit=50&offset=0` - List messages
- `DELETE /api/chat/messages/{messageId}` - Delete message

**Status**: ✅ Appears consistent

---

## HTTP Status Codes

| Status | Use Case | Service |
|--------|----------|---------|
| 200 | Successful operation | All |
| 201 | Resource created | POST /rooms, /messages |
| 204 | No content (delete) | DELETE operations |
| 400 | Bad request (validation) | All |
| 401 | Unauthorized | All |
| 403 | Forbidden (permission denied) | All |
| 404 | Not found | All |
| 409 | Conflict (duplicate) | POST /register, /friends/request |
| 500 | Server error | Error handling |

**Verification**: Check each service returns appropriate status codes

---

## Request/Response DTO Consistency

### Auth Service

**Register Request**:
```json
{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "secure123"
}
```

**Register Response** (201 Created):
```json
{
  "userId": "uuid",
  "username": "john_doe",
  "email": "john@example.com"
}
```

**Issue**: Response contains email (OK if public, risky if not)

---

### Chat Service

**Send Message Request**:
```json
{
  "text": "Hello",
  "attachments": []
}
```

**Send Message Response** (201 Created):
```json
{
  "messageId": "uuid",
  "roomId": "uuid",
  "senderId": "uuid",
  "text": "Hello",
  "seq": 42,
  "timestamp": "2026-05-14T10:00:00Z",
  "attachments": []
}
```

**Verification**: Response includes seq (for pagination/ordering)

---

## Pagination

### Current Pattern (assumed)

**List Messages**:
```
GET /api/chat/rooms/{roomId}/messages?limit=50&before-seq=500
```

**Response**:
```json
{
  "messages": [...],
  "nextSeq": 400,
  "hasMore": true
}
```

**Issue**: Inconsistent pagination across services?

**Recommendation**: Standardize on:
- `limit` (default 50)
- `before` (cursor for pagination)
- `after` (cursor for pagination)
- `hasMore` boolean

---

## Sorting

**Assumption**: Messages sorted by seq DESC (newest first)

**Verification**: Confirm sorting is consistent

---

## Idempotency

### Idempotent Operations
- GET (always safe)
- PUT (replace, idempotent)
- DELETE (idempotent if it's "delete if exists")

### Non-Idempotent
- POST (creates new; multiple POSTs create duplicates)

**Risk**: User retries POST /messages twice → duplicate message

**Fix**: Use idempotency key header
```java
@PostMapping("/messages")
public ResponseEntity<ChatMessage> sendMessage(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody SendMessageRequest req) {
    
    if (idempotencyKey != null) {
        // Check if already processed
        ChatMessage existing = messageRepository.findByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            return ResponseEntity.ok(existing);  // Replay response
        }
    }
    
    ChatMessage msg = messageService.send(req);
    if (idempotencyKey != null) {
        msg.setIdempotencyKey(idempotencyKey);
    }
    return ResponseEntity.status(201).body(msg);
}
```

**Scope**: SERVICE-ONLY (each POST endpoint)  
**Risk**: MEDIUM  
**Severity**: HIGH (prevents duplicate messages)

---

## Validation

### Input Validation

**Required** (verify present):
- Email format in register
- Password strength
- Room name length
- Message text length
- File size (upload)

**Implementation**: Spring Validation annotations
```java
public class SendMessageRequest {
    @NotBlank(message = "Message text required")
    @Size(min = 1, max = 5000)
    private String text;
}
```

**Verification**: All DTOs have appropriate `@NotNull`, `@Size`, `@Email`, etc.

---

## API Versioning

**Current Status**: No versioning apparent (all `/api/...`)

**Risk**: Breaking changes force all clients to update

**Recommendation** (DO-NOT-FIX-NOW): 
- If breaking changes needed, use `/api/v2/...` with migration period
- Currently not required

---

## Error Responses

### Consistent Error Format

**Expected**:
```json
{
  "status": 400,
  "error": "Validation failed",
  "message": "Message text required",
  "timestamp": "2026-05-14T10:00:00Z"
}
```

**Or with field errors**:
```json
{
  "status": 400,
  "errors": {
    "text": ["Message text required"],
    "roomId": ["Invalid room"]
  }
}
```

**Verification**: Check all services return consistent error format

**Issue if Inconsistent**: Clients can't parse errors reliably

---

## Response Envelope

### Option 1: Direct JSON
```json
{
  "messageId": "uuid",
  "text": "Hello"
}
```

**Pros**: Simple, RESTful  
**Cons**: No metadata

### Option 2: Wrapped with Metadata
```json
{
  "data": {
    "messageId": "uuid",
    "text": "Hello"
  },
  "meta": {
    "correlationId": "...",
    "timestamp": "..."
  }
}
```

**Pros**: Space for metadata  
**Cons**: Adds nesting

**Current**: Likely Option 1 (direct JSON)

---

## ClientMessageId (Optimistic UI)

### Problem: Slow Network, Optimistic Message

1. Client sends message (locally shows with `clientMessageId = "temp-1"`)
2. Network is slow (2 seconds)
3. User sees "pending" message
4. Response returns with real `messageId = "uuid"`
5. Client must update temp ID → real ID

**Implementation**:
```java
public class SendMessageRequest {
    private String clientMessageId;  // Temp ID from client
    private String text;
}

public class ChatMessageResponse {
    private String clientMessageId;  // Echo back to client
    private String messageId;  // Real ID from server
}
```

**Verification**: Check if supported

**Risk if Missing**: Optimistic UI breaks (client can't correlate response)

---

## Summary

| Issue | Severity | Scope | Action |
|-------|----------|-------|--------|
| Inconsistent pagination | MEDIUM | COMMON-OPTIONAL | Standardize pattern |
| No idempotency key support | HIGH | SERVICE-ONLY | Add to POST endpoints |
| Missing input validation | MEDIUM | SERVICE-ONLY | Add @Size/@NotNull annotations |
| Inconsistent error format | MEDIUM | COMMON-OPTIONAL | Centralize error handler |
| No clientMessageId support | MEDIUM | SERVICE-ONLY | Add for optimistic UI |
| Missing versioning plan | LOW | DO-NOT-FIX-NOW | Document for future |

---

**Next**: Read 17-error-handling-observability-review.md

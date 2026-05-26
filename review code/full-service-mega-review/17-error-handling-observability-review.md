# Error Handling & Observability Review

**Focus**: Logging, tracing, metrics, observability  

---

## Structured Logging

### Current Status: Unknown (need verification)

**Expected**:
```
{
  "timestamp": "2026-05-14T10:00:00.000Z",
  "level": "INFO",
  "logger": "com.example.chat.service.ChatMessageService",
  "correlationId": "uuid",
  "userId": "user-123",
  "message": "Message sent to room",
  "messageId": "msg-uuid",
  "roomId": "room-uuid",
  "durationMs": 45
}
```

**Current Likely**:
```
2026-05-14 10:00:00 INFO [chat-service] Message sent to room
```

**Recommendation**: Add structured logging (JSON format)
- Use Logback with JSON encoder
- Or use Spring Cloud Sleuth for log context

**Scope**: COMMON-OPTIONAL  
**Risk**: LOW

---

## Correlation ID Propagation

### 🟡 MEDIUM: Missing Correlation ID Tracking

**Problem**: Can't trace request through all services

**Current State**: Unclear

**Fix**: Add correlation ID to:
1. All log lines
2. HTTP headers (X-Correlation-ID)
3. Kafka messages (EventMetadata)
4. Redis operations

**Implementation**:
```java
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
            HttpServletRequest req, HttpServletResponse resp, FilterChain chain) {
        
        String correlationId = req.getHeader("X-Correlation-Id");
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        
        MDC.put("correlationId", correlationId);  // Logback MDC
        resp.setHeader("X-Correlation-Id", correlationId);
        
        try {
            chain.doFilter(req, resp);
        } finally {
            MDC.clear();
        }
    }
}
```

**Verification**: All logs contain correlationId

**Scope**: COMMON-OPTIONAL (if centralized) or SERVICE-ONLY  
**Risk**: LOW

---

## Error Logging

### Kafka Consumer Failure

**Current**: Unknown if errors are logged

**Expected**:
```
ERROR [notification-service] Kafka consumer error for event CHAT_MESSAGE_SENT
  eventId: uuid
  consumerGroup: notification-service
  topic: chat.messages
  partition: 0
  offset: 12345
  exception: NullPointerException
  stack: ...
```

**Recommendation**: Log full context before retry/DLT

**Scope**: SERVICE-ONLY  
**Risk**: LOW

---

### REST Endpoint Error

**Expected**:
```
WARN [chat-service] Message send failed
  userId: user-123
  roomId: room-uuid
  status: 500
  exception: Database timeout
```

**Implementation**: `@ExceptionHandler` with logging

---

## Trace ID (Distributed Tracing)

### Current Status: Unknown

**Expected Integration**: Spring Cloud Sleuth with Zipkin/Jaeger

**Benefit**: 
- Visualize request flow across all services
- Find performance bottlenecks
- Debug issues in production

**Implementation** (DO-NOT-FIX-NOW):
```yaml
spring:
  cloud:
    sleuth:
      traceId128: true
      sampler:
        probability: 0.1  # Sample 10% of requests
  zipkin:
    baseUrl: http://zipkin:9411
```

**Scope**: COMMON-OPTIONAL  
**Risk**: LOW (optional enhancement)

---

## Request Duration Logging

### Example
```
INFO [chat-service] Message send completed
  userId: user-123
  durationMs: 145
  status: 201
```

**Implementation**:
```java
@Component
public class RequestDurationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain) {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(req, resp);
        } finally {
            long duration = System.currentTimeMillis() - start;
            log.info("Request completed", kv("durationMs", duration));
        }
    }
}
```

**Scope**: COMMON-OPTIONAL  
**Risk**: LOW

---

## Metrics

### 🟡 MEDIUM: Missing Custom Metrics

**Expected** (using Micrometer):
```java
@Component
public class MessageMetrics {
    private final Counter messagesCreated;
    private final Timer messageDuration;
    
    public MessageMetrics(MeterRegistry registry) {
        this.messagesCreated = Counter.builder("messages.created")
            .description("Total messages created")
            .register(registry);
        this.messageDuration = Timer.builder("message.send.duration")
            .description("Message send duration")
            .register(registry);
    }
    
    public void recordMessageSent(long durationMs) {
        messagesCreated.increment();
        messageDuration.record(Duration.ofMillis(durationMs));
    }
}
```

**Verification**: Check if services expose custom metrics

**Scope**: SERVICE-ONLY or COMMON-OPTIONAL  
**Risk**: LOW (observability enhancement)

---

## Slow Query Logging

### Database

**Configuration** (Hibernate):
```yaml
logging:
  level:
    org.hibernate.SQL_SLOW: WARN
spring:
  jpa:
    properties:
      hibernate:
        jdbc.batch_size: 20
        order_inserts: true
        order_updates: true
```

**Verification**: Check if slow queries (> 1 second) are logged

---

## Actuator Endpoints

### Health Checks

**Expected**:
```bash
GET /actuator/health
{
  "status": "UP",
  "components": {
    "db": {"status": "UP", "details": {"database": "PostgreSQL"}},
    "kafkaHealthIndicator": {"status": "UP"},
    "redis": {"status": "UP"}
  }
}
```

**Verification**: All services expose health with dependencies

---

## Graceful Degradation

### If Redis is Down
**Expected**: Service logs warning, falls back to direct queries

### If Kafka is Down
**Expected**: 
- Critical if service depends on Kafka startup
- Should queue events if not critical

### If Database is Down
**Expected**: Service fails immediately (unrecoverable)

---

## Audit Logging

### 🟡 LOW: Missing Audit Logs for Security Events

**Events to Audit**:
- User registration
- User login/logout
- Password change
- Permission changes
- Sensitive data access

**Example**:
```
AUDIT INFO User account created
  username: john_doe
  email: john@example.com
  timestamp: 2026-05-14T10:00:00Z
  ipAddress: 192.168.1.100
  userAgent: Mozilla/5.0...
```

**Scope**: DO-NOT-FIX-NOW (future enhancement)

---

## Summary

| Issue | Severity | Scope | Action |
|-------|----------|-------|--------|
| Missing correlation ID | MEDIUM | COMMON-OPTIONAL | Add filter + MDC logging |
| No structured logging | MEDIUM | COMMON-OPTIONAL | Add JSON encoder |
| Kafka error logging unclear | MEDIUM | SERVICE-ONLY | Log with context |
| Missing custom metrics | LOW | SERVICE-ONLY | Add Micrometer gauges |
| No distributed tracing | LOW | COMMON-OPTIONAL | Integrate Zipkin (future) |
| Missing audit logs | LOW | DO-NOT-FIX-NOW | Document for future |
| Slow query logging | LOW | SERVICE-ONLY | Enable in config |

---

**Next**: Read 18-test-coverage-review.md

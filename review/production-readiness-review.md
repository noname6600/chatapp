# Production Readiness Review — chatappBE
> Deep-dive audit | Date: 2026-05-20 | Reviewer: multi-agent analysis

---

## 1. Deployment Model

### Current State

The system is deployed as Docker Compose on a single host. `docker-compose.yml` is well-structured with health checks, restart policies (`restart: unless-stopped`), and ordered dependency chains.

**Single-host limitations:**
- No horizontal scaling for any service
- Host failure = complete system outage
- No rolling deployments — all services restart simultaneously on `docker-compose up`
- No blue-green or canary deployment support
- No pod disruption budgets

### Missing Production Infrastructure

| Component | Status | Required For |
|-----------|--------|-------------|
| Kubernetes manifests | ❌ Missing | Multi-instance, rolling deploys |
| Helm charts | ❌ Missing | Configurable deployments |
| Service mesh (Istio/Linkerd) | ❌ Missing | mTLS, observability, traffic management |
| Secrets management (Vault/KMS) | ❌ Missing | Secure credential storage |
| Container registry | ❌ Missing | Image versioning and rollback |
| CI/CD pipeline | ❌ Missing | Automated builds and deploys |

---

## 2. Container Security

### PROD-01 — CRITICAL | All 9 Containers Run as Root

**Affected:** All Dockerfiles — no `USER` directive present.

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY app.jar .
CMD ["java", "-jar", "app.jar"]
# Missing: RUN addgroup -S app && adduser -S app -G app
# Missing: USER app
```

A container escape vulnerability (e.g., in the JVM, a dependency, or a path traversal in the app) gives an attacker **root access to the host machine**. All 9 services have this issue.

**Fix (apply to all Dockerfiles):**
```dockerfile
RUN addgroup -S app && adduser -S app -G app
USER app
```

---

### PROD-02 — HIGH | No .dockerignore — Sensitive Files Included in Image

No `.dockerignore` files found. Docker build context includes:
- `application-local.yaml` (contains live credentials)
- `*.gradle` build files
- `.git/` directory
- Test resources

Even if the COPY instruction only copies the JAR, some Dockerfile patterns copy the entire source — check each service.

**Fix:** Add `.dockerignore` to each service:
```
.git
*.md
src/test
build/
.gradle
**/*application-local*
**/*.env
```

---

### PROD-03 — HIGH | No JVM Memory Flags — Container Memory Unsafe

```dockerfile
CMD ["java", "-jar", "app.jar"]
```

Without JVM flags, the JVM uses host memory for sizing. In a containerized environment with memory limits:
- JVM allocates 25% of **host** RAM as heap (e.g., 4 GB on 16 GB host)
- Container memory limit (e.g., 512 MB) is exceeded → OOM kill
- Service restarts immediately (restart policy) but hits OOM again → restart loop

**Fix:**
```dockerfile
CMD ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", \
     "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
```

---

## 3. Database Management

### PROD-04 — CRITICAL | auth-service Uses `ddl-auto: update` — Data Loss Risk

**Affected:** `auth-service/src/main/resources/application.yaml`

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
```

`ddl-auto: update` on a production database:
- Silently drops columns when entity fields are removed
- Does not handle column type changes (attempts and may fail with constraint errors)
- Does not run in a transaction — partial schema changes cannot be rolled back
- Prevents reproducible environments (schema is different between instances)

**Affected services:** auth-service, user-service (both use `update`).
**Only safe service:** chat-service (uses Flyway + `validate`).

**Fix:** Migrate all services to Flyway with `ddl-auto: validate`. Write initial V1 migrations (currently placeholder files).

---

### PROD-05 — CRITICAL | V1 Flyway Migrations Are Empty Placeholders

**Affected:**
- `friendship-service/src/main/resources/db/migration/V1__initial_schema.sql` — contains only comments
- `user-service/src/main/resources/db/migration/V1__initial_schema.sql` — contains only comments

Fresh deployment to any new environment will fail: Flyway runs the empty V1 migration (success), Hibernate validates entities against empty tables (failure → startup exception).

**Impact:** Blocks CI, staging, and any new developer environment setup.

---

### PROD-06 — HIGH | No Database Backup Strategy

PostgreSQL runs on a Docker volume (`postgres-data`) with no automated backup. Docker volumes persist across container restarts but:
- Host disk failure = total data loss
- No point-in-time recovery (PITR) configured
- No off-host backup (S3/GCS)
- No backup testing

**Minimum for production:**
- Daily `pg_dump` with 7-day retention to object storage
- Weekly full backup test restore
- WAL archiving for PITR

---

## 4. Observability

### PROD-07 — HIGH | DEBUG Logging Enabled in Production Configuration

**Affected:** `realtime-edge-service/src/main/resources/application.yaml`

```yaml
logging:
  level:
    com.chatweb.realtime: DEBUG      # Extremely verbose
    org.springframework.web.socket: DEBUG  # Every WS frame logged
```

With 10K concurrent WebSocket connections and 100 messages/second:
- Spring WebSocket at DEBUG: ~1M log lines/minute
- realtime service at DEBUG: ~500K log lines/minute
- Log volume: potentially gigabytes per hour
- Disk fills → service crashes → data loss

**Fix:** Set all loggers to `INFO` in production. Create a separate `application-dev.yaml` profile for DEBUG logging.

---

### PROD-08 — HIGH | No Centralized Log Aggregation

Each service writes to stdout/Docker logs. In a multi-service, multi-instance deployment:
- Cross-service correlation requires SSH-ing into each container
- Logs are lost when containers restart
- No search across services
- No alerts based on log patterns (ERROR rate, exception count)

**Fix:** Add ELK stack (Elasticsearch + Logstash + Kibana) or Grafana Loki + Promtail. Configure structured JSON logging in all services.

---

### PROD-09 — HIGH | No Structured JSON Logging

No `logback.xml` or `logback-spring.xml` in any service. Logs use Spring Boot's default pattern format:
```
2026-05-20 10:15:22.123  INFO 1 --- [main] c.c.auth.AuthServiceApplication : Started
```

For log aggregation systems (ELK, Loki), structured JSON is required for field extraction:
```json
{"timestamp":"2026-05-20T10:15:22.123Z","level":"INFO","service":"auth-service","traceId":"abc123","message":"User registered","userId":"uuid"}
```

**Fix:** Add `logback-spring.xml` to each service with JSON appender (Logstash encoder or Logback JSON encoder).

---

### PROD-10 — HIGH | Prometheus Metrics Missing on 7 of 9 Services

Only realtime-edge-service and gateway-service export Prometheus metrics. The remaining 7 services expose only `health` and `info` actuator endpoints.

**Missing metrics from:**
- auth-service: login success/failure rates, token issuance latency
- chat-service: message throughput, pipeline step latencies
- notification-service: notification delivery rate, consumer lag
- friendship-service: friend request rate
- presence-service: online user count
- user-service: profile read latency

**Fix:** Add to all service `application.yaml`:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus,metrics
  metrics:
    export:
      prometheus:
        enabled: true
```

---

### PROD-11 — MEDIUM | No Distributed Tracing

The `TraceIdFilter` in `common-web` generates a `traceId` per request. But:
- Trace context is not propagated to Kafka messages (no headers)
- Trace context is not propagated via Feign HTTP calls (no OpenTelemetry instrumentation)
- Redis pub/sub events have no trace context

A message from WebSocket → chat-service → Kafka → notification-service → Redis pub/sub → realtime-edge spans 5 services with no common trace ID.

**Fix:** Add Micrometer Tracing with OpenTelemetry exporter (Zipkin/Jaeger). Enable Kafka header propagation and Feign interceptor for trace propagation.

---

## 5. Resilience

### PROD-12 — HIGH | No Circuit Breakers on Service-to-Service HTTP Calls

**Affected:** chat-service → friendship-service (Feign), auth-service → user-service (Feign)

Resilience4j circuit breakers are configured only at the gateway. Service-to-service Feign calls have no circuit breaker:

```java
// FriendshipClient.java — no @CircuitBreaker annotation
FeignResponse checkBlockStatus(UUID senderId, UUID receiverId);
```

If friendship-service responds slowly, chat-service threads accumulate waiting for responses. With default Feign timeout (none configured), threads wait indefinitely. Thread pool exhaustion causes chat-service to become unresponsive.

**Fix:**
```java
@CircuitBreaker(name = "friendship-service", fallbackMethod = "checkBlockStatusFallback")
FeignResponse checkBlockStatus(UUID senderId, UUID receiverId);
```

---

### PROD-13 — HIGH | Feign Clients Have No Timeout

**Affected:** All Feign clients across all services

Without explicit timeout configuration:
```yaml
feign:
  client:
    config:
      default:
        connectTimeout: 2000    # 2 seconds
        readTimeout: 3000       # 3 seconds
```

The Feign client uses the JVM default socket timeout (effectively infinite on some JVM implementations). A single slow downstream service can hang all threads in the calling service.

**Fix:** Configure explicit connect and read timeouts. For message-send critical path, use aggressive timeouts (connect: 500ms, read: 1000ms).

---

### PROD-14 — MEDIUM | Gateway Timeout Is 10 Seconds — Too Long for Chat

```yaml
resilience4j:
  timelimiter:
    instances:
      default:
        timeoutDuration: 10s
```

A 10-second gateway timeout means users wait up to 10 seconds before receiving an error response for a failed message send. For a realtime chat system, 10 seconds is an eternity.

**Fix:** Set message-send route timeout to 3 seconds. Auth routes can tolerate longer (email sending may take 2-3s).

---

## 6. Graceful Shutdown

### PROD-15 — HIGH | No Graceful Shutdown Configuration

**Affected:** All services

None of the services configure:
```yaml
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
server:
  shutdown: graceful
```

Without graceful shutdown:
- In-flight HTTP requests are dropped when the container receives SIGTERM
- Kafka consumers stop immediately without committing current message offsets → reprocessing on restart
- WebSocket connections receive `CloseStatus.SERVICE_RESTARTED` without draining in-flight messages
- Chat-service pipeline steps running in async threads are abandoned mid-execution → partial state

**Fix:** Add graceful shutdown configuration to all services. Kafka consumers need `spring.kafka.listener.shutdown-timeout: 30s`.

---

## 7. Rate Limiting and Auth Hardening

### PROD-16 — HIGH | Auth Endpoints Have Same Rate Limit as API

```yaml
gateway:
  ratelimit:
    auth-replenish-rate: 5      # 5 req/sec per IP for auth endpoints
```

5 requests/second = 300 login attempts/minute per IP. For a distributed attack using multiple IPs, the effective rate is much higher. No per-account lockout exists (SEC-11).

**Fix:**
- Add per-IP rate limit: 5 login attempts per 15 minutes
- Add per-account rate limit: 10 failed attempts → 15-minute lockout in Redis
- Add CAPTCHA after 3 failures

---

## 8. Kafka Production Configuration

### PROD-17 — CRITICAL | Kafka Single Broker with RF=1

**Affected:** `docker-compose.yml`

```yaml
kafka:
  environment:
    KAFKA_DEFAULT_REPLICATION_FACTOR: 1
    KAFKA_NUM_PARTITIONS: 1
```

Single broker, RF=1: any Kafka restart causes complete message loss for all in-flight messages. In production:
- Use 3+ brokers
- RF=3, min.insync.replicas=2
- Disable auto topic creation (`KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"`)
- Pre-create topics with correct partition counts

---

### PROD-18 — HIGH | Kafka Consumer Using ZooKeeper (Deprecated)

Confluent Platform 7.6.0 uses ZooKeeper for Kafka coordination. ZooKeeper is deprecated in Kafka 3.x and removed in Kafka 4.x. The codebase ties to a deprecated coordination model.

**Fix:** Plan migration to Kafka KRaft mode (broker.id replaces ZooKeeper). Confluent Platform 7.6.0 supports KRaft in preview.

---

## 9. Production Readiness Checklist

| Requirement | Status | Notes |
|-------------|--------|-------|
| Credentials secured | ❌ | Cloudinary secret baked into JAR |
| Database migrations managed | ❌ | Only chat-service uses Flyway; V1 migrations empty |
| Horizontal scaling supported | ⚠️ | Redis registry mode must be configured |
| Health checks configured | ✅ | All services have Docker health checks |
| Circuit breakers at gateway | ✅ | Resilience4j configured |
| Circuit breakers service-to-service | ❌ | No Feign circuit breakers |
| Rate limiting | ⚠️ | Gateway only; no per-account lockout |
| Structured logging (JSON) | ❌ | No logback.xml, default text format |
| Centralized log aggregation | ❌ | Not configured |
| Distributed tracing | ❌ | Not configured |
| Prometheus metrics on all services | ❌ | Only 2 of 9 services |
| Alerting rules | ❌ | Not configured |
| Database backup | ❌ | No automated backups |
| Secrets management | ❌ | Environment variables only; secrets in JAR |
| Graceful shutdown | ❌ | Not configured |
| Test coverage | ❌ | No tests found in any service |
| Production log levels | ❌ | DEBUG enabled in realtime-edge |
| Kafka fault tolerance | ❌ | Single broker, RF=1 |
| Redis fault tolerance | ❌ | Single instance |
| PostgreSQL fault tolerance | ❌ | Single instance |
| JVM memory flags in Docker | ❌ | No -XX:MaxRAMPercentage |
| Non-root containers | ❌ | All 9 containers run as root |
| Feign timeouts configured | ❌ | Infinite timeout on service-to-service calls |
| Service-to-service mTLS | ❌ | Internal HTTP unencrypted |

**Checked: 2/24 = 8%**

**Overall Production Readiness: 3/10 — NOT production ready for internet-facing deployment.**

The system is architecturally sound and would be production-ready with 4-6 weeks of hardening. The primary blockers are: credential rotation, database migration management, observability stack, graceful shutdown, and Kafka/Redis high availability.

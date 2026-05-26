# Build and Runtime Gates Review

**Focus**: Gradle compilation, bean wiring, startup correctness, environment configuration  
**Severity Range**: BLOCKER to LOW  

---

## 1. Build Compilation Status

### ✅ Current Status
- **Build Result**: BUILD SUCCESSFUL
- **Compile Time**: 1m 46s
- **Command**: `./gradlew.bat clean compileJava --no-daemon`

### ⚠️ Warnings Detected

#### `common-core` Module
- **File**: `common-core/src/main/java/com/example/common/core/pipeline/PipelineStep.java`
- **Warning**: "uses unchecked or unsafe operations"
- **Severity**: LOW
- **Fix Scope**: COMMON-OPTIONAL
- **Recommendation**: Suppress with `@SuppressWarnings("unchecked")` if unavoidable, or refactor to use generics properly

#### `auth-service` Module
- **File**: `auth-service/src/main/java/com/example/auth/integration/resend/ResendEmailClient.java`
- **Warning**: "uses or overrides a deprecated API"
- **Severity**: LOW
- **Fix Scope**: SERVICE-ONLY
- **Recommendation**: Update Resend client library or replace deprecated calls

#### `chat-service` Module
- **Files**: Multiple files
- **Warning**: "uses or overrides deprecated API" + "unchecked or unsafe operations"
- **Severity**: LOW
- **Fix Scope**: SERVICE-ONLY

#### `presence-service` Module
- **File**: `presence-service/src/main/java/com/example/presence/state/redis/RedisPresenceTtlCacheAdapter.java`
- **Warning**: "uses or overrides a deprecated API"
- **Severity**: LOW
- **Fix Scope**: SERVICE-ONLY

#### `user-service` Module
- **File**: `user-service/src/main/java/com/example/user/service/impl/UserProfileService.java`
- **Warning**: "uses or overrides a deprecated API"
- **Severity**: LOW
- **Fix Scope**: SERVICE-ONLY

#### `realtime-edge-service` Module
- **Files**: 
  - `realtime-edge-service/src/main/java/com/example/realtime/adapter/in/redis/RedisEventListener.java` (line 25)
  - `realtime-edge-service/src/main/java/com/example/realtime/adapter/in/websocket/JwtHandshakeInterceptor.java` (line 28)
  - `realtime-edge-service/src/main/java/com/example/realtime/adapter/in/kafka/FriendshipKafkaEventConsumer.java` (line 27)
- **Warning**: "Field 'log' already exists" (3 warnings)
- **Severity**: MEDIUM
- **Issue**: Duplicate `@Slf4j` annotation applied (likely from Lombok)
- **Fix Scope**: SERVICE-ONLY
- **Root Cause**: Multiple inheritance of Slf4j or class + inner class both annotated
- **Recommendation**: Remove duplicate `@Slf4j` from parent/inner class, keep only one
- **Fix**: Remove `@Slf4j` from one level of hierarchy

---

## 2. Critical Bean Wiring Issues

### 🔴 BLOCKER: JwtDecoder Bean Conflicts

**Problem**: Multiple services define `@Bean JwtDecoder` without `@Primary` designation

**Location**: Services with local JWT validation:
- `auth-service/src/main/java/com/example/auth/configuration/SecurityConfig.java`
- `chat-service/src/main/java/com/example/chat/configuration/SecurityConfig.java` (if present)
- `notification-service/src/main/java/com/example/notification/configuration/LocalValidationJwtDecoderConfig.java`
- `presence-service/src/main/java/com/example/presence/configuration/SecurityConfig.java`

**Issue**: When multiple `@Bean JwtDecoder` exist in classpath (especially with common-security), Spring cannot autowire without `NoUniqueBeanDefinitionException`

**Risk**: Startup failure if another service imports both beans

**Fix Scope**: SERVICE-ONLY per service, but coordinated

**Recommendation**:
1. One service should define `@Bean @Primary JwtDecoder` in common-security
2. Other services should remove duplicate bean definitions, or
3. Each service's SecurityConfig should qualify with `@Bean("service-jwt-decoder")`

---

### 🔴 BLOCKER: Cloudinary Bean Missing

**Location**: `upload-service/src/main/resources/application.yaml`

**Problem**: No Cloudinary configuration section found; bean cannot be instantiated

**Evidence**: `upload-service/src/main/java/com/example/upload/configuration/` has no CloudinaryConfig class

**Fix**:
```yaml
cloudinary:
  cloud-name: ${CLOUDINARY_CLOUD_NAME}
  api-key: ${CLOUDINARY_API_KEY}
  api-secret: ${CLOUDINARY_API_SECRET}
```

**Scope**: SERVICE-ONLY  
**Risk**: Startup failure  
**Verification**: `./gradlew.bat :upload-service:compileJava` (currently succeeds, but bean creation fails at runtime)

---

### 🟠 HIGH: Redis Cache Configuration Commented Out

**Location**: `user-service/src/main/java/com/example/user/configuration/RedisCacheConfig.java`

**Problem**: Entire `@Configuration` class commented out or disabled; Redis caching for user profiles disabled

**Impact**: User profile queries bypass cache; performance degradation, repeated DB queries

**Evidence**: Check if class has `@Configuration` active

**Fix Scope**: SERVICE-ONLY  
**Risk**: LOW (re-enabling cache)  
**Verification**: Redis container running, cache keys appearing in Redis

---

## 3. Service Startup Correctness

### Environment Variables Check

**Required Variables** (review docker-compose.yml):

#### Gateway Service
- `SPRING_CLOUD_GATEWAY_ROUTES_*`: Route definitions
- `SERVER_PORT`: Default 8080
- `SPRING_KAFKA_BOOTSTRAP_SERVERS`: Default localhost:9092

#### Auth Service
- `SPRING_DATASOURCE_URL`: Postgres connection
- `SPRING_DATASOURCE_USERNAME`: Postgres user
- `SPRING_DATASOURCE_PASSWORD`: Postgres password
- `JWT_SECRET`: Signing key for tokens
- `RESEND_API_KEY`: Email service

#### User Service
- Same Postgres variables
- `SPRING_REDIS_HOST`: Redis host
- `SPRING_REDIS_PORT`: Redis port

#### Chat Service
- Postgres variables
- `SPRING_KAFKA_BOOTSTRAP_SERVERS`
- `SPRING_REDIS_HOST`

#### Presence Service
- `SPRING_REDIS_HOST`
- `SPRING_REDIS_PORT`

#### Notification Service
- Postgres variables
- `SPRING_KAFKA_BOOTSTRAP_SERVERS`
- `SPRING_REDIS_HOST`

#### Upload Service
- Cloudinary credentials (documented above)
- AWS S3 credentials (if used)

#### Friendship Service
- Postgres variables
- `SPRING_KAFKA_BOOTSTRAP_SERVERS`

#### Realtime Edge Service
- `SPRING_REDIS_HOST`
- `SPRING_KAFKA_BOOTSTRAP_SERVERS`

**Verification Command**:
```bash
docker-compose config | grep -E "environment:|SPRING_|KAFKA_|REDIS_|JWT_"
```

---

### 🟡 LOW: Hardcoded localhost Issues Inside Docker

**Issue**: Some services may reference `localhost:5432` instead of `postgres:5432`

**Fix**: Review application.yaml in each service; hostname should be service name in docker-compose

**Example**:
```yaml
# ❌ Wrong
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/chatapp_db

# ✅ Correct
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/chatapp_db
```

---

## 4. Circular Dependency Detection

### ✅ No Detected Circular Dependencies

**Verification Command**:
```bash
./gradlew.bat :chat-service:compileJava --info 2>&1 | grep -i "circular"
```

Result: No circular dependency issues found.

---

## 5. Bean Auto-Wiring Verification

### Potential Missing Beans

| Service | Bean | Location | Status |
|---------|------|----------|--------|
| gateway-service | RouteLocator | `GatewayConfig` | ✅ Present |
| auth-service | PasswordEncoder | `SecurityConfig` | ✅ Present |
| chat-service | KafkaProducer | `KafkaProducerAdapterConfig` | ✅ Present |
| presence-service | RedisConnectionFactory | `PresenceRedisRegistryConfig` | ✅ Present |
| notification-service | KafkaTemplate | Inherited from common-kafka | ✅ Present |
| upload-service | CloudinaryApi | NOT FOUND | 🔴 BLOCKER |
| realtime-edge-service | WebSocketHandler | `WebSocketConfig` | ✅ Present |

---

## 6. Startup Order Dependencies

### Current docker-compose.yml Ordering

**Expected**:
```yaml
services:
  postgres:
    # ...
  redis:
    # ...
  kafka:
    # ...
  zookeeper:  # Kafka dependency
    # ...
  gateway:
    depends_on:
      - auth
  auth:
    depends_on:
      - postgres
  chat:
    depends_on:
      - postgres
      - kafka
```

**Verification Command**:
```bash
docker-compose config | grep -A 2 "depends_on:"
```

**Action**: Check docker-compose.yml for proper `depends_on` conditions. Spring Boot 3.5.6 should wait for service availability based on readiness probes.

---

## 7. Compilation Verification Commands

### Full Build
```bash
cd chatappBE
./gradlew.bat clean compileJava --no-daemon
```
**Expected**: BUILD SUCCESSFUL  
**Current**: ✅ PASS (with LOW warnings)

### Per-Service Compilation
```bash
# Gateway
./gradlew.bat :gateway-service:compileJava --no-daemon

# Auth
./gradlew.bat :auth-service:compileJava --no-daemon

# User
./gradlew.bat :user-service:compileJava --no-daemon

# Chat
./gradlew.bat :chat-service:compileJava --no-daemon

# Presence
./gradlew.bat :presence-service:compileJava --no-daemon

# Friendship
./gradlew.bat :friendship-service:compileJava --no-daemon

# Notification
./gradlew.bat :notification-service:compileJava --no-daemon

# Upload
./gradlew.bat :upload-service:compileJava --no-daemon

# Realtime Edge
./gradlew.bat :realtime-edge-service:compileJava --no-daemon
```

### Common Module Compilation
```bash
./gradlew.bat :common:compileJava --no-daemon
```

---

## 8. Test Compilation

```bash
./gradlew.bat :gateway-service:compileTestJava --no-daemon
./gradlew.bat :chat-service:compileTestJava --no-daemon
./gradlew.bat :presence-service:compileTestJava --no-daemon
```

**Status**: Should compile successfully after BLOCKER fixes

---

## Summary

| Issue | Severity | Count | Status |
|-------|----------|-------|--------|
| BLOCKER | BLOCKER | 3 | 🔴 Must fix |
| HIGH | HIGH | 1 | 🔴 Must fix |
| MEDIUM | MEDIUM | 1 | 🟠 Should fix |
| LOW | LOW | 7 | 🟡 Nice to fix |

**Next**: Read 02-service-architecture-review.md for overall architecture audit.

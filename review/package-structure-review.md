# Package Structure Review — chatappBE
> Deep-dive audit | Date: 2026-05-20 | Reviewer: multi-agent analysis

---

## 1. Package Structure Across Services

| Service | Architecture Style | Layer Names | Consistency |
|---------|-------------------|------------|-------------|
| auth-service | Flat layered | `controller`, `service`, `repository`, `entity`, `dto`, `configuration` | Standard |
| user-service | Flat layered | `controller`, `service`, `repository`, `entity`, `dto`, `configuration` | Standard |
| chat-service | DDD modules | `modules/{domain}/application`, `modules/{domain}/domain`, `modules/{domain}/infrastructure` | Unique — most structured |
| friendship-service | Flat layered | `controller`, `service`, `repository`, `entity`, `dto`, `config` | `config` vs `configuration` inconsistency |
| gateway-service | Spring-specific | `config`, `filter`, `health` | Gateway-appropriate |
| notification-service | Flat layered | `controller`, `infrastructure`, `service`, `entity`, `configuration` | Mixes `infrastructure` with flat |
| presence-service | Partial hexagonal | `controller`, `configuration`, `state`, `event`, `redis` | Inconsistent naming |
| realtime-edge-service | Hexagonal (ports-and-adapters) | `adapter/in`, `adapter/out`, `application`, `config`, `connection`, `delivery`, `dispatch` | Best structure in codebase |
| upload-service | Flat layered | `controller`, `service`, `configuration` | Standard |

---

## 2. Package Structure Comparison

### auth-service (flat layered)
```
com.chatweb.auth
├── configuration/         # Spring config beans
├── controller/            # REST controllers
├── dto/                   # Request/response DTOs
├── entity/                # JPA entities
├── jwt/                   # JWT-related: KeyManager, JwksController
├── service/               # Business logic
│   ├── impl/             # Implementations
│   └── event/            # Event-driven afterCommit logic
└── repository/            # Spring Data repos
```

Issues:
- `jwt/` is a misplaced domain concern — mixes JWT business logic with controller (`JwksController`)
- `service/event/` is an unusual nesting — event handling should be in a separate `event/` or `messaging/` package
- No domain model layer — entities are JPA entities exposed directly to services

### realtime-edge-service (hexagonal — best in codebase)
```
com.chatweb.realtime
├── adapter/
│   ├── in/
│   │   ├── kafka/         # Kafka consumers (inbound adapters)
│   │   ├── redis/         # Redis pub/sub listeners (inbound adapters)
│   │   └── websocket/     # WebSocket handlers (inbound adapters)
│   └── out/               # Outbound adapters (HTTP clients, Redis publishers)
├── application/           # Domain services (ports)
│   └── port/
│       ├── in/            # Use case interfaces
│       └── out/           # Repository/publisher interfaces
├── config/                # Spring configuration
├── connection/            # Session management (partially misplaced — should be domain)
├── delivery/              # Message delivery (partially misplaced)
├── dispatch/              # Event dispatch (partially misplaced)
└── subscription/          # Channel subscription management
```

Issues:
- `connection/`, `delivery/`, `dispatch/`, `subscription/` belong in `application/` domain
- The hexagonal boundary is leaky — domain classes import Spring annotations

### chat-service (DDD modules — most sophisticated)
```
com.chatweb.chat
├── application/           # Cross-cutting application concerns
├── config/
├── infrastructure/
│   └── cache/             # MessageCacheService (dead code — no @Service)
└── modules/
    ├── message/
    │   ├── application/
    │   │   ├── command/   # Command objects
    │   │   ├── pipeline/  # Pipeline steps
    │   │   └── service/   # Application services
    │   ├── domain/
    │   │   └── model/     # Domain entities/aggregates
    │   └── infrastructure/
    │       └── persistence/ # JPA repositories
    ├── room/
    ├── reaction/
    └── attachment/
```

This is the only service with a proper domain model layer. The pipeline pattern is sophisticated. However the `CompletableFuture.runAsync()` bug undermines the value of the careful transaction management.

---

## 3. Structural Inconsistencies

### PKG-01 — HIGH | `configuration` vs `config` Package Name Inconsistency

| Service | Package name |
|---------|-------------|
| auth-service | `configuration/` |
| user-service | `configuration/` |
| notification-service | `configuration/` |
| friendship-service | `config/` |
| gateway-service | `config/` |
| realtime-edge-service | `config/` |
| presence-service | `configuration/` |
| chat-service | `config/` |

Half the services use `configuration`, half use `config`. A new developer reading the codebase encounters the inconsistency immediately and cannot infer the convention.

**Impact:** Minor cognitive overhead; significant when searching by package name (`find . -name "*.java" -path "*/configuration/*"` misses half the services).

**Fix:** Standardize on `config/` across all services (shorter, conventional in Spring community).

---

### PKG-02 — HIGH | Hexagonal Architecture Applied Only to realtime-edge-service

The hexagonal architecture in realtime-edge-service provides clear separation between:
- What the service does (domain/application)
- How it communicates (adapters)

But the pattern is not applied to any other service. This means:
- auth-service, user-service, friendship-service mix HTTP protocol concerns directly into service classes
- Testing is harder — you cannot test service logic without a web layer
- Feign clients are instantiated directly in service classes without port interfaces

**Recommendation:** At minimum, introduce a port interface layer in chat-service and auth-service (the two most complex services). The DDD module structure in chat-service is a good foundation.

---

### PKG-03 — MEDIUM | DDD Applied Only to chat-service

chat-service has proper domain modeling:
- `domain/model/` — pure domain objects
- `application/` — application services (use cases)
- `infrastructure/` — persistence, external integrations

Other services expose JPA `@Entity` classes directly to service methods. Changes to the JPA schema directly affect business logic — no domain model buffers the change.

**Risk:** For auth-service, the JPA entity `User.java` is used directly in service methods. Adding a new JPA relationship (e.g., `@ManyToMany roles`) requires changing service code.

---

## 4. Common Module Architecture

### PKG-04 — HIGH | SharedEventCatalog Couples All Services

**Affected file:** `common-events/src/main/java/com/chatweb/common/events/SharedEventCatalog.java`

All Kafka event DTOs are defined in one file in a shared module. Every service imports `common-events`.

**Consequences:**
1. Changing `ChatMessageSentEvent` forces recompile of all 9 services
2. Services that only produce events must still have all consumer DTOs on their classpath
3. Dead DTOs (MESSAGE_PINNED/UNPINNED events with no publishers) remain in the catalog forever
4. No versioning mechanism — `ChatMessageSentEventV2` cannot coexist with `ChatMessageSentEvent`

**Better approach:**
```
common-events-api/         # Shared interfaces only (no concrete classes)
chat-service-events/       # chat-service owned event DTOs
auth-service-events/       # auth-service owned event DTOs
```
Each consumer defines its own DTO matching the fields it cares about. Jackson's `@JsonIgnoreProperties(ignoreUnknown = true)` allows partial deserialization.

---

### PKG-05 — MEDIUM | common-security Module Is Too Thin

**Affected:** `common-security/`

The module appears to contain only the `InternalServiceAuthFilter`. This filter is then duplicated in user-service, friendship-service, and presence-service (each has their own copy) instead of using the shared module.

The module exists but is not used for its primary purpose.

**Fix:** Ensure `InternalServiceAuthFilter` in common-security is the canonical implementation imported by all services. Remove duplicates.

---

### PKG-06 — MEDIUM | common-redis Contains Dead Abstractions

**Affected:** `common-redis/`

`RedisEventRegistry`, `DefaultRedisEventRegistry`, and `RedisEventDispatcher` appear to be abstractions created for a use case that was later abandoned. The realtime-edge service implements its own Redis event handling without using these common abstractions.

Dead shared module code has maintenance costs: it appears in IDE suggestions, confuses new developers, and must be kept compatible during Spring Boot upgrades.

**Fix:** Remove unused abstractions from common-redis. If they are intended for future use, add a `@Deprecated` annotation and tracking issue.

---

## 5. Root Project Naming

### PKG-07 — LOW | Root Project Named `demo`

**Affected:** root `settings.gradle`

```groovy
rootProject.name = 'demo'
```

The root project name appears in Spring Boot application name generation, Gradle build outputs, and Docker image names. Using `demo` is a leftover from project initialization.

**Fix:** `rootProject.name = 'chatapp'` or `chatapp-be`.

---

## 6. Recommended Target Package Structure

For all services, adopt a consistent structure:

```
com.chatweb.{service}
├── config/                  # Spring configuration beans
├── api/                     # REST controllers + DTOs (inbound HTTP adapter)
│   ├── dto/
│   └── controller/
├── domain/                  # Pure domain model (no Spring/JPA imports)
│   ├── model/
│   └── service/             # Domain service interfaces
├── application/             # Application services (use cases)
│   └── service/             # Implementations, orchestrate domain + infrastructure
├── infrastructure/          # Technical implementations
│   ├── persistence/         # JPA entities, repositories
│   ├── messaging/           # Kafka producers, consumers
│   ├── cache/               # Redis integration
│   └── client/              # Feign clients, external HTTP
└── security/                # Service-specific security config (if needed)
```

This structure:
- Is consistent across all services
- Separates domain from infrastructure
- Makes the layers independently testable
- Matches what chat-service is already doing

---

## 7. Summary

| ID | Severity | Issue |
|----|----------|-------|
| PKG-01 | HIGH | `configuration` vs `config` package name inconsistency across services |
| PKG-02 | HIGH | Hexagonal architecture only in realtime-edge; no consistent pattern |
| PKG-04 | HIGH | SharedEventCatalog couples all services to a monolithic event module |
| PKG-03 | MEDIUM | DDD domain model only in chat-service; other services use JPA entities directly |
| PKG-05 | MEDIUM | common-security exists but InternalServiceAuthFilter is duplicated 3x |
| PKG-06 | MEDIUM | common-redis contains dead abstractions not used by any service |
| PKG-07 | LOW | Root project named `demo` |

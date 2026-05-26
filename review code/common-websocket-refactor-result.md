# common-websocket Refactor Result

## Freeze Verdict
**FREEZE-READY.**
All 8 refactor phases complete. All tests pass. No obsolete code remains. No service changes made.

---

## 1. New Websocket/Realtime Standard Contracts Added

### identity/
| Class | Description |
|-------|-------------|
| `RealtimePrincipal` | Core interface: userId, principalName, authorities, attributes |
| `RealtimeIdentity` | Immutable record implementing `RealtimePrincipal` + `java.security.Principal` |

### session/
| Class | Description |
|-------|-------------|
| `RealtimeSession` | Canonical transport-independent session abstraction |
| `RealtimeSessionRegistry` | register, unregister, findBySessionId, findByUserId, listSessions |

### subscription/
| Class | Description |
|-------|-------------|
| `RealtimeDestinationType` | USER, SESSION, CHANNEL_GROUP, GLOBAL |
| `RealtimeDestination` | Typed destination record; factory methods user/session/channel/global |
| `RealtimeSubscription` | Single active subscription binding |
| `RealtimeSubscriptionRegistry` | subscribe, unsubscribe, listBySession, listByDestination, cleanupSession |

### frame/
| Class | Description |
|-------|-------------|
| `RealtimeEventFrame` | Outbound event frame wrapping `EventEnvelope<?>`. Field name: **payload** (no data ambiguity) |
| `RealtimeCommandFrame` | Control frame: SUBSCRIBE, UNSUBSCRIBE, PING, PONG, ACK |
| `RealtimeErrorFrame` | Error frame wrapping `RealtimeError` |
| `RealtimeCommandType` | Enum of command types |

### error/
| Class | Description |
|-------|-------------|
| `RealtimeError` | Structured error payload: code, message, correlationId, details |
| `RealtimeErrorCode` | AUTH_FAILED, AUTHORIZATION_DENIED, UNKNOWN_DESTINATION, INVALID_FRAME, SUBSCRIPTION_DUPLICATE, SUBSCRIPTION_NOT_FOUND, INTERNAL_ERROR |

### codec/
| Class | Description |
|-------|-------------|
| `RealtimeFrameCodec` | encode/decode abstraction |
| `JsonRealtimeFrameCodec` | Jackson-backed implementation |
| `RealtimeCodecException` | Thrown on encode/decode failure |

### auth/
| Class | Description |
|-------|-------------|
| `RealtimeIdentityResolver` | Resolves raw token → `RealtimePrincipal` |
| `RealtimeAuthorizationPolicy` | allowConnect, allowSubscribe extension points |
| `NoOpRealtimeAuthorizationPolicy` | Allow-all default |

### observer/
| Class | Description |
|-------|-------------|
| `RealtimeObserver` | Full lifecycle interface with default no-op methods |
| `NoOpRealtimeObserver` | Explicit no-op implementation |
| `LoggingRealtimeObserver` | SLF4J-backed DEBUG/WARN logging, no token material |
| `MicrometerRealtimeObserver` | Micrometer counter-based metrics |

### registry/ (in-memory implementations)
| Class | Description |
|-------|-------------|
| `InMemoryRealtimeSessionRegistry` | Thread-safe ConcurrentHashMap, multi-session per user |
| `InMemoryRealtimeSubscriptionRegistry` | Thread-safe, duplicate subscription protection |

### sender/
| Class | Description |
|-------|-------------|
| `RealtimeMessageSender` | Send frame to one session by sessionId |
| `RealtimeBroadcaster` | sendToUser, sendToSession, sendToDestination, sendToAll |
| `RealtimeSendResult` | success/failure result with sessionId and cause |

### config/
| Class | Description |
|-------|-------------|
| `WebSocketAutoConfiguration` | Conditional auto-config: observer, authz policy, codec, session/subscription registries |

Auto-config registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

---

## 2. Old Websocket Code Removed/Replaced

| Removed File | Reason |
|---|---|
| `broadcaster/AbstractWebSocketBroadcaster.java` | Replaced by `SpringRealtimeMessageSender` (adapter), `DefaultRealtimeBroadcaster` |
| `dto/WsOutgoingMessage.java` | Duplicate DTO, replaced by `RealtimeEventFrame` |
| `protocol/RealtimeWsEvent.java` | Duplicate DTO, replaced by `RealtimeEventFrame` |
| `handshake/AbstractJwtHandshakeInterceptor.java` | Replaced by `SpringHandshakeInterceptor` |
| `handshake/IJwtHandshakeInterceptor.java` | Replaced by `RealtimeIdentityResolver` (no I-prefix) |
| `handshake/JwtHandshakeHandler.java` | Replaced by `SpringJwtHandshakeHandler` |
| `handshake/JwtHandshakeInterceptor.java` | Replaced by `JwtRealtimeIdentityResolver` (adapter) |
| `handshake/WsPrincipal.java` | Replaced by `RealtimeIdentity` (richer, multi-field) |
| `session/IWebSocketSessionRegistry.java` | Replaced by `RealtimeSessionRegistry` |
| `session/IRoomSessionRegistry.java` | Replaced by `RealtimeSubscriptionRegistry` + `RealtimeDestination` |
| `session/IUserBroadcaster.java` | Replaced by `RealtimeBroadcaster.sendToUser` |
| `session/IRoomBroadcaster.java` | Replaced by `RealtimeBroadcaster.sendToDestination` |
| `session/IGlobalBroadcaster.java` | Replaced by `RealtimeBroadcaster.sendToAll` |

---

## 3. Spring-Specific Adapter Boundary

All Spring types are contained in `adapter.spring` package only:

| Adapter | Spring type used | Kept private to |
|---|---|---|
| `SpringHandshakeInterceptor` | `HandshakeInterceptor`, `ServerHttpRequest/Response` | adapter.spring |
| `SpringJwtHandshakeHandler` | `DefaultHandshakeHandler` | adapter.spring |
| `SpringRealtimeSession` | `WebSocketSession` | adapter.spring |
| `SpringRealtimeMessageSender` | `WebSocketSession` | adapter.spring |
| `JwtRealtimeIdentityResolver` | `JwtDecoder` | adapter.spring |
| `QueryParamTokenResolver` | `ServerHttpRequest`, `ServletServerHttpRequest` | adapter.spring |
| `DefaultRealtimeBroadcaster` | (no Spring types) | adapter.spring |
| `HandshakeTokenResolver` (interface) | `ServerHttpRequest` | adapter.spring |

Public core contracts — `RealtimeSession`, `RealtimeSessionRegistry`, `RealtimeMessageSender`, `RealtimeBroadcaster`, `RealtimeIdentityResolver`, `RealtimeAuthorizationPolicy`, `RealtimeObserver`, `RealtimeFrameCodec` — contain **no Spring WebSocketSession references**.

---

## 4. EventEnvelope/common-events Integration

- `RealtimeEventFrame` wraps `EventEnvelope<?>` directly as the canonical outbound semantic event container.
- Field name is **`payload`** — the `data` alias ambiguity from `WsOutgoingMessage`/`RealtimeWsEvent` is removed.
- `common-events` is declared as an `api` dependency (not `implementation`) in build.gradle because `EventEnvelope` appears in the public API of `RealtimeEventFrame`.
- `JsonRealtimeFrameCodec` encodes frames using Jackson with `JavaTimeModule` — same serializer chain as `common-redis` and `common-kafka`.

---

## 5. common-security Identity Integration

- `JwtRealtimeIdentityResolver` in `adapter.spring` uses `JwtHelper.extractUserId(Jwt)` from `common-security` — no duplicated `UUID.fromString(jwt.getSubject())` logic.
- `common-security` added as `implementation` dependency in build.gradle.
- `RealtimePrincipal` carries userId, principalName, authorities extracted from JWT scope/authorities claims.

---

## 6. Tests Added

### Test classes created under `src/test/java/com/example/common/websocket/`:

| Test class | Covers |
|---|---|
| `frame/RealtimeFrameContractTest` | A: Frame serialization, `payload` field name, eventType preservation, error correlationId, command types |
| `registry/InMemoryRealtimeSessionRegistryTest` | B: register, unregister, findBySessionId, findByUserId, multi-session, cleanup |
| `registry/InMemoryRealtimeSubscriptionRegistryTest` | B: subscribe, unsubscribe, duplicate prevention, list-by-session, list-by-destination, cleanupSession |
| `subscription/RealtimeDestinationTest` | C: destination factory methods, isValid for each type, blank/null identifier rejection |
| `auth/RealtimeAuthorizationPolicyTest` | D: NoOp allow-all, custom deny-anonymous policy |
| `observer/RealtimeObserverTest` | F: all observer callbacks — noOp and logging implementations do not throw |
| `adapter/spring/SpringHandshakeInterceptorTest` | E: missing token rejection, identity resolver failure rejection, successful accept + attribute storage, QueryParamTokenResolver behavior |
| `guard/WebSocketContractGuardTest` | G: no core contract exposes `WebSocketSession`, all obsolete classes absent |

---

## 7. Validation Command Results

```
.\gradlew.bat :common:common-websocket:compileJava
→ BUILD SUCCESSFUL in 11s

.\gradlew.bat :common:common-websocket:test
→ BUILD SUCCESSFUL in 53s

.\gradlew.bat :common:common-events:test :common:common-security:compileJava :common:common-web:compileJava :common:common-websocket:test
→ BUILD SUCCESSFUL in 7s (all UP-TO-DATE)
```

---

## 8. Remaining Blockers

None blocking freeze of `common-websocket`.

Low-priority deferred items (not blocking freeze):
- `common-web/realtime/policy` package is still in transitional location. The classes were not moved in this pass because they have no direct websocket dependency and moving them would require service impact analysis. When service adoption begins, align `RealtimeFlowId` naming with canonical event types.
- `RedisChannels` still owns channel naming constants. Destination-to-Redis-channel mapping adapters should be defined when service adoption starts; `RealtimeDestination` now owns the destination semantics side.
- Heartbeat/keepalive policy not defined (out of scope for this common-only pass).
- No auto-configuration for `SpringRealtimeMessageSender` or `DefaultRealtimeBroadcaster` — these require an active `WebSocketSession` map and are expected to be wired by services via `@Bean` overrides.

---

## 9. Freeze Verdict

**common-websocket: FREEZE-READY.**

All high and medium blockers from the review have been resolved:
- One canonical realtime message model (`RealtimeEventFrame` / `RealtimeCommandFrame` / `RealtimeErrorFrame`) — `WsOutgoingMessage` and `RealtimeWsEvent` deleted.
- `EventEnvelope<?>` integrated for semantic outbound events.
- `WebSocketSession` removed from all public contracts; contained in `adapter.spring` only.
- Full session, subscription, destination, sender, broadcaster, error, codec, auth, identity, lifecycle, and observability contracts defined.
- JWT auth redesigned: `HandshakeTokenResolver` → `RealtimeIdentityResolver` → `RealtimeAuthorizationPolicy` chain, no hardcoded query-param, no token logging.
- `common-security` `JwtHelper` reused; UUID subject parsing not duplicated.
- Richer `RealtimeIdentity` replaces UUID-only `WsPrincipal`.
- All `I*`-prefix interfaces removed; naming aligned with `common-kafka`/`common-redis` style.
- Auto-configuration is conditional (`@ConditionalOnClass`, `@ConditionalOnMissingBean`), not eager `@Component`.
- Full test suite added with 8 test classes covering contracts, registries, destinations, auth, adapter, observer, and guard assertions.

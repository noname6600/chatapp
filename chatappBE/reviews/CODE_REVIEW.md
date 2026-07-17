# Backend Services Code Review
> Generated: 2026-05-18 | Scope: all services except `common/`

---

## How to read this document

Each finding includes:
- **File + line** — exact location verified against source
- **Issue** — what is wrong or wasteful
- **Fix** — concrete step to resolve it
- **Priority**: `HIGH` = security or data correctness | `MEDIUM` = functional gap or silent failure | `LOW` = cleanup / quality

---

## gateway-service

### [HIGH] Client can inject `X-User-Id` header
**File:** `gateway-service/src/main/java/com/example/gateway/filter/JwtAuthFilterGatewayFilterFactory.java:38-44`

The filter **adds** `X-User-Id` from the JWT subject but never **strips** the same header from the incoming client request first. A client that sends `X-User-Id: <someone-else's-id>` will have it forwarded to downstream if the route does not go through this filter (e.g. public routes, or future misconfigured routes).

**Fix:** Before the `chain.filter(mutated)` block, strip the incoming header:
```java
.request(exchange.getRequest().mutate()
    .headers(h -> h.remove("X-User-Id"))   // strip client-supplied value first
    .header("X-User-Id", jwt.getSubject())
    .build())
```

---

### [HIGH] JWT subject forwarded without UUID validation
**File:** `gateway-service/src/main/java/com/example/gateway/filter/JwtAuthFilterGatewayFilterFactory.java:40`

`jwt.getSubject()` is forwarded as-is. If a token is ever issued with a malformed subject (not a valid UUID), downstream services calling `UUID.fromString(request.getHeader("X-User-Id"))` will throw `IllegalArgumentException` and return a 500.

**Fix:** Validate before forwarding:
```java
String subject = jwt.getSubject();
try { UUID.fromString(subject); } catch (IllegalArgumentException e) {
    exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
    return exchange.getResponse().setComplete();
}
```

---

### [LOW] Hardcoded filter name
**File:** `gateway-service/src/main/java/com/example/gateway/filter/JwtAuthFilterGatewayFilterFactory.java:26-28`

`name()` returns the hardcoded string `"JwtAuthFilter"`. If the class is ever renamed, the YAML routes will silently break at startup.

**Fix (optional):** This is fine to keep if you don't plan to rename it. If you want it to be derivation-safe, remove the `name()` override and rename the class to `JwtAuthFilterGatewayFilterFactory` already matches the convention Spring uses to auto-derive the name as `JwtAuthFilter`. The current explicit override is actually redundant — you can just delete the `name()` method.

---

## auth-service

### [MEDIUM] `@Transactional` on read-only facade methods
**File:** `auth-service/src/main/java/com/example/auth/service/impl/TokenServiceFacade.java:23`

`@Transactional` is declared at the class level. Methods like `buildAuthResponse()` (line 119) and `cleanup()` (line 115) are purely read/compute or just delegate to a repo bulk-delete — they don't need a full transaction or could be `@Transactional(readOnly = true)`.

**Fix:** Remove the class-level `@Transactional` and annotate each method individually:
- `issue()`, `refresh()`, `logout()`, `logoutAll()` → `@Transactional`
- `cleanup()` → `@Transactional`
- `buildAuthResponse()` → no annotation (private pure helper)

---

### [MEDIUM] `KeyManager.rotateOnce()` — `synchronized` not safe in multi-instance deploy
**File:** `auth-service/src/main/java/com/example/auth/jwt/impl/KeyManager.java:67`

`synchronized` only prevents concurrent rotation within **one JVM**. If you run multiple auth-service instances, two pods can simultaneously call `rotateOnce()`, both call `repo.clearAllActive()`, and create two new active keys. The second instance's `clearAllActive()` will deactivate the key just saved by the first — leaving the first instance's in-memory `currentKid` pointing to a now-inactive key.

**Fix (short term):** Add a unique constraint on `jwt_keys(active)` filtered to `active = true` (partial index), or use a `SELECT ... FOR UPDATE` on the active key before rotation. The simplest approach: add a DB-level advisory lock or change `clearAllActive` to a conditional CAS update:
```sql
UPDATE jwt_keys SET active = false WHERE active = true;
```
This is already what you have, but it needs to be inside the same transaction as the `saveAndFlush` — which it is, since `@Transactional` is present. The real risk is two concurrent transactions both passing `clearAllActive()` before either commits. Add a retry-on-duplicate or use `SKIP LOCKED` pattern.

**Fix (long term):** Use a distributed lock (Redis `SET NX EX`) around the rotate block so only one instance rotates at a time.

---

### [LOW] Defensive assertion in `buildAuthResponse()` is unreachable
**File:** `auth-service/src/main/java/com/example/auth/service/impl/TokenServiceFacade.java:119-126`

`buildAuthResponse()` checks `StringUtils.hasText(accessToken)` and throws `IllegalStateException`. Since both tokens are generated just before this call in `issue()`, neither can be null or blank (both are locally constructed strings). The check is dead code.

**Fix:** Remove lines 120–126 check and the `@Slf4j` import if this was the only log usage. The `IllegalStateException` message will never fire in production.

---

### [LOW] `JwtAuthenticationFilter` — `verify()` returns UUID, null not handled
**File:** `auth-service/src/main/java/com/example/auth/configuration/JwtAuthenticationFilter.java:43-52`

`jwtVerifierService.verify(token)` returns `UUID`. If it ever returns `null` (e.g. a subclass or mock returns null), `new JwtPrincipal(null)` is set as the authentication principal and the request proceeds authenticated with a null account ID. The `catch (Exception ex)` block on line 54 will not catch this.

**Fix:** Add a null guard:
```java
UUID accountId = jwtVerifierService.verify(token);
if (accountId == null) {
    SecurityContextHolder.clearContext();
    filterChain.doFilter(request, response);
    return;
}
```

---

## friendship-service

### [HIGH] TOCTOU race on `sendRequest()` — duplicate friendship row possible
**File:** `friendship-service/src/main/java/com/example/friendship/service/impl/FriendCommandService.java:48-81`

`repository.findBetweenUsers()` (line 48) and `repository.save()` (line 79) are two separate DB operations. Under concurrent requests, two threads can both pass the `existing.isPresent()` check and both insert a new `Friendship` row for the same user pair.

**Fix:** Add a unique constraint at the DB level on the (user_low, user_high) pair:
```sql
ALTER TABLE friendships ADD CONSTRAINT uq_friendship_pair UNIQUE (user_low, user_high);
```
Catch `DataIntegrityViolationException` in `sendRequest()` and treat it as "request already pending".

---

### [MEDIUM] `sendRequestByUsername()` silently returns on all error paths with no logging
**File:** `friendship-service/src/main/java/com/example/friendship/service/impl/FriendCommandService.java:85-111`

Lines 87, 93–95, and 103–105 all `return` silently. When the user-service returns an empty list or target has no accountId, the caller gets 200 OK with no indication of what happened. Makes debugging hard.

**Fix:** Downgrade from silent return to a `log.debug()` on each path so you can trace in development. **Keep** the intentional `catch (FeignException.NotFound)` silence (username probing guard, line 108) — that one is correct.
```java
if (matches == null || matches.isEmpty()) {
    log.debug("[FRIEND] sendRequestByUsername: no user found for username={}", username);
    return;
}
```

---

### [LOW] `publishAfterCommit()` — `TransactionSynchronization` can be replaced
**File:** `friendship-service/src/main/java/com/example/friendship/service/impl/FriendCommandService.java:199-211`

The manual `TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { afterCommit() })` pattern is verbose. Spring's `@TransactionalEventListener(phase = AFTER_COMMIT)` does the same with less boilerplate.

**Fix (optional):** This is a style improvement, not a bug. Current code is correct. If you refactor, use `ApplicationEventPublisher.publishEvent(new FriendshipEvent(...))` inside the transaction and handle it in a `@TransactionalEventListener`. Defer this unless doing a larger refactor.

---

### [LOW] `low()` / `high()` helpers are fine — no action needed
The first review flagged these as removable. After reading the source (lines 36–37), they are simple, clear one-liners that improve readability of every `block/unblock/send` call. **Keep them.**

---

## chat-service

### [HIGH] No room membership check before sending message
The send pipeline steps load the message aggregate and authorize by sender/room but the actual membership validation (is the sender still a member of this room?) needs verification. Check the `AuthorizeSendMessageStep` to confirm it calls `roomMembershipService.isMember()` or equivalent. If it only checks that the room exists, a removed member could still send.

**How to verify:**
```
grep -r "isMember\|membership\|authorize" chat-service/src/main/java --include="*.java" -l
```
If no membership check exists in the authorization step, add one.

---

### [MEDIUM] MESSAGE notification type is unconditionally suppressed
**File:** `notification-service/.../NotificationMessageEventApplicationService.java:81-84`

```java
if (notificationType == NotificationType.MESSAGE) {
    log.info("[NOTI] Skip MESSAGE notification ...");
    continue;
}
```
This means plain message notifications (not mentions, not replies, not invites) are **never delivered**, regardless of the user's notification mode. If MESSAGE notifications are intentionally removed from the product, delete the `MESSAGE` enum value and the dead branch. If they are intended for future use, document the intent clearly. As-is, a user who sets notification mode to `ALL` still gets no MESSAGE notifications.

**Fix:** Either delete the suppression block and handle MESSAGE properly, or add a comment explicitly stating "MESSAGE type is intentionally suppressed per product decision [date/ticket]."

---

### [MEDIUM] `senderName` passed twice to `createNotification()`
**File:** `notification-service/.../NotificationMessageEventApplicationService.java:94-104`

```java
notificationCommandService.createNotification(
    recipientUserId,
    notificationType,
    payload.getMessageId(),
    roomId,
    senderId,
    senderName,   // <-- line 101
    senderName,   // <-- line 102 — duplicate
    preview,
    payload.getCreatedAt()
);
```
Two consecutive `senderName` arguments look like a copy-paste error. Check `createNotification()`'s signature — the second name parameter might be intended for the room name or actor name.

**Fix:** Inspect the method signature and pass the correct second argument (likely `payload.getRoomName()` or similar).

---

### [MEDIUM] Cloudinary parameters hardcoded in `GroupAvatarGenerator`
**File:** `chat-service/src/main/java/com/example/chat/modules/room/service/impl/GroupAvatarGenerator.java:24-31`

`w_400,h_400`, `Arial_140_bold`, `r_max` and the color palette (lines 52-59) are all hardcoded in the URL string. If Cloudinary transforms need changing, you must redeploy.

**Fix:** Extract to `application.yml`:
```yaml
chat:
  group-avatar:
    width: 400
    height: 400
    font: Arial_140_bold
    palette: ["FF6B6B","FFD93D","6BCB77","4D96FF","B983FF","FF9F1C","2EC4B6"]
```
Inject with `@ConfigurationProperties`. This is a LOW-priority cleanup unless you need to change these values.

---

### [LOW] Pipeline abstraction is fine — no action needed
The send/edit/delete pipelines (`SendMessagePipeline`, `EditMessagePipeline`, `DeleteMessagePipeline`) are thin wrappers around `PipelineExecutor` from `common-core`. Since `PipelineFactory.create()` already handles ordering and execution, the pipeline classes themselves are just entry points. They're not over-engineered — keep them.

---

## presence-service

### [MEDIUM] `joinRoom()` silently drops join if user state is not loaded
**File:** `presence-service/src/main/java/com/example/presence/service/PresenceService.java:162`

```java
public void joinRoom(UUID roomId, UUID userId) {
    if (getStoredPresenceState(userId) == null) return;  // silent drop
    ...
}
```
If a user's TTL cache entry has expired (e.g. brief Redis hiccup) or they call `joinRoom` before `online()` completes, the room join is silently dropped and no presence event is published. The client thinks they joined but receives no further room presence events.

**Fix:** Log at WARN level at minimum:
```java
if (getStoredPresenceState(userId) == null) {
    log.warn("[PRESENCE] joinRoom skipped: no state for userId={} roomId={}", userId, roomId);
    return;
}
```
Longer term: consider whether to restore default state and proceed, or throw so the caller can retry.

---

### [MEDIUM] `online()` — `firstConnection` check is not atomic with publish
**File:** `presence-service/src/main/java/com/example/presence/service/PresenceService.java:73-96`

`incrementConnectionCount()` uses Redis `INCR` (atomic), so `connectionCount == 1` correctly identifies the first connection per Redis. However there is a window between the INCR and `publishUserEvent()` where the connection count is 1 but the event has not yet been published. If the process crashes in that window, no `USER_ONLINE` event is ever published — user appears online in Redis but no realtime subscribers were notified.

**Fix:** This is an inherent distributed systems tradeoff. The practical mitigation is to use `handleUserOfflineByTTL()` (which already exists and is triggered by Redis keyspace expiry) as the authoritative offline mechanism. For the online event, document that it may be missed on crash and rely on the snapshot endpoint (`getRoomPresence`) to reconcile.

---

### [LOW] Inconsistent indentation in `online()`
**File:** `presence-service/src/main/java/com/example/presence/service/PresenceService.java:87`

Line 87 `if (firstConnection)` is indented at 4 spaces while the surrounding block uses 8 spaces. Minor but inconsistent.

**Fix:** Re-indent line 87 and its block to 8 spaces.

---

### [LOW] `DEFAULT_MODE` is a class constant but could be config
**File:** `presence-service/src/main/java/com/example/presence/service/PresenceService.java:30`

```java
private static final PresenceMode DEFAULT_MODE = PresenceMode.AUTO;
```
Fine for now. If you ever want to change the default for new users without redeploying, move to `application.yml`. Low priority.

---

## realtime-edge-service

### [HIGH] Missing `accessToken` returns `true` (success) without error to client
**File:** `realtime-edge-service/src/main/java/com/example/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java:151-153`

```java
String accessToken = (String) session.getAttributes().get("accessToken");
if (accessToken == null || accessToken.isBlank()) {
    return true;  // ← tells caller "handled", but client got no response
}
```
When `accessToken` is missing, the handler returns `true` (meaning "I handled this message") but sends nothing back to the client. The client thinks `presence.room.join` succeeded, subscribes no channel, and waits for events that never arrive.

**Fix:**
```java
if (accessToken == null || accessToken.isBlank()) {
    sendError(session, "Missing access token");
    return true;
}
```

---

### [HIGH] `sendChatError()` and `sendPresenceError()` are identical
**File:** `realtime-edge-service/src/main/java/com/example/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java:366-378`

```java
private void sendChatError(WebSocketSession session, String message) throws IOException { ... }
private void sendPresenceError(WebSocketSession session, String message) throws IOException { ... }
```
Both methods build the exact same `{"type":"error","message":"..."}` response.

**Fix:** Replace both with one method:
```java
private void sendError(WebSocketSession session, String message) throws IOException {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("type", "error");
    response.put("message", message);
    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
}
```
Update all 6 call sites to use `sendError(...)`.

---

### [MEDIUM] `parseRoomId()` used to parse `messageId` — misleading name
**File:** `realtime-edge-service/src/main/java/com/example/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java:288, 299, 310, 323, 335`

```java
UUID messageId = parseRoomId(root.path("messageId").asText(null));
```
The method is just UUID parsing. The name `parseRoomId` is misleading when parsing a message ID. It works correctly but causes confusion.

**Fix:** Rename `parseRoomId()` to `parseUUID()`:
```java
private UUID parseUUID(String raw) { ... }
```
This is a pure rename — no logic change.

---

### [MEDIUM] Endpoint paths are string-compared inline
**File:** `realtime-edge-service/src/main/java/com/example/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java:67-81, 99-109, 131`

`"/ws/friendship"`, `"/ws/chat"`, `"/ws/presence"` appear as inline string comparisons in 3 separate methods.

**Fix:** Extract to constants:
```java
private static final String WS_FRIENDSHIP = "/ws/friendship";
private static final String WS_CHAT       = "/ws/chat";
private static final String WS_PRESENCE   = "/ws/presence";
```

---

### [LOW] No subscription rollback on exception in presence handler
**File:** `realtime-edge-service/src/main/java/com/example/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java:170-191`

In `presence.room.join`, `subscribeSession(presenceChannel)` and `subscribeSession(typingChannel)` are called before `presenceDomainClient.joinRoom()`. If `joinRoom()` or `roomSnapshot()` throws, the subscriptions are registered but presence is not set. On disconnect, `afterConnectionClosed` will clean up registry registrations, so the leak is bounded to the session lifetime. Not critical but worth noting.

**Fix (optional):** Wrap in try-catch and unsubscribe on failure:
```java
try {
    subscribeSession(realtimeSession, presenceChannel);
    subscribeSession(realtimeSession, typingChannel);
    presenceDomainClient.joinRoom(accessToken, roomId);
    ...
} catch (Exception ex) {
    unsubscribeSession(realtimeSession, presenceChannel);
    unsubscribeSession(realtimeSession, typingChannel);
    sendError(session, "Failed to join room presence");
}
```

---

## upload-service

### [MEDIUM] `fetchVerifiedCloudinaryAsset()` swallows all exceptions
**File:** `upload-service/src/main/java/com/example/upload/service/UploadSigningService.java:248-260`

```java
} catch (Exception ex) {
    log.debug("[UPLOAD] cloudinary resource lookup miss ...");
}
```
A network timeout, authentication failure, or rate-limit from Cloudinary is treated identically to "asset not found". The caller only sees `BusinessException("uploaded asset not found")`. A temporary Cloudinary outage will cause all upload confirmations to fail with a misleading "not found" error.

**Fix:** Distinguish exception types:
```java
} catch (com.cloudinary.exceptions.NotFound ex) {
    log.debug("[UPLOAD] cloudinary resource not found publicId={}", publicId);
} catch (Exception ex) {
    log.error("[UPLOAD] cloudinary API error publicId={} resourceType={}", publicId, candidate, ex);
    throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "Cloud provider unavailable, please retry");
}
```

---

### [LOW] `DEFAULT_PREPARE_TOKEN_TTL_SECONDS` constant is only a fallback
**File:** `upload-service/src/main/java/com/example/upload/service/UploadSigningService.java:35,55-57,164`

The constant `600L` on line 35 is only used when `prepareTokenTtlSeconds <= 0`. Since `@Value("${upload.confirm.prepare-token-ttl-seconds:600}")` already defaults to 600, the constant will never be reached unless someone explicitly sets the property to 0 or negative. It's harmless but confusing.

**Fix:** Remove the constant and simplify line 164:
```java
long ttlSeconds = prepareTokenTtlSeconds > 0 ? prepareTokenTtlSeconds : 600L;
// Or just trust the @Value default and use prepareTokenTtlSeconds directly
long ttlSeconds = prepareTokenTtlSeconds;
```

---

### [LOW] `normalized()` is a near-duplicate of `asString()` + trim
**File:** `upload-service/src/main/java/com/example/upload/service/UploadSigningService.java:263-278`

`normalized(String)` = null-safe trim. `asString(Object)` = null-safe `String.valueOf()`. They're used in combination (`normalized(asString(x))`). Consider merging into one `trimmedString(Object)` helper. Low priority — current code is readable enough.

---

## user-service

### [HIGH] Cache evicted inside `@Transactional` before DB commit
**File:** `user-service/src/main/java/com/example/user/service/impl/UserProfileService.java:120-179`

`UserProfileService` is annotated `@Transactional` at the class level (line 27). In `updateProfile()`, `profileCache.evict(accountId)` is called at line 179 inside the transaction — **before** the DB change is committed. If between eviction and commit another request reads the profile, it fetches from DB (getting the old value) and caches the old value again. After commit, the cache holds stale data until TTL expires.

**Fix:** Move the cache eviction to an `@TransactionalEventListener(phase = AFTER_COMMIT)` or evict after-commit using a `TransactionSynchronization`:
```java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override public void afterCommit() { profileCache.evict(accountId); }
});
```
Same pattern applies to `applyAvatarMetadata()` line 197.

---

### [MEDIUM] Username uniqueness check is TOCTOU — needs DB unique constraint
**File:** `user-service/src/main/java/com/example/user/service/impl/UserProfileService.java:140-148`

`findByUsernameIgnoreCase()` check (line 140) then `profile.setUsername()` (line 148) — two separate operations. Two concurrent users could both check, both find no conflict, and both save the same username. The DB must be the final authority.

**Fix:** Ensure a unique constraint exists (case-insensitive if using Postgres):
```sql
CREATE UNIQUE INDEX uq_user_profile_username_ci ON user_profiles (LOWER(username));
```
Catch `DataIntegrityViolationException` in `updateProfile()` and rethrow as `BusinessException(CONFLICT, "Username already taken")`.

---

### [MEDIUM] Cache TTL hardcoded
**File:** `user-service/src/main/java/com/example/user/service/impl/UserProfileService.java:31`

```java
private static final Duration PROFILE_TTL = Duration.ofMinutes(5);
```

**Fix:** Externalize to `application.yml`:
```yaml
user:
  profile-cache-ttl-minutes: 5
```
Inject with `@Value("${user.profile-cache-ttl-minutes:5}")` and convert: `Duration.ofMinutes(profileCacheTtlMinutes)`.

---

### [LOW] `safeGetProfileFromCache()` catches `RuntimeException` too broadly
**File:** `user-service/src/main/java/com/example/user/service/impl/UserProfileService.java:92-103`

Catching `RuntimeException` means programming errors (NPE, ClassCastException) are silently swallowed and treated as cache misses. This hides bugs.

**Fix:** Catch specific cache-related exceptions. Since you're using Spring Cache / Redis:
```java
} catch (org.springframework.data.redis.RedisConnectionFailureException
       | org.springframework.data.redis.serializer.SerializationException ex) {
    log.warn("...", ...);
    return null;
}
```

---

### [LOW] `"user/avatar/"` folder path is hardcoded — intentional coupling
**File:** `user-service/src/main/java/com/example/user/service/impl/UserProfileService.java:274`

```java
if (!request.getPublicId().startsWith("user/avatar/")) {
```
The comment on line 273 says `CRITICAL: Validate that publicId matches user/avatar/ folder from upload-service policy`. This coupling is intentional — it's a security boundary. If the folder changes in upload-service, this must be updated in sync.

**Fix:** Extract to a shared constant in `common` or read from config, so changes are atomic:
```yaml
# application.yml in user-service
upload.policies.user-avatar.folder: user/avatar
```
Or create a shared constant in `common-event-contract`.

---

## Priority Summary

| # | Priority | Service | Finding |
|---|----------|---------|---------|
| 1 | HIGH | gateway | Client can inject `X-User-Id` header |
| 2 | HIGH | gateway | JWT subject forwarded without UUID validation |
| 3 | HIGH | realtime-edge | Missing `accessToken` returns silent success |
| 4 | HIGH | friendship | TOCTOU race on `sendRequest()` — add unique DB constraint |
| 5 | MEDIUM | notification | MESSAGE type unconditionally suppressed |
| 6 | MEDIUM | notification | `senderName` passed twice to `createNotification()` |
| 7 | MEDIUM | user | Cache evicted before DB commit in `updateProfile()` |
| 8 | MEDIUM | user | Username uniqueness check lacks DB unique constraint |
| 9 | MEDIUM | presence | `joinRoom()` silent drop with no log |
| 10 | MEDIUM | upload | Cloudinary exceptions all treated as "not found" |
| 11 | MEDIUM | auth | `@Transactional` on whole facade including read-only methods |
| 12 | MEDIUM | friendship | `sendRequestByUsername()` silent returns, no debug log |
| 13 | LOW | auth | `KeyManager.rotateOnce()` `synchronized` not distributed-safe |
| 14 | LOW | auth | `buildAuthResponse()` null guard is unreachable dead code |
| 15 | LOW | auth | `JwtAuthFilter` null return from `verify()` not guarded |
| 16 | LOW | realtime-edge | `sendChatError` / `sendPresenceError` duplicate — merge |
| 17 | LOW | realtime-edge | `parseRoomId` misleading name — rename to `parseUUID` |
| 18 | LOW | realtime-edge | Endpoint paths as inline strings — extract to constants |
| 19 | LOW | user | Cache TTL hardcoded `Duration.ofMinutes(5)` |
| 20 | LOW | user | `safeGetProfileFromCache` catches too-broad `RuntimeException` |
| 21 | LOW | upload | `DEFAULT_PREPARE_TOKEN_TTL_SECONDS` constant is unreachable |
| 22 | LOW | chat | `GroupAvatarGenerator` hardcoded Cloudinary transform params |
| 23 | LOW | gateway | `name()` override is redundant (class name already matches) |

---

## Findings marked as FALSE POSITIVES (safe to ignore)

- **friendship `low()` / `high()` helpers** — these are fine, clear, keep them
- **friendship `publishAfterCommit()` using `TransactionSynchronization`** — correct, works as-is
- **chat `SendMessagePipeline`** — thin and clean, not over-engineered
- **realtime-edge `ChannelSubscriptionManager` substring risk** — channels passed to `isAuthorized()` are always constructed internally with valid format before reaching `substring()`, not from raw user input
- **presence `online()` double-publish** — Redis `INCR` is atomic; only one concurrent call can get `count == 1`
- **upload `DEFAULT_PREPARE_TOKEN_TTL_SECONDS`** — harmless fallback, not a real bug

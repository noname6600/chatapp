# Phase C — Presence Migration to realtime-edge-service

## Summary

Phase C completes the inbound HTTP command surface in `presence-service` and the lifecycle bridge in `realtime-edge-service` so that WebSocket clients connecting through the edge drive identical presence domain state as clients connecting directly.

---

## What Was Implemented

### presence-service: `PresenceEdgeCommandController`

**File:** `presence-service/src/main/java/com/example/presence/controller/PresenceEdgeCommandController.java`

New controller at `/api/v1/presence/ws/**` with 7 endpoints:

| Method | Path | Domain Action |
|--------|------|---------------|
| POST | `/ws/connect` | `presenceService.online(userId)` + `heartbeat(userId, true)` |
| POST | `/ws/disconnect` | `presenceService.offline(userId)` |
| POST | `/ws/heartbeat` | `presenceService.heartbeat(userId, active)` (body: `{active: bool}`) |
| POST | `/ws/rooms/{roomId}/join` | `presenceService.joinRoom(roomId, userId)` |
| POST | `/ws/rooms/{roomId}/leave` | `presenceService.leaveRoom(roomId, userId)` |
| POST | `/ws/rooms/{roomId}/typing` | `presenceRealtimePort.publishRoomEvent(ROOM_TYPING, ...)` |
| POST | `/ws/rooms/{roomId}/stop-typing` | `presenceRealtimePort.publishRoomEvent(ROOM_STOP_TYPING, ...)` |

**Auth:** All endpoints require JWT bearer (`anyRequest().authenticated()` in `SecurityConfig`). The edge forwards the original client JWT as the `Authorization` header. UserID is extracted via `JwtHelper.extractUserId(jwt)`.

**Key design choices:**
- Typing endpoints skip the `sessionRegistry.isUserInRoom()` check from the old WS handler — the edge already enforces room membership via channel subscription state, so the check is redundant here.
- Disconnect always calls `presenceService.offline()` — edge calls it only when the last presence session closes (ownership lives in `EdgePresenceLifecycleBridge`).

---

### realtime-edge-service: `EdgePresenceLifecycleBridge`

**File:** `realtime-edge-service/src/main/java/com/example/realtime/adapter/out/presence/EdgePresenceLifecycleBridge.java`

Dedicated lifecycle bridge separating presence connect/disconnect concerns from the generic WebSocket handler.

**`onPresenceConnected(realtimeSession, wsSession, accessToken)`:**
1. Calls `presenceDomainClient.connect(accessToken)` — signals online to presence-service
2. Fetches global snapshot via `presenceDomainClient.globalSnapshot(accessToken)`
3. Pushes `presence.global.snapshot` message to the WebSocket session

**`onPresenceDisconnected(realtimeSession, accessToken)`:**
1. Checks if any remaining edge sessions for this user are subscribed to `"presence:global"`
2. Only calls `presenceDomainClient.disconnect(accessToken)` if this was the last presence session
3. This mirrors the multi-session guard in `PresenceConnectionLifecycleAdapter`

**Integration point:** `RealtimeWebSocketHandler` now delegates to this bridge instead of directly calling `presenceDomainClient` and managing snapshot logic inline.

---

### realtime-edge-service: `PresenceDomainClient` — Error Hardening

**File:** `realtime-edge-service/src/main/java/com/example/realtime/adapter/out/presence/PresenceDomainClient.java`

All 9 methods (`connect`, `disconnect`, `heartbeat`, `joinRoom`, `leaveRoom`, `typing`, `stopTyping`, `globalSnapshot`, `roomSnapshot`) now wrap their HTTP calls in try/catch. Failures are logged at `WARN` level and swallowed — presence errors are non-fatal from the edge's perspective. A failed typing event or heartbeat must not close the WebSocket session.

---

## Inbound Command Flow

```
Client                    Edge (RealtimeWebSocketHandler)         Presence-Service
  |  --presence.room.join-->  |                                         |
  |                           |  --POST /ws/rooms/{roomId}/join +JWT-->  |
  |                           |                                         | joinRoom(roomId, userId)
  |                           |                                         | publishRoomEvent(ROOM_JOIN, ...)
  |                           |  <-- 200 OK --                          |
```

---

## Outbound Event Flow (Unchanged from Phase B)

```
Presence-Service                     Redis (realtime.presence.*)
  |  --EventEnvelope-->   |
                          |
                    Edge RedisListenerConfig
                    (pattern: realtime.presence.*)
                          |
                    RedisEventListener
                          |
                    PresenceRealtimeDeliveryService
                          |
                    WebSocket sessions subscribed to
                    "presence:global" or "presence:{roomId}"
```

This path was already working from Phase A/B. Phase C adds the inbound command path.

---

## Connection Lifecycle Model

```
/ws/presence connect:
  1. Edge registers RealtimeSession
  2. Edge subscribes session to "presence:global"
  3. EdgePresenceLifecycleBridge.onPresenceConnected()
     -> presenceDomainClient.connect(JWT)      -> POST /ws/connect
     -> presenceDomainClient.globalSnapshot()  -> GET /presence/global
     -> edge sends presence.global.snapshot to client

/ws/presence disconnect:
  1. Edge unregisters RealtimeSession
  2. EdgePresenceLifecycleBridge.onPresenceDisconnected()
     -> checks findByUserId(userId).anyMatch(subscribed to "presence:global")
     -> if last session: presenceDomainClient.disconnect(JWT) -> POST /ws/disconnect
```

---

## Dual-Path State

Both paths remain active. Clients can connect via:
- **Edge** (`/ws/presence` on realtime-edge-service) — uses `PresenceEdgeCommandController`
- **Direct** (`/ws/presence` on presence-service) — uses `PresenceWebSocketHandler` unchanged

This allows rollback by routing clients back to presence-service if issues arise.

---

## Pre-Existing Test Fixes (Bonus)

Two pre-existing broken tests were fixed as collateral:

1. **`PresenceRealtimeContractBaselineTest`**:
   - Removed import of `com.example.common.integration.realtime.RealtimeContractVersions` (class never created)
   - Fixed `"presence.room.stop_typing"` → `"presence.room.stop-typing"` (underscore vs hyphen mismatch)

2. **`ChatappApplicationTests.contextLoads()`**: Pre-existing Spring Boot full-context test that fails without running Redis/auth — not fixed, out of scope.

---

## Compile/Test Results

```
:presence-service:compileJava       → BUILD SUCCESSFUL
:realtime-edge-service:compileJava  → BUILD SUCCESSFUL
:presence-service domain tests      → BUILD SUCCESSFUL (service/contract/redis packages)
:realtime-edge-service:test         → BUILD SUCCESSFUL
```

---

## Remaining Gaps Before Staging Validation

1. **No routing rule change** — clients still need to be pointed at edge (`/ws/presence` on port 8086) vs presence-service directly
2. **No integration test** for `PresenceEdgeCommandController` — unit tests cover domain, but no mock-MVC test for the new endpoints
3. **Snapshot ordering race** — on `/ws/presence` connect, edge calls `POST /ws/connect` then `GET /presence/global` sequentially; if a status event fires between these, the snapshot could be stale (acceptable for MVP)
4. **No circuit breaker** on `PresenceDomainClient` — current error handling swallows all failures; high-volume typing events that time out will produce log noise
5. **Chat and Friendship** — not migrated (out of scope for Phase C)

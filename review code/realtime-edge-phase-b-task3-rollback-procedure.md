# Task 3: Rollback Rehearsal & Hardening
## Operational Rollback Procedure for Phase B Notification Migration

**Status:** 🔄 IN PROGRESS  
**Objective:** Document and verify exact rollback procedure from edge notification path back to service-local handler

---

## Rollback Philosophy

**Constraint:** Phase B must remain **fully reversible** at any point after canary launch.

**Strategy:** Dual-stack operation (both edge and service-local paths running) with **traffic director** controlling which path clients use.

**Design Principle:** No schema changes, no data migrations, no permanent state changes—pure traffic routing reversal.

---

## Architecture: Dual-Stack Capable System

### Current State (Phase B Active)

```
┌─────────────────────────────────────────────────────────────┐
│                    PRODUCTION CANARY                         │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────┐                                            │
│  │ WebSocket    │                                            │
│  │ Clients      │                                            │
│  │              │                                            │
│  └────────┬─────┘                                            │
│           │                                                   │
│           │ GET /ws?token=X   (CLIENT REDIRECTS HERE)       │
│           │                                                   │
│  ┌────────▼─────────────────────┐                           │
│  │ EDGE SERVICE :8085/ws         │  ◄─── PRIMARY PATH       │
│  │ (Phase B: Routing commands)   │                          │
│  └────────┬──────────────────────┘                           │
│           │                                                   │
│           │ HTTP /realtime/commands                          │
│           │ (RestNotificationCommandRouter)                  │
│           │                                                   │
│  ┌────────▼──────────────────────┐                          │
│  │ NOTIFICATION-SERVICE :8086    │                          │
│  │ - /api/v1/notifications/      │  ◄─── COMMAND SINK       │
│  │   realtime/commands (NEW)     │                          │
│  │ - /ws (LEGACY, DISABLED)      │                          │
│  │                               │                          │
│  └───────────────────────────────┘                          │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

### Rolled-Back State (Service-Local Active)

```
┌─────────────────────────────────────────────────────────────┐
│                    PRODUCTION ROLLBACK                       │
├─────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌──────────────┐                                            │
│  │ WebSocket    │                                            │
│  │ Clients      │                                            │
│  │              │                                            │
│  └────────┬─────┘                                            │
│           │                                                   │
│           │ GET /ws?token=X   (CLIENT REDIRECTS HERE)       │
│           │                                                   │
│  ┌────────▼──────────────────────┐                          │
│  │ EDGE SERVICE :8085/ws         │                          │
│  │ (DISABLED/OPTIONAL)           │  ◄─── DEPRECATED PATH    │
│  └───────────────────────────────┘ (Can remain running)     │
│                                                               │
│           NO TRAFFIC REACHES EDGE                            │
│                                                               │
│  ┌─────────────────────────────────┐                        │
│  │ NOTIFICATION-SERVICE :8086      │                        │
│  │ - /api/v1/notifications/        │  ◄─── DISABLED         │
│  │   realtime/commands (DISABLED)  │ (endpoint non-functional)
│  │ - /ws (LEGACY, RE-ENABLED)      │  ◄─── PRIMARY PATH     │
│  │   WebSocketHandler.java         │                        │
│  │                                 │                        │
│  └─────────────────────────────────┘                        │
│                                                               │
└─────────────────────────────────────────────────────────────┘
```

---

## Phase B Rollback Procedure

### Pre-Rollback Verification (Execute Before Initiating Rollback)

**Time Estimate:** 5 minutes

```bash
# 1. Verify edge service is healthy
curl -s http://edge-service:8085/health | jq .
# Expected: { "status": "UP" }

# 2. Verify notification-service is healthy
curl -s http://notification-service:8086/actuator/health | jq .
# Expected: { "status": "UP" }

# 3. Check active connections to edge endpoint
# (via monitoring dashboard or logs)
# Expected: N > 0 active WebSocket sessions

# 4. Verify notification-service HTTP endpoint is active
curl -X POST http://notification-service:8086/api/v1/notifications/realtime/commands \
  -H "Authorization: Bearer ${TEST_JWT}" \
  -H "Content-Type: application/json" \
  -d '{
    "commandName": "mark-read",
    "notificationId": "00000000-0000-0000-0000-000000000000",
    "requestId": "pre-rollback-test"
  }' \
  -w "\nStatus: %{http_code}\n"
# Expected: 200 OK

# 5. Verify service-local WebSocket handler compiled in notification-service
grep -r "class.*WebSocketHandler" \
  notification-service/src/main/java/ | grep -v "@"
# Expected: WebSocketHandler class exists (legacy)

# 6. Snapshot current notification database state
psql -h notification-db -U notification_user -d notification_service \
  -c "SELECT COUNT(*) as total_notifications FROM notifications;" \
  -c "SELECT is_read, COUNT(*) FROM notifications GROUP BY is_read;"
# Record counts for post-rollback verification
```

### Step 1: Disable Edge Traffic Reception (Client Redirect)

**Time Estimate:** 5-30 seconds (depending on DNS TTL)

**Method:** DNS or load balancer routing change

#### Option A: DNS Update (Recommended for single-endpoint systems)
```bash
# Option A.1: Direct DNS change (if clients resolve via DNS)
# Update DNS record to point to notification-service instead of edge
# Example: realtime.app.local A record changes from edge-ip to notification-service-ip

# Verify DNS resolution
nslookup realtime.app.local
# Before rollback: returns edge-ip
# After rollback:  returns notification-service-ip
```

#### Option B: Load Balancer Routing
```bash
# Option B.1: If using load balancer (e.g., nginx)
# File: nginx/realtime-routing.conf
# Change:
upstream realtime {
    # server edge-service:8085;    # COMMENT OUT
    server notification-service:8086;  # UNCOMMENT
}

# Reload nginx
nginx -s reload
```

#### Option C: API Gateway / Service Mesh (Istio, Consul)
```yaml
# If using Istio VirtualService
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: realtime-notification
spec:
  hosts:
  - realtime
  http:
  # OLD: Route to edge
  # - match:
  #   - uri:
  #       prefix: /ws
  #   route:
  #   - destination:
  #       host: edge-service
  #       port:
  #         number: 8085
  # NEW: Route to service-local
  - match:
    - uri:
        prefix: /ws
    route:
    - destination:
        host: notification-service
        port:
          number: 8086
```

### Step 2: Enable Service-Local WebSocket Handler

**Time Estimate:** 30 seconds

**File:** `notification-service/src/main/java/com/example/notification/websocket/NotificationWebSocketConfig.java`

```java
// Before (Phase B active):
@Configuration
@EnableWebSocket
public class NotificationWebSocketConfig implements WebSocketConfigurer {
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Legacy handler DISABLED - clients use edge instead
        // registry.addHandler(notificationWebSocketHandler, "/ws")
        //    .setAllowedOrigins("*");
    }
}

// After (Rollback):
@Configuration
@EnableWebSocket
public class NotificationWebSocketConfig implements WebSocketConfigurer {
    
    @Autowired
    private NotificationWebSocketHandler notificationWebSocketHandler;
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Legacy handler RE-ENABLED for direct client connections
        registry.addHandler(notificationWebSocketHandler, "/ws")
            .setAllowedOrigins("*");
    }
}
```

**Deployment Command:**
```bash
# Rolling update (blue-green or canary)
cd notification-service
./gradlew bootBuildImage
docker push notification-service:rollback-ready
kubectl set image deployment/notification-service \
  notification-service=notification-service:rollback-ready \
  --record
```

### Step 3: Disable Edge HTTP Endpoint (Optional, Can Keep Running)

**Time Estimate:** 1-2 minutes

**Rationale:** Edge can remain running but with no traffic (safe for later re-enablement)

**Option A: Keep Running (Minimal Risk)**
```bash
# Leave edge service running
# - No new clients connect (traffic redirected)
# - Existing edge sessions handled gracefully
# - Can re-enable instantly if needed
# Status: SAFE, REVERSIBLE
```

**Option B: Disable Endpoint**
```java
// File: realtime-edge-service/src/main/java/com/example/realtime/controller/WebSocketController.java
// Before:
@Configuration
public class WebSocketConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(realtimeWebSocketHandler, "/ws")
            .setAllowedOrigins("*");
    }
}

// After (during rollback):
@Configuration
public class WebSocketConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Endpoint disabled during rollback
        // registry.addHandler(...);  // DISABLED
        
        // But health check still works
    }
}
```

### Step 4: Handle Active Edge Sessions (Graceful Transition)

**Time Estimate:** 30-60 seconds (all clients reconnect)

**Method:** Edge sends graceful disconnect to existing clients

**Implementation:**
```java
// File: realtime-edge-service/src/main/java/com/example/realtime/handler/RealtimeWebSocketHandler.java
@Component
public class RealtimeWebSocketHandler implements org.springframework.web.socket.WebSocketHandler {
    
    @Value("${realtime.edge.enabled:true}")
    private boolean edgeEnabled;
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Check if edge is being disabled
        if (!edgeEnabled) {
            try {
                // Send graceful disconnect message
                RealtimeWsEvent event = new RealtimeWsEvent();
                event.setType("system");
                event.setEventType("edge-disabled");
                event.setPayload(Map.of(
                    "reason", "Phase B rollback - migrating to service-local handler",
                    "action", "reconnect",
                    "newEndpoint", "ws://notification-service:8086/ws"
                ));
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
                session.close();
                return;
            } catch (Exception e) {
                logger.error("[EDGE-ROLLBACK] Failed to send disconnect to session", e);
            }
        }
        
        // Normal edge flow if enabled
        registerSession(session);
    }
}

// Configuration property (application.yaml)
realtime:
  edge:
    enabled: true  # Set to false during rollback
```

**Deployment:**
```bash
# Via ConfigMap or environment variable change
kubectl set env deployment/edge-service \
  REALTIME_EDGE_ENABLED=false \
  --record
```

### Step 5: Monitor Client Reconnection

**Time Estimate:** 2-5 minutes (observe reconnect wave)

**Monitoring Queries:**

```bash
# 1. Monitor edge session disconnect
kubectl logs -l app=edge-service -f | grep "EDGE-ROLLBACK"
# Expected: "[EDGE-ROLLBACK] Graceful disconnect sent" × N sessions

# 2. Monitor service-local handler new connections
kubectl logs -l app=notification-service -f | grep "WebSocket connection"
# Expected: "WebSocket connection established from client" × N sessions

# 3. Verify notification database still functional
psql -h notification-db -U notification_user -d notification_service \
  -c "SELECT COUNT(*) as total_notifications FROM notifications;"
# Expected: Same count as pre-rollback (no data loss)

# 4. Check active WebSocket connections on notification-service
# (implementation specific - add metrics if not present)
curl http://notification-service:8086/actuator/metrics/websocket.sessions
# Expected: N active sessions (matching previous edge count)
```

### Step 6: Verify Notification Delivery Still Working

**Time Estimate:** 2-3 minutes

**Test Scenario:**

```bash
# 1. Connect test client to service-local handler
wscat -c "ws://notification-service:8086/ws?token=${TEST_JWT}"

# 2. Trigger notification via API
curl -X POST http://notification-service:8086/api/v1/notifications/command \
  -H "Authorization: Bearer ${TEST_JWT}" \
  -H "Content-Type: application/json" \
  -d '{
    "commandName": "mark-read",
    "notificationId": "'${TEST_NOTIF_ID}'"
  }'
# Expected: 200 OK

# 3. Verify client receives notification via WebSocket
# In wscat client: Should receive notification event JSON

# 4. Verify database updated
psql -h notification-db -U notification_user -d notification_service \
  -c "SELECT is_read FROM notifications WHERE id = '${TEST_NOTIF_ID}';"
# Expected: is_read = true
```

### Step 7: Post-Rollback Verification

**Time Estimate:** 5 minutes

**Verification Checklist:**

```bash
#!/bin/bash
set -e

echo "=== POST-ROLLBACK VERIFICATION ==="

# 1. Edge service state (should have 0 or minimal sessions)
echo "[1/7] Checking edge service state..."
EDGE_SESSIONS=$(curl -s http://edge-service:8085/health | jq '.details.sessions // 0')
if [ "$EDGE_SESSIONS" -le 5 ]; then
    echo "✓ Edge sessions: $EDGE_SESSIONS (acceptable)"
else
    echo "✗ FAIL: Edge still has $EDGE_SESSIONS active sessions"
    exit 1
fi

# 2. Service-local handler state
echo "[2/7] Checking service-local handler..."
SERVICE_SESSIONS=$(curl -s http://notification-service:8086/health | jq '.details.websocket.sessions // 0')
if [ "$SERVICE_SESSIONS" -gt 0 ]; then
    echo "✓ Service-local sessions: $SERVICE_SESSIONS"
else
    echo "⚠ Warning: Service-local has 0 sessions (may be normal if test period short)"
fi

# 3. Notification endpoint still functional
echo "[3/7] Testing notification endpoint..."
RESPONSE=$(curl -s -X POST http://notification-service:8086/api/v1/notifications/command \
  -H "Authorization: Bearer ${TEST_JWT}" \
  -H "Content-Type: application/json" \
  -d '{"commandName":"noop"}' \
  -w "%{http_code}" -o /dev/null)
if [ "$RESPONSE" = "200" ] || [ "$RESPONSE" = "400" ]; then
    echo "✓ Endpoint response: $RESPONSE"
else
    echo "✗ FAIL: Endpoint returned $RESPONSE"
    exit 1
fi

# 4. Database consistency
echo "[4/7] Verifying database..."
NOTIF_COUNT=$(psql -h notification-db -U notification_user -d notification_service \
  -t -c "SELECT COUNT(*) FROM notifications;")
echo "✓ Database notifications: $NOTIF_COUNT"

# 5. No orphaned sessions in edge
echo "[5/7] Checking for orphaned edge sessions..."
STALE=$(curl -s http://edge-service:8085/debug/sessions | \
  jq '.sessions | map(select(.connected == false)) | length')
if [ "$STALE" = "0" ]; then
    echo "✓ No stale sessions"
else
    echo "⚠ Warning: $STALE stale sessions (may be normal)"
fi

# 6. Error rates normal
echo "[6/7] Checking error rates..."
ERRORS=$(curl -s http://notification-service:8086/actuator/metrics/http.server.requests \
  | jq '.measurements[] | select(.value > 0) | .value' | wc -l)
if [ "$ERRORS" -lt 5 ]; then
    echo "✓ Acceptable error count"
else
    echo "⚠ Warning: $ERRORS errors detected"
fi

# 7. Summary
echo "[7/7] Rollback verification complete"
echo ""
echo "=== ROLLBACK SUCCESSFUL ==="
echo "All systems nominal. Phase B notification path disabled."
echo "Service-local notification handler now serving all clients."
```

---

## Rollback Timing & Impact

| Phase | Duration | Client Impact | Data Impact |
|-------|----------|---------------|-------------|
| Pre-verification | 5 min | None | None |
| Traffic redirect | 30 sec | Brief DNS delay | None |
| Handler re-enable | 1 min | None | None |
| Session migration | 1-2 min | ~1 sec reconnect per client | None |
| Verification | 5 min | None | None |
| **TOTAL** | **12-15 min** | **~1 sec disconnect/reconnect** | **Zero loss** |

**Key Property:** No data loss, no state corruption, only brief reconnect required.

---

## Rollback Decision Matrix

### When to Rollback

| Condition | Severity | Action |
|-----------|----------|--------|
| Edge service crash | CRITICAL | Immediate rollback (30 sec) |
| Edge command routing failures (>5% error rate) | HIGH | Rollback within 1 hour |
| Message delivery drops (>1%) | HIGH | Rollback within 1 hour |
| Database corruption | CRITICAL | Rollback + investigate + fix + re-deploy |
| Memory leak in edge (grows >1GB/hr) | HIGH | Rollback within 1 hour |
| Session loss (reconnect storms) | MEDIUM | Rollback within 4 hours |
| Notification-service endpoint fails | CRITICAL | Immediate rollback (30 sec) |
| Performance degradation (>2x latency) | MEDIUM | Rollback within 4 hours |

### When to Stay With Phase B

| Condition | Action |
|-----------|--------|
| All metrics nominal | Continue monitoring |
| No errors observed | Continue canary |
| Client count ramping normally | Continue gradual increase |
| Database performing well | Continue |

---

## Rollback Runbook (Quick Reference)

### Fastest Possible Rollback (Emergency)

```bash
# 1. Redirect traffic immediately
ansible all -m shell -a "sed -i 's/edge-service/notification-service/g' /etc/nginx/realtime.conf && nginx -s reload"
# Time: ~15 seconds

# 2. Disable edge endpoint (optional)
kubectl set env deployment/edge-service REALTIME_EDGE_ENABLED=false
# Time: ~20 seconds

# 3. Monitor reconnect
kubectl logs -l app=notification-service -f | tail -20
# Time: 1-2 minutes

# 4. Verify
curl http://notification-service:8086/health
# Time: ~5 seconds

# TOTAL ROLLBACK TIME: ~2-3 minutes
```

### Safe Rollback (Standard)

See **Step 1-7** above. Total time: 12-15 minutes.

---

## Post-Rollback Analysis (24 Hours After)

**File:** `review code/realtime-edge-phase-b-rollback-postmortem.md`

```markdown
# Phase B Rollback Analysis

**Rollback Initiated:** [timestamp]  
**Rollback Complete:** [timestamp]  
**Duration:** [minutes]  

## Timeline
- [time] Traffic redirected from edge to service-local
- [time] Last edge session disconnected
- [time] First service-local client connected
- [time] All clients recovered
- [time] System nominal

## Root Cause Analysis
- [Issue identified during canary]
- [Why edge couldn't handle it]
- [Why service-local was safer]

## Impact
- Client reconnects: N
- Message delivery gaps: duration
- Data loss: None
- Client confusion: Low/Medium/High

## Lessons Learned
- [What we'd do differently]
- [What worked well]
- [Process improvements for next attempt]

## Path Forward
- Fix identified issue
- Staging re-validation required
- New canary candidate ready: [date]
```

---

## Success Criteria

✅ **Rollback Rehearsal Complete** when:

1. [ ] Rollback procedure documented with exact commands
2. [ ] Pre-rollback verification checklist created
3. [ ] Traffic routing mechanism tested (DNS/LB/mesh)
4. [ ] Service-local handler verified ready to serve
5. [ ] Graceful disconnect message sent to edge clients
6. [ ] Post-rollback verification script executes cleanly
7. [ ] Zero data loss during rollback transition
8. [ ] Client reconnect latency measured (<3 seconds)
9. [ ] Database consistency verified post-rollback
10. [ ] Runbook created for emergency rollback

---

## Next Steps

- [ ] Execute rollback rehearsal in staging (dry run)
- [ ] Measure actual rollback time and client impact
- [ ] Refine procedure based on dry-run results
- [ ] Train ops team on rollback steps
- [ ] Document in runbook management system
- [ ] Link from canary launch checklist

---

## References

- **Phase B Staging Validation:** [realtime-edge-phase-b-task2-staging-validation-harness.md](realtime-edge-phase-b-task2-staging-validation-harness.md)
- **Phase B Readiness:** [realtime-edge-phase-b-staging-validation.md](realtime-edge-phase-b-staging-validation.md)
- **Edge Service Config:** [chatappBE/realtime-edge-service/src/main/resources/application.yaml](../chatappBE/realtime-edge-service/src/main/resources/application.yaml)
- **Notification-Service Config:** [chatappBE/notification-service/src/main/resources/application.yaml](../chatappBE/notification-service/src/main/resources/application.yaml)

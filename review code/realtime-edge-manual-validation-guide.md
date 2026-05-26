# Realtime Edge Local Validation Manual Guide

## Overview

This guide covers manual validation scenarios that are difficult or impossible to fully automate via integration tests. These scenarios validate runtime behavior, user-visible latency, and cross-instance correctness.

**Target Audience:** QA team, validation engineers, or developers performing hands-on testing

**Scope:** Local validation environment with single realtime-edge instance for scenarios 1-3, two-instance setup for scenario 4

**Time Estimate:** 45-60 minutes for full manual validation

---

## Prerequisites

Before starting manual validation:

1. **Docker environment running:**
   ```powershell
   .\validate-realtime-edge-local.ps1 full
   ```

2. **All services healthy:**
   ```powershell
   .\validate-realtime-edge-local.ps1 status
   ```

3. **Integration tests passing:**
   ```powershell
   .\validate-realtime-edge-local.ps1 test
   ```

4. **WebSocket client tool:**
   - Option A: Use `websocat` (install via `choco install websocat`)
   - Option B: Use browser DevTools Network tab + custom HTML client
   - Option C: Use `wscat` (npm install -g wscat)

5. **HTTP client tool:**
   - `curl` (included in Windows 10+)
   - Or Postman/Insomnia for GUI preference

---

## Scenario 1: Notification Domain - Inbound Command + Outbound Delivery

### Goal
Verify notification commands reach notification-service and notifications delivered via edge to user.

### Setup

1. **Create two test users:**
   ```bash
   # Create user A
   curl -X POST http://localhost:8082/api/v1/users/register \
     -H "Content-Type: application/json" \
     -d '{
       "email": "testuser-a@local.test",
       "password": "TestPassword123!",
       "username": "testuser-a"
     }'
   
   # Create user B
   curl -X POST http://localhost:8082/api/v1/users/register \
     -H "Content-Type: application/json" \
     -d '{
       "email": "testuser-b@local.test",
       "password": "TestPassword123!",
       "username": "testuser-b"
     }'
   ```

2. **Authenticate both users** (get JWT tokens):
   ```bash
   # User A login
   curl -X POST http://localhost:8081/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -d '{
       "email": "testuser-a@local.test",
       "password": "TestPassword123!"
     }'
   # Save token as $TOKEN_A
   
   # User B login (similarly, save as $TOKEN_B)
   ```

### Test Steps

**Step 1: Connect User A to Edge WebSocket**
```bash
websocat -H "Authorization: Bearer $TOKEN_A" \
  ws://localhost:8087/ws/realtime
```
Keep this connection open in terminal tab 1.

**Step 2: Create Notification for User A**
```bash
# From separate terminal tab 2, create a welcome notification for User A
curl -X POST http://localhost:8083/api/v1/notifications \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_A" \
  -d '{
    "title": "Welcome Test",
    "message": "Testing edge notification delivery",
    "type": "SYSTEM"
  }'
```

**Step 3: Observe Notification Delivery**
- In tab 1 (WebSocket connection): Watch for incoming notification message
- Expected message format:
  ```json
  {
    "type": "notification",
    "data": {
      "id": "...",
      "title": "Welcome Test",
      "message": "Testing edge notification delivery",
      "delivered": true
    }
  }
  ```
- **Success Signal:** Message appears within 1-2 seconds

**Step 4: Test Notification Command - Mark Read**
```bash
# Send mark-read command via edge
curl -X POST http://localhost:8087/api/v1/notifications/{notification-id}/mark-read \
  -H "Authorization: Bearer $TOKEN_A"
```

- Check notification-service logs:
  ```bash
  docker logs validation-notification-service | tail -20
  ```
- **Success Signal:** "Mark read command received" or similar appears in logs (no errors)

### Expected Outcomes

| Scenario | Expected | Actual | Status |
|----------|----------|--------|--------|
| Notification reaches edge within 1-2s | ✓ | | |
| Edge formats message for WebSocket | ✓ | | |
| Mark-read command reaches notification-service | ✓ | | |
| No ERROR logs on notification-service | ✓ | | |
| No ERROR logs on realtime-edge | ✓ | | |

### Troubleshooting

- **Notification not received:** Check edge logs for delivery service errors
- **Command not processed:** Check notification-service logs for HTTP errors
- **Timeout on WebSocket:** Verify auth token is valid, check edge health

---

## Scenario 2: Presence Domain - Lifecycle + Room Subscription

### Goal
Verify user connect/disconnect signals reach presence-service and room presence updates delivered via edge.

### Setup

Same user credentials from Scenario 1. Create JWT tokens for both users.

### Test Steps

**Step 1: Connect User A to Edge**
```bash
websocat -H "Authorization: Bearer $TOKEN_A" \
  ws://localhost:8087/ws/realtime
```
Connection automatically triggers setOnline signal.

**Step 2: Create a Presence Room and Subscribe**
```bash
# Join presence room "room-1"
curl -X POST http://localhost:8087/api/v1/presence/rooms/room-1/join \
  -H "Authorization: Bearer $TOKEN_A"
```

**Step 3: Connect User B and Join Same Room**
In terminal tab 3:
```bash
websocat -H "Authorization: Bearer $TOKEN_B" \
  ws://localhost:8087/ws/realtime
```

In terminal tab 4:
```bash
curl -X POST http://localhost:8087/api/v1/presence/rooms/room-1/join \
  -H "Authorization: Bearer $TOKEN_B"
```

**Step 4: Observe Presence Updates**
- In tab 1 (User A WebSocket): Should receive presence update showing User B joined
- Expected format:
  ```json
  {
    "type": "presence",
    "room": "room-1",
    "event": "user_joined",
    "user": "testuser-b",
    "timestamp": "..."
  }
  ```
- **Success Signal:** Presence event within 1-2 seconds of User B joining

**Step 5: Test User Disconnect**
In tab 3 (User B WebSocket):
```
Ctrl+C to disconnect
```

- In tab 1 (User A WebSocket): Should receive presence update showing User B left
- Expected event: "user_left"
- **Success Signal:** Disconnect detected within 3-5 seconds

### Expected Outcomes

| Scenario | Expected | Actual | Status |
|----------|----------|--------|--------|
| User A online signal sent | ✓ | | |
| Room join command routed | ✓ | | |
| User B presence update received by A | ✓ | | |
| User B disconnect detected | ✓ | | |
| No ERROR logs in presence-service | ✓ | | |

### Troubleshooting

- **Presence not updated:** Check presence-service logs for Redis pub/sub errors
- **Disconnect not detected:** May be expected delay (up to 5-10s TTL cleanup)
- **Command rejected:** Verify room name format and auth token validity

---

## Scenario 3: Chat Domain - Room + DM Messages

### Goal
Verify chat room JOIN/SEND deliver messages through edge, and DM extra recipient fanout works.

### Setup

Same user credentials from Scenarios 1-2. Create JWT tokens.

### Test Steps

**Step 1: Create Chat Room**
```bash
# Create room "test-room-1" via chat-service
curl -X POST http://localhost:8085/api/v1/chat/rooms \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_A" \
  -d '{
    "name": "test-room-1",
    "isPrivate": false
  }'
```

**Step 2: User A Joins Room and User B Joins**
Terminal 1 (User A WebSocket):
```bash
websocat -H "Authorization: Bearer $TOKEN_A" \
  ws://localhost:8087/ws/realtime
```

Terminal 2 (User A sends join command):
```bash
curl -X POST http://localhost:8087/api/v1/chat/rooms/test-room-1/join \
  -H "Authorization: Bearer $TOKEN_A"
```

Terminal 3 (User B WebSocket):
```bash
websocat -H "Authorization: Bearer $TOKEN_B" \
  ws://localhost:8087/ws/realtime
```

Terminal 4 (User B joins):
```bash
curl -X POST http://localhost:8087/api/v1/chat/rooms/test-room-1/join \
  -H "Authorization: Bearer $TOKEN_B"
```

**Step 3: User A Sends Message**
Terminal 2:
```bash
curl -X POST http://localhost:8087/api/v1/chat/rooms/test-room-1/messages \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_A" \
  -d '{
    "content": "Hello from User A in test-room-1"
  }'
```

**Step 4: Observe Message in Both Users' WebSocket Connections**
- Terminal 1 (User A): Should see message echoed back
- Terminal 3 (User B): Should receive message
- Expected format:
  ```json
  {
    "type": "message",
    "room": "test-room-1",
    "sender": "testuser-a",
    "content": "Hello from User A in test-room-1",
    "timestamp": "..."
  }
  ```
- **Success Signal:** Message delivered within 1 second to both users

**Step 5: Test DM (Direct Message) Extra Recipient**
Terminal 2:
```bash
curl -X POST http://localhost:8087/api/v1/chat/rooms/dm/messages \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_A" \
  -d '{
    "recipientId": "testuser-b",
    "content": "This is a DM from User A to User B"
  }'
```

**Step 6: Observe DM in Both Users' WebSocket Connections**
- Terminal 1 (User A): Message should appear
- Terminal 3 (User B): Message should appear
- **Success Signal:** DM fanout works; both users receive the same message

### Expected Outcomes

| Scenario | Expected | Actual | Status |
|----------|----------|--------|--------|
| Room join processed | ✓ | | |
| Message sent to room | ✓ | | |
| Message delivered to User A | ✓ | | |
| Message delivered to User B within 1s | ✓ | | |
| DM fanout to recipient | ✓ | | |
| No message loss | ✓ | | |
| No ERROR logs in chat-service | ✓ | | |

### Troubleshooting

- **Message not delivered:** Check realtime-edge logs for delivery service errors
- **DM not fanout:** Check chat handler for extra recipient routing logic
- **Command rejected:** Verify room exists and auth tokens valid

---

## Scenario 4: Friendship Domain - Kafka Topic Routing (Post-Hardening)

### Goal
Verify friendship request events route to correct Kafka aggregate topics and reach recipient via edge.

### Prerequisites

- Verify Kafka is running: `docker logs validation-kafka | grep -i "started"`
- Verify topic creation: `docker exec validation-kafka kafka-topics --bootstrap-server localhost:9092 --list | grep friendship`

Expected topics:
- `friendship.request.events`
- `friendship.events`

### Test Steps

**Step 1: Monitor Kafka Topics**
Terminal (keep open throughout):
```bash
docker exec validation-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic friendship.request.events \
  --from-beginning &
```

Another terminal:
```bash
docker exec validation-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic friendship.events \
  --from-beginning &
```

**Step 2: User A Sends Friend Request to User B**
Terminal:
```bash
curl -X POST http://localhost:8087/api/v1/friendship/requests/send \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_A" \
  -d '{
    "recipientId": "testuser-b"
  }'
```

**Step 3: Verify Message on Kafka Topic**
- In Kafka consumer terminals: Watch for event message
- **Success Signal:** Event appears on `friendship.request.events` topic within 2 seconds
- Expected payload contains: friendshipId, senderId (testuser-a), recipientId (testuser-b)

**Step 4: User B Connects and Receives Request Notification**
Terminal (User B WebSocket):
```bash
websocat -H "Authorization: Bearer $TOKEN_B" \
  ws://localhost:8087/ws/realtime
```

Terminal (Check if notification appears):
- User B's WebSocket should receive notification of friend request
- **Success Signal:** Notification within 2-3 seconds

**Step 5: User B Accepts Request**
Terminal:
```bash
curl -X POST http://localhost:8087/api/v1/friendship/requests/{request-id}/accept \
  -H "Authorization: Bearer $TOKEN_B"
```

**Step 6: Verify Acceptance on Kafka**
- Watch Kafka consumer for event on `friendship.events` topic
- **Success Signal:** Status change event appears within 2 seconds

### Expected Outcomes

| Scenario | Expected | Actual | Status |
|----------|----------|--------|--------|
| Request published to friendship.request.events | ✓ | | |
| Request NOT published to friendship.events | ✓ | | |
| Request notification reaches User B via edge | ✓ | | |
| Acceptance published to friendship.events | ✓ | | |
| Topic routing matches aggregate topic model | ✓ | | |
| No ERROR logs in friendship-service | ✓ | | |

### Troubleshooting

- **Events not on Kafka:** Check FriendshipEventProducer logs in friendship-service
- **Wrong topic:** Verify hardening fix applied (producer routes to aggregate topics)
- **Notifications not received:** Check realtime-edge FriendshipRealtimeDeliveryService logs
- **Consumer lag:** Kafka takes time to replicate; wait 5-10 seconds

---

## Scenario 5: Cross-Domain Consistency Check

### Goal
Verify all 4 domains work together without conflicts or duplication.

### Test Steps

**Run a rapid sequence:**

1. Create notifications (Scenario 1)
2. Update presence (Scenario 2)
3. Send chat message (Scenario 3)
4. Send friend request (Scenario 4)

All simultaneously or in rapid sequence.

### Expected Outcomes

| Scenario | Expected | Actual | Status |
|----------|----------|--------|--------|
| All 4 operations complete without error | ✓ | | |
| No message duplication | ✓ | | |
| No cross-domain interference | ✓ | | |
| Metrics increment correctly | ✓ | | |
| Edge delivery counters match message counts | ✓ | | |

---

## Scenario 6: Rollback Path Verification

### Goal
Verify legacy domain-service websocket endpoints still work (for rollback safety).

### Test Steps

**Step 1: Connect to Legacy Notification WebSocket**
```bash
websocat -H "Authorization: Bearer $TOKEN_A" \
  ws://localhost:8083/ws/notifications
```

**Step 2: Send Notification via Notification Service Directly**
```bash
curl -X POST http://localhost:8083/api/v1/notifications \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN_A" \
  -d '{
    "title": "Legacy Test",
    "message": "Testing rollback path"
  }'
```

**Step 3: Observe Message on Legacy Endpoint**
- Should receive notification on direct notification-service websocket
- **Success Signal:** Notification appears without going through edge

**Repeat for:**
- Presence: `ws://localhost:8084/ws/presence`
- Chat: `ws://localhost:8085/ws/chat`
- Friendship: `ws://localhost:8086/ws/friendship`

### Expected Outcomes

| Service | Legacy Path Works | Rollback Ready |
|---------|-------------------|----------------|
| Notification | ✓ | ✓ |
| Presence | ✓ | ✓ |
| Chat | ✓ | ✓ |
| Friendship | ✓ | ✓ |

---

## Scenario 7: Metrics and Logging Verification

### Goal
Verify metrics are being recorded and logs show expected patterns.

### Test Steps

**Step 1: Check Realtime Edge Metrics**
```bash
curl http://localhost:8087/actuator/metrics
```

Expected metrics:
- `realtime.edge.delivery.success`
- `realtime.edge.delivery.failure`
- `realtime.edge.session.count`
- `realtime.edge.handoff.publish`
- `realtime.edge.handoff.consume`

**Step 2: Verify Metric Values Increase**
- Run Scenario 1-4 tests
- Re-run metrics endpoint
- **Success Signal:** Counters increment, gauges update

**Step 3: Review Logs for ERROR Patterns**
```bash
docker logs validation-realtime-edge | grep ERROR
docker logs validation-notification-service | grep ERROR
docker logs validation-presence-service | grep ERROR
docker logs validation-chat-service | grep ERROR
docker logs validation-friendship-service | grep ERROR
```

- **Success Signal:** No ERROR-level messages during normal operation
- WARN and INFO acceptable

---

## Test Result Reporting

After completing all scenarios, fill in:

```markdown
# Manual Validation Results

**Date:** [DATE]
**Validator:** [NAME]
**Environment:** Docker local validation

## Summary

| Domain | Inbound Command | Outbound Delivery | Rollback | Overall |
|--------|-----------------|-------------------|----------|---------|
| Notification | PASS/FAIL | PASS/FAIL | PASS/FAIL | PASS/FAIL |
| Presence | PASS/FAIL | PASS/FAIL | PASS/FAIL | PASS/FAIL |
| Chat | PASS/FAIL | PASS/FAIL | PASS/FAIL | PASS/FAIL |
| Friendship | PASS/FAIL | PASS/FAIL | PASS/FAIL | PASS/FAIL |

## Issues Found

[List any failures, timeouts, or unexpected behavior]

## Recommendation

- Ready for staging validation: YES/NO
- Needs fixes before staging: [List if any]

## Sign-Off

Validator: __________________ Date: __________
```

---

## Cleanup

After manual validation completes:

```powershell
.\validate-realtime-edge-local.ps1 cleanup
```

This removes all containers, volumes, and test artifacts.

---

## Quick Reference: Service URLs

- Auth Service: `http://localhost:8081`
- User Service: `http://localhost:8082`
- Notification Service: `http://localhost:8083`
- Presence Service: `http://localhost:8084`
- Chat Service: `http://localhost:8085`
- Friendship Service: `http://localhost:8086`
- Realtime Edge Service: `http://localhost:8087`
- Realtime Edge WebSocket: `ws://localhost:8087/ws/realtime`
- Redis: `localhost:6379`
- Kafka: `localhost:9092`


## 1. Notification REST Contract Alignment

- [x] 1.1 Update frontend notification room-settings, mute, and unmute API calls to use the canonical room-scoped gateway paths
- [x] 1.2 Verify notification-service room mute controller behavior and add or adjust tests for successful authenticated settings reads

## 2. Notification WebSocket Contract Alignment

- [x] 2.1 Add gateway websocket routing for the notification service on the canonical `/ws/notifications` path
- [x] 2.2 Update frontend notification websocket configuration to use the canonical plural notification path

## 3. Reconnect Suppression And Diagnostics

- [x] 3.1 Update the notification websocket client to suppress repeated reconnect attempts after non-recoverable route or handshake failures
- [x] 3.2 Add observable diagnostics and targeted frontend tests for notification websocket failure and reconnect behavior

## 4. Verification

- [x] 4.1 Verify room notification settings requests resolve without internal server error through the gateway
- [x] 4.2 Verify notification websocket connects through the gateway without repeated network spam in the browser
- [x] 4.3 Run focused frontend and backend tests covering notification room settings and notification websocket routing behavior
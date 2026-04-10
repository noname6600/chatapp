## Why

Notification room settings currently fail through the gateway with an internal server error, and the notification websocket keeps reconnecting noisily in the browser network panel. The current notification REST and websocket contracts are inconsistent across frontend, gateway, and notification-service, so users cannot reliably load mute settings and the client keeps retrying a broken realtime path.

## What Changes

- Align room notification settings REST calls with the canonical notification-service route so room mute state can be read, muted, and unmuted through the gateway without path mismatches.
- Add explicit notification websocket routing through the gateway and align the frontend websocket endpoint name with the backend handler path.
- Prevent infinite or noisy notification websocket reconnect behavior when the connection fails for non-recoverable reasons such as missing route or invalid handshake path.
- Add targeted diagnostics and tests around notification settings retrieval and notification websocket connect or reconnect behavior.

## Capabilities

### New Capabilities
- None.

### Modified Capabilities
- `room-notification-settings`: Clarify the canonical room mute settings read and write routes so mute state loads successfully for the authenticated user.
- `notification-realtime-sync`: Notification realtime flow must distinguish recoverable disconnects from broken endpoint or handshake failures and avoid repeated futile reconnect churn.
- `gateway-routing`: Gateway websocket routing must expose the notification realtime path in addition to existing REST routing.
- `fe-websocket-gateway-integration`: Frontend notification websocket configuration must use the gateway path contract that the backend and gateway actually serve.

## Impact

- Frontend notification REST and websocket clients: `chatappFE/src/api/notification.service.ts`, `chatappFE/src/config/ws.config.ts`, `chatappFE/src/websocket/notification.socket.ts`, `chatappFE/src/store/notification.store.tsx`
- Gateway routing configuration: `chatappBE/gateway-service/src/main/resources/application.yaml`
- Notification service controllers and websocket config: `chatappBE/notification-service/src/main/java/com/example/notification/controller/RoomMuteController.java`, `chatappBE/notification-service/src/main/java/com/example/notification/configuration/WebSocketConfig.java`
- Tests covering room mute settings endpoint behavior and notification websocket retry behavior
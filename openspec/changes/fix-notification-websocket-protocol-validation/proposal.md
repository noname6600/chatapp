## Why

The notification websocket connection is being rejected with code 1002 (Protocol error) due to JWT handshake validation failures on the backend. This causes the client to enter rapid reconnect loops, resulting in connection spam. The backend needs improved error handling and diagnostics to identify why tokens are being rejected, and potentially fix token validation issues in the handshake interceptor.

## What Changes

- Add comprehensive handshake rejection reasons and logging to the JWT handshake interceptor
- Ensure token extraction from WebSocket query parameters works correctly across gateway routing
- Validate that JWT decoder is functioning properly and returning valid user IDs
- Add metrics/counters for successful vs. failed handshakes
- Document the WebSocket JWT authentication flow and troubleshooting steps
- Potentially fix token parameter extraction if gateway is not propagating query params correctly

## Capabilities

### New Capabilities
- `websocket-handshake-diagnostics`: Enhanced logging and metrics for WebSocket connection validation failures

### Modified Capabilities
- `notification-websocket-auth`: Update JWT token validation requirements to ensure tokens are properly extracted and validated during WebSocket handshake

## Impact

- Backend notification service: `chatappBE/notification-service/`
- Common WebSocket infrastructure: `chatappBE/common/common-websocket/`
- API Gateway routing for `/ws/notifications` endpoint
- DevOps/monitoring: New metrics for handshake success/failure rates

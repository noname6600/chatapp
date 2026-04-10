## 1. Update REST API Configuration

- [x] 1.1 Examine current `src/config/api.config.ts` structure
- [x] 1.2 Examine current `src/api/clients.ts` to understand API client creation
- [x] 1.3 Update `src/config/api.config.ts` to use single `API_BASE_URL` or `API_GATEWAY_URL` instead of individual service URLs
- [x] 1.4 Support environment variable override: `process.env.REACT_APP_API_URL` for custom gateway URLs
- [x] 1.5 Verify all API service URLs (auth, user, chat, presence, friend, notification, upload) now use the centralized gateway base URL
- [x] 1.6 Update `src/api/clients.ts` to use new centralized gateway configuration
- [x] 1.7 Create `.env.local` file with `REACT_APP_API_URL=http://localhost:8080/api/v1` for local development
- [x] 1.8 Create `.env.production` file with production gateway URL (or document how to set it)

## 2. Update WebSocket Configuration

- [x] 2.1 Search codebase for WebSocket URL hardcoding (grep for `ws://localhost:808x` or `wss://`)
- [x] 2.2 Locate WebSocket connection code (likely in `src/websocket/` or service-specific files)
- [x] 2.3 Document current WebSocket endpoint structure and which services use WebSocket (chat, presence, friendship)
- [x] 2.4 Create centralized WebSocket URL configuration (similar to REST API config)
- [x] 2.5 Support environment variable for WebSocket URL: `REACT_APP_WS_URL` (default: `ws://localhost:8080/api/v1`)
- [x] 2.6 Update chat WebSocket connection to use gateway endpoint
- [x] 2.7 Update presence WebSocket connection to use gateway endpoint
- [x] 2.8 Update friendship WebSocket connection to use gateway endpoint
- [x] 2.9 Ensure WebSocket connections include JWT authentication header/token
- [x] 2.10 Verify WebSocket reconnection logic works with gateway endpoints

## 3. Test REST API Integration

- [ ] 3.1 Start backend with `docker-compose -f chatappBE/docker-compose.local.yml up` (ensure all 9 containers running)
- [ ] 3.2 Verify gateway health endpoint: `curl http://localhost:8080/actuator/health`
- [ ] 3.3 Start frontend: `npm run dev` (should run on localhost:5173)
- [ ] 3.4 Test login: verify auth API call routes through gateway (check browser network tab)
- [ ] 3.5 Test user profile fetch: verify user API call routes through gateway
- [ ] 3.6 Test creating a chat message: verify chat API call routes through gateway
- [ ] 3.7 Verify no direct service port calls (8081-8088) appear in network traffic
- [ ] 3.8 Verify all API responses work correctly (no 502/503 errors from gateway)
- [ ] 3.9 Verify JWT token is included in Authorization header for all authenticated requests
- [ ] 3.10 Test token refresh: ensure 401 handling still works through gateway

## 4. Test WebSocket Integration

- [ ] 4.1 Start frontend and backend as in step 3
- [ ] 4.2 Open chat room and verify WebSocket connection succeeds (check browser DevTools Network tab)
- [ ] 4.3 Verify WebSocket connects to gateway URL (not individual service port)
- [ ] 4.4 Test sending/receiving chat messages in real-time through gateway
- [ ] 4.5 Test presence updates (coming online/offline) flow through gateway
- [ ] 4.6 Test friendship notifications through gateway
- [ ] 4.7 Simulate network interruption: disconnect chat and verify reconnection attempts through gateway
- [ ] 4.8 Verify WebSocket session state is maintained across reconnections
- [ ] 4.9 Monitor latency: verify no noticeable additional delay through gateway

## 5. Validate Full End-to-End Flow

- [ ] 5.1 Create new user account (registration via gateway)
- [ ] 5.2 Login with new account (verify auth through gateway)
- [ ] 5.3 Search for and add friend (friendship service through gateway)
- [ ] 5.4 Initiate chat with friend (chat through gateway + WebSocket)
- [ ] 5.5 Send and receive messages (verify real-time delivery)
- [ ] 5.6 Verify presence indicator shows friend online/offline
- [ ] 5.7 Test file upload (upload service through gateway)
- [ ] 5.8 Verify all notifications arrive in real-time
- [ ] 5.9 Restart browser and verify session recovery

## 6. Performance and Error Handling

- [ ] 6.1 Monitor API response times through gateway (should be negligible additional latency)
- [ ] 6.2 Test rate limiting: verify gateway rate limiter triggers after ~20 req/s
- [ ] 6.3 Test circuit breaker: simulate service failure and verify fallback behavior
- [ ] 6.4 Test CORS: verify gateway CORS headers allow localhost:5173
- [ ] 6.5 Test error handling: verify 4xx/5xx errors from gateway display correctly on FE
- [ ] 6.6 Verify gateway logs show requests from frontend client IPs

## 7. Documentation and Cleanup

- [ ] 7.1 Update README.md with new API integration setup instructions
- [ ] 7.2 Document environment variables for local dev and production
- [ ] 7.3 Update contributor guide: explain that all API calls now go through gateway
- [ ] 7.4 Remove or comment out any old hardcoded service URLs in codebase
- [ ] 7.5 Add JSDoc comments to API services explaining gateway routing
- [ ] 7.6 Test production build: `npm run build` with production environment variables
- [ ] 7.7 Create/update .env.example with all required environment variables
- [ ] 7.8 Mark old individual service URL configuration as deprecated (if keeping for reference)

## 8. Final Validation

- [ ] 8.1 Clean rebuild: `rm -rf node_modules && npm install && npm run dev`
- [ ] 8.2 Fresh backend startup with docker-compose.local.yml
- [ ] 8.3 Full smoke test: login → chat → friend request → notifications
- [ ] 8.4 Verify no console errors or network failures
- [ ] 8.5 Document any breaking changes or migration steps for existing developers
- [ ] 8.6 All tests passing (if frontend has unit/integration tests)

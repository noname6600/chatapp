## 1. Backend Setup - WebSocket and Kafka Configuration

- [x] 1.1 Add `spring-boot-starter-websocket` and update `common-websocket` dependency in friendship-service build.gradle
- [x] 1.2 Create `FriendshipWebSocketConfig` class extending `AbstractWebSocketMessageBrokerConfigurer` to configure WebSocket endpoints and message broker
- [x] 1.3 Create `FriendshipWebSocketSecurityConfig` class extending `AbstractJwtHandshakeInterceptor` for WebSocket JWT authentication
- [x] 1.4 Register `/ws` WebSocket endpoint with STOMP messaging support

## 2. Backend - Kafka Event Consumption

- [x] 2.1 Create `FriendshipEventConsumer` class with `@KafkaListener` for `friendship.events` topic
- [x] 2.2 Create `FriendshipRequestEventConsumer` class with `@KafkaListener` for `friendship.request.events` topic
- [x] 2.3 Implement event parsing logic to extract user IDs, action types, and friendship status from Kafka messages
- [x] 2.4 Map Kafka event types to WebSocket message types (FRIEND_REQUEST_SENT → FRIEND_REQUEST_RECEIVED, etc.)

## 3. Backend - WebSocket Broadcasting Implementation

- [x] 3.1 Inject `IUserBroadcaster` from `common-websocket` into event consumers
- [x] 3.2 Create `FriendshipWebSocketPublisher` service class with methods to broadcast friendship events
- [x] 3.3 Implement method to send `FRIEND_REQUEST_RECEIVED` to recipient when `FRIEND_REQUEST_SENT` event consumed
- [x] 3.4 Implement method to send `FRIEND_STATUS_CHANGED` to both users when friendship status changes (accept/decline/block/unblock)
- [x] 3.5 Wrap broadcast payloads in `WsOutgoingMessage` with proper message type and user IDs

## 4. Backend - REST API Enhancements

- [x] 4.1 Add GET `/api/friends/unread-count` endpoint in `FriendController` returning unread friend request count
- [x] 4.2 Add method to `IFriendQueryService` to fetch unread friend request count by user ID
- [x] 4.3 Add database query to count pending (not accepted/declined) friend requests for a user
- [x] 4.4 Ensure endpoint returns count in consistent JSON format (e.g., `{ "unreadCount": 5 }`)

## 5. Frontend - WebSocket Event Handlers

- [x] 5.1 Create `src/websocket/handlers/friendshipEventHandler.ts` to handle friendship WebSocket messages
- [x] 5.2 Import and dispatch Redux actions for `FRIEND_REQUEST_RECEIVED` message type
- [x] 5.3 Import and dispatch Redux actions for `FRIEND_REQUEST_ACCEPTED` message type
- [x] 5.4 Import and dispatch Redux actions for `FRIEND_STATUS_CHANGED` message type
- [x] 5.5 Register friendship event handler in WebSocket connection service (attach listener on connect)

## 6. Frontend - Redux Store for Friendship State

- [x] 6.1 Create Redux slice `src/store/friendshipSlice.ts` with unread count state
- [x] 6.2 Add `setUnreadCount(number)` reducer action
- [x] 6.3 Add `incrementUnreadCount()` reducer action (for new requests)
- [x] 6.4 Add `decrementUnreadCount()` reducer action (for accepted/declined requests)
- [x] 6.5 Create async thunk `fetchUnreadCount()` to GET `/api/friends/unread-count` on app load
- [x] 6.6 Register friendship slice in Redux store `configureStore`
- [x] 6.7 Dispatch `fetchUnreadCount()` thunk in app initialization (App.tsx useEffect)

## 7. Frontend - Unread Badge UI Component

- [x] 7.1 Create `src/components/FriendRequestBadge.tsx` component to display unread count
- [x] 7.2 Connect component to Redux to read `unreadCount` from `friendshipSlice`
- [x] 7.3 Render badge conditionally (only if count > 0)
- [x] 7.4 Style badge with appropriate positioning (typically on friend/contact icon in navigation)
- [x] 7.5 Add click handler to navigate to friend requests page

## 8. Frontend - Friend Requests Page Enhancement

- [x] 8.1 Identify existing friend requests page/component (likely in `src/pages/`)
- [x] 8.2 Dispatch Redux action to clear unread count when user navigates to friend requests view (mark as read)
- [x] 8.3 Add WebSocket listener in friend requests page to update list in realtime when new requests arrive
- [x] 8.4 Handle `FRIEND_REQUEST_RECEIVED` to add new request to UI list without page refresh
- [x] 8.5 Handle `FRIEND_REQUEST_ACCEPTED` to remove request from pending list and update friendship status

## 9. Frontend - Notification Integration

- [x] 9.1 Update notification panel component to display friendship notifications from WebSocket
- [x] 9.2 Add friendship icons and styling for friend request notifications in notification panel
- [x] 9.3 Parse `FRIEND_REQUEST_RECEIVED` and `FRIEND_STATUS_CHANGED` events into notification UI entries
- [x] 9.4 Add click handler on friendship notifications to navigate to relevant UI (friend requests or friend profile)

## 10. Frontend - Reconnection Handling

- [x] 10.1 Add WebSocket reconnect event listener in friendship event handler
- [x] 10.2 Dispatch `fetchUnreadCount()` thunk on reconnect to re-sync badge
- [ ] 10.3 Add notification about pending requests fetched after disconnect (optional UX improvement)

## 11. Integration Testing

- [ ] 11.1 Test backend: Send friend request via REST API and verify Kafka event published
- [ ] 11.2 Test backend: Verify Kafka consumer triggers WebSocket broadcast to recipient and requester
- [ ] 11.3 Test backend: Verify `/api/friends/unread-count` returns correct count
- [ ] 11.4 Test frontend: WebSocket message listener receives and parses friendship events correctly
- [ ] 11.5 Test frontend: Redux dispatch updates unread count state from WebSocket messages
- [ ] 11.6 Test frontend: Badge updates when friend request received while app is open
- [ ] 11.7 Test frontend (multi-tab): Same user with 2 browser tabs receives events on both tabs simultaneously
- [ ] 11.8 Test frontend (reconnect): WebSocket reconnect re-fetches unread count and restores listening

## 12. Documentation and Deployment

- [x] 12.1 Add inline code comments to WebSocket and Kafka consumer classes
- [x] 12.2 Document new WebSocket message types in project README (FRIEND_REQUEST_RECEIVED, FRIEND_STATUS_CHANGED, etc.)
- [x] 12.3 Update API documentation for new `/api/friends/unread-count` endpoint
- [ ] 12.4 Deploy backend changes and monitor Kafka consumer logs for errors
- [ ] 12.5 Deploy frontend changes and verify badge appears correctly alongside other UI elements

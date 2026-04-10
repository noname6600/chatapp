## 1. Preparation and Structure Setup

- [x] 1.1 Create folder structure for `/src/features/` directory and subdirectories for each feature (auth, chats, rooms, friends, presence, profile, notifications)
- [x] 1.2 Create `/src/core/` folder for global concerns (auth.store, ui.store, types)
- [x] 1.3 Create `/src/components/shared/` folder for reusable UI components
- [ ] 1.4 Document the new structure in README or architecture guide
- [ ] 1.5 Set up ESLint/TypeScript rules to prevent cross-feature dependencies

## 2. Core and Shared Organization

- [x] 2.1 Move global types from `/src/types/` to `/src/core/types/` (auth types, common types, enums)
- [x] 2.2 Create `/src/core/auth.store.tsx` with centralized authentication state and actions
- [x] 2.3 Create `/src/core/ui.store.tsx` with global UI state (modals, notifications, overlays)
- [ ] 2.4 Consolidate shared utility functions in `/src/utils/` (error handling, formatting, validators)
- [ ] 2.5 Move reusable UI components (common layouts, buttons, modals) to `/src/components/shared/`

## 3. Auth Feature Refactoring

- [x] 3.1 Create auth feature folder structure: `/src/features/auth/types/`, `/src/features/auth/api/`, `/src/features/auth/hooks/`, `/src/features/auth/components/`
- [x] 3.2 Migrate auth types to `/src/features/auth/types/auth.ts` (User, AuthToken, LoginRequest, etc.)
- [x] 3.3 Move auth API calls to `/src/features/auth/api/auth.api.ts` (login, logout, signup, refresh token)
- [x] 3.4 Create `/src/features/auth/api/auth.service.ts` with auth business logic (token handling, session validation)
- [ ] 3.5 Create custom hooks in `/src/features/auth/hooks/` (useLogin, useLogout, useAuthStatus, useRefreshToken)
- [ ] 3.6 Create auth-related components in `/src/features/auth/components/` (LoginForm, RegisterForm, etc.)
- [x] 3.7 Create `/src/features/auth/index.ts` with public exports
- [ ] 3.8 Update all imports throughout the app to use new auth paths

## 4. Chats Feature Refactoring

- [x] 4.1 Create chats feature folder structure: `/src/features/chats/types/`, `/src/features/chats/api/`, `/src/features/chats/hooks/`, `/src/features/chats/components/`, `/src/features/chats/store/`, `/src/features/chats/websocket/`
- [x] 4.2 Define types in `/src/features/chats/types/`: messages.ts, chat.ts (Message, Chat, ChatMessage, ChatResponse DTOs)
- [x] 4.3 Create `/src/features/chats/api/messages.api.ts` (HTTP endpoints: fetchMessages, sendMessage, editMessage, deleteMessage)
- [x] 4.4 Create `/src/features/chats/api/messages.service.ts` (business logic: validate messages, format responses, handle caching)
- [ ] 4.5 Create `/src/features/chats/store/chats.store.tsx` with message state, actions, and selectors
- [ ] 4.6 Create custom data hooks in `/src/features/chats/hooks/` (useFetchMessages, useSendMessage, useEditMessage, useDeleteMessage)
- [ ] 4.7 Move chat components to `/src/features/chats/components/` (MessageList, MessageItem, MessageInput, ChatContainer)
- [x] 4.8 Create `/src/features/chats/websocket/message.socket.ts` for real-time message handling
- [x] 4.9 Create `/src/features/chats/index.ts` with public exports
- [ ] 4.10 Update chat component imports and references throughout app

## 5. Rooms Feature Refactoring

- [x] 5.1 Create rooms feature folder structure: `/src/features/rooms/types/`, `/src/features/rooms/api/`, `/src/features/rooms/hooks/`, `/src/features/rooms/components/`, `/src/features/rooms/store/`, `/src/features/rooms/modals/`, `/src/features/rooms/websocket/`
- [x] 5.2 Define types in `/src/features/rooms/types/`: rooms.ts, members.ts (Room, RoomMember, RoomResponse DTOs)
- [x] 5.3 Create `/src/features/rooms/api/rooms.api.ts` (HTTP endpoints: fetch rooms, create, join, leave, update)
- [x] 5.4 Create `/src/features/rooms/api/rooms.service.ts` (business logic for room operations)
- [x] 5.5 Create `/src/features/rooms/store/rooms.store.tsx` with room state, actions, selectors
- [ ] 5.6 Create custom hooks in `/src/features/rooms/hooks/` (useFetchRooms, useCreateRoom, useJoinRoom, useRoomUsers, useLeaveRoom)
- [ ] 5.7 Move room components to `/src/features/rooms/components/` (RoomList, RoomHeader, RoomSidebar, RoomSettings)
- [ ] 5.8 Move room modals to `/src/features/rooms/modals/` (CreateRoomModal, InviteModal, JoinRoomModal, RoomSettingsDetailModal)
- [ ] 5.9 Create `/src/features/rooms/websocket/room.socket.ts` for real-time room updates
- [x] 5.10 Create `/src/features/rooms/index.ts` with public exports
- [ ] 5.11 Update room-related imports throughout app

## 6. Friends Feature Refactoring

- [x] 6.1 Create friends feature folder structure: `/src/features/friends/types/`, `/src/features/friends/api/`, `/src/features/friends/hooks/`, `/src/features/friends/components/`, `/src/features/friends/store/`, `/src/features/friends/websocket/`
- [x] 6.2 Define types in `/src/features/friends/types/`: friends.ts, friendRequests.ts (Friend, FriendRequest, FriendResponse DTOs)
- [x] 6.3 Create `/src/features/friends/api/friends.api.ts` (HTTP endpoints for friend operations)
- [x] 6.4 Create `/src/features/friends/api/friends.service.ts` (friend business logic)
- [x] 6.5 Create `/src/features/friends/store/friends.store.tsx` with friend state and actions
- [ ] 6.6 Create hooks in `/src/features/friends/hooks/` (useFetchFriends, useSendFriendRequest, useAcceptFriendRequest, useRemoveFriend)
- [ ] 6.7 Move friend components to `/src/features/friends/components/` (FriendList, FriendRow, AddFriendPanel, MoreMenu)
- [ ] 6.8 Create `/src/features/friends/websocket/friend.socket.ts` for friend status updates
- [x] 6.9 Create `/src/features/friends/index.ts` with public exports
- [ ] 6.10 Update friend feature imports throughout app

## 7. Presence Feature Refactoring

- [x] 7.1 Create presence feature folder structure: `/src/features/presence/types/`, `/src/features/presence/hooks/`, `/src/features/presence/store/`, `/src/features/presence/websocket/`, `/src/features/presence/components/`
- [x] 7.2 Define types in `/src/features/presence/types/`: presence.ts (UserPresence, PresenceUpdate, TypingIndicatorEvent)
- [x] 7.3 Create `/src/features/presence/store/presence.store.tsx` with presence state, user status, typing indicators
- [ ] 7.4 Create hooks in `/src/features/presence/hooks/` (usePresence, useUserPresence, useTypingIndicator, useSendTypingIndicator)
- [x] 7.5 Create presence socket handlers in `/src/features/presence/websocket/` (presence.socket.ts, typing.socket.ts)
- [ ] 7.6 Move presence components to `/src/features/presence/components/` (OnlineDot, TypingIndicator, PresenceIndicator)
- [ ] 7.7 Create initialization hook for presence listener (usePresenceSocket in hooks)
- [x] 7.8 Create `/src/features/presence/index.ts` with public exports
- [ ] 7.9 Update presence-related imports throughout app

## 8. Profile and Notifications Features Refactoring

- [x] 8.1 Create profile feature folder structure: `/src/features/profile/types/`, `/src/features/profile/api/`, `/src/features/profile/hooks/`, `/src/features/profile/components/`, `/src/features/profile/store/`
- [x] 8.2 Define profile types in `/src/features/profile/types/`: profile.ts (UserProfile, ProfileUpdate, ProfileResponse)
- [x] 8.3 Create `/src/features/profile/api/` with profile API and service files
- [x] 8.4 Create profile store and hooks for profile management
- [ ] 8.5 Move profile components to `/src/features/profile/components/` (ProfileEditor, ProfilePreview, ProfileSettings)
- [x] 8.6 Create `/src/features/profile/index.ts` with public exports
- [ ] 8.7 Create notifications feature folder structure: `/src/features/notifications/types/`, `/src/features/notifications/store/`, `/src/features/notifications/components/`, `/src/features/notifications/websocket/`
- [ ] 8.8 Create notifications store and components for notification display
- [ ] 8.9 Create notification socket handlers for real-time notifications
- [ ] 8.10 Update profile and notification imports throughout app

## 9. Update App Routes and Page Organization

- [ ] 9.1 Update `/src/routes/AppRoutes.tsx` to import from new feature locations
- [ ] 9.2 Update `/src/pages/` to import components from feature modules via public exports
- [ ] 9.3 Update `/src/layouts/` to import components and hooks from new locations
- [ ] 9.4 Ensure all route dependencies are properly satisfied by feature exports

## 10. Import Path Updates and Verification

- [ ] 10.1 Global find-and-replace: update imports from `/api/` to `/features/*/api/`
- [ ] 10.2 Global find-and-replace: update imports from `/store/` to `/features/*/store/` or `/core/`
- [ ] 10.3 Global find-and-replace: update imports from `/types/` to `/features/*/types/` or `/core/types/`
- [ ] 10.4 Update WebSocket initialization and listener registration to use new paths
- [ ] 10.5 Run TypeScript compiler (`npm run build`) to catch all import errors
- [ ] 10.6 Run ESLint to verify code quality and linting rules pass

## 11. Testing and Validation

- [ ] 11.1 Start development server (`npm run dev`) and verify app loads without errors
- [ ] 11.2 Test authentication flow (login, logout, token refresh)
- [ ] 11.3 Test chat feature (send message, receive message, WebSocket sync)
- [ ] 11.4 Test rooms feature (list rooms, create, join, leave)
- [ ] 11.5 Test friends feature (add friend, friend list, friend status)
- [ ] 11.6 Test presence tracking (online status, typing indicators)
- [ ] 11.7 Test profile feature (view/edit profile, avatar upload)
- [ ] 11.8 Verify all WebSocket connections work correctly

## 12. Cleanup and Finalization

- [ ] 12.1 Remove old `/src/api/` folder (after confirming no remaining references)
- [ ] 12.2 Remove old component folders from `/src/components/` (keep only `/shared/`)
- [ ] 12.3 Remove old store folder `/src/store/` (keep only `/core/`)
- [ ] 12.4 Remove old `/src/websocket/` folder entries that moved to features
- [ ] 12.5 Clean up any duplicate type definitions
- [ ] 12.6 Run final TypeScript and ESLint checks
- [ ] 12.7 Build production bundle (`npm run build`) and verify no errors
- [ ] 12.8 Create or update architecture documentation with new structure diagrams

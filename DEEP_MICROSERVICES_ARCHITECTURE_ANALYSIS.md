# Deep Microservices Architecture Analysis - Spring Boot Chatapp Backend

**Analysis Date:** May 14, 2026  
**Scope:** All 9 backend services + common modules  
**Methodology:** Direct code inspection with concrete evidence  
**Level of Detail:** VERY DEEP - Service ownership, domain boundaries, event flows, storage, real-time mechanisms

---

## Executive Summary

This is a **cleanly separated microservices architecture** with:
- **Event-driven communication** via Kafka (inter-service) and Redis (intra-service real-time)
- **Clear domain ownership** with minimal violations
- **WebSocket-first real-time layer** in realtime-edge-service (unified entry point)
- **Service-specific databases** (no shared DB)
- **Proper encapsulation** of business logic in domain models and application services

### Architecture Overview Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                       API Gateway (Spring Cloud Gateway)         │
│  - CORS dedupe, JWT auth via custom filter, rate limiting       │
└────────────┬─────────────────────────────────────────────────────┘
             │ HTTP/REST & WebSocket
    ┌────────┴────────────────────────────────────────────┐
    │                                                      │
    v (HTTP)                                        v (WebSocket)
┌──────────────────────────────────────┐      ┌──────────────────────┐
│  Auth-Service (Monolithic Auth Hub)  │      │ Realtime-Edge-Service│
│  ├─ Account creation/login/oauth     │      │ (Command dispatch)   │
│  ├─ Token issuance & verification    │      │ ├─ WebSocket ingress │
│  └─ Password/email management        │      │ ├─ Command routing   │
│                                      │      │ ├─ Event delivery    │
└─────┬──────────────────────────────┬─┘      └────┬──────┬─────┬────┘
      │ ACCOUNT_CREATED event        │             │      │     │
      v                              │             │      │     │
   (KAFKA)                           │      REST calls to services
      │                              │      based on endpoint path
      ├──────────┬─────────┬─────────┼─────────────┼──────┤
      │          │         │         │             │      │
      v          v         v         v             v      v
┌───────────┐┌─────────┐┌──────────┐          ┌──────────────┐
│  User-    ││Friend-  ││Chat-     │          │ Chat-Service │
│ Service   ││ ship    ││ Service  │  <─REST──┤ (REST API)   │
│           ││ Service ││          │  Comds───│ (Commands via│
│ UserProfile││        ││ChatMessage│         │ HTTP + WS)   │
│           ││Friendship││          │         │              │
│           ││ (entity) ││ChatRoom  │         ├─ Send message│
│           ││          ││Message   │         ├─ Edit message│
│           ││          ││Reaction  │         ├─ React       │
└────┬──────┘└─────┬────┘└────┬──────┘         └──────┬───────┘
     │ ACC_CREATED│ FRIEND    │ MESSAGE_SENT         │ Redis
     │            │ EVENTS    │ REACTIONS            │ subscribers
     v            v           v                       v
   Local DB     Local DB    Local DB              (message fan-out)
   
   ┌──────────────────────────────────────────────────────────┐
   │         Upload-Service (Cloudinary wrapper)              │
   │  ├─ Prepare upload (signed URL generation)               │
   │  ├─ Confirm upload (public_id confirmation)              │
   │  └─ Asset metadata                                       │
   └─────────────────────────┬────────────────────────────────┘
                             v
                     Cloudinary (external)
                     
   ┌──────────────────────────────────────────────────────────┐
   │         Presence-Service (User online status)            │
   │  ├─ Online/offline/heartbeat                             │
   │  ├─ Redis TTL cache for presence state                   │
   │  ├─ Room join/leave tracking                             │
   │  └─ Typing indicators                                    │
   ├─ WebSocket handler (service-local)                       │
   └─ Redis publisher for real-time events                    │
   
   ┌──────────────────────────────────────────────────────────┐
   │      Notification-Service (Notification management)      │
   │  ├─ Notification entity (FRIEND_REQUEST, MENTION, etc)   │
   │  ├─ Mute settings per room                               │
   │  ├─ Kafka consumers:                                      │
   │  │  - AccountCreated (init user settings)                │
   │  │  - MessageCreated (check @mentions, @everyone)        │
   │  │  - FriendRequest (create notification)                │
   │  │  - ReactionEvent (if configured)                      │
   │  ├─ WebSocket publisher (push to clients)                │
   │  ├─ REST API (mark read, get unread count)               │
   │  └─ Redis publisher (notification.requested)             │
   └─────────────────────────────────────────────────────────┘

   ┌──────────────────────────────────────────────────────────┐
   │     Common Infrastructure (Shared libraries)             │
   │  ├─ common-core: base exceptions, error codes            │
   │  ├─ common-web: base controllers, responses              │
   │  ├─ common-kafka: event envelope, producer/consumer      │
   │  ├─ common-redis: pub/sub, session registry              │
   │  ├─ common-events: shared Kafka event contracts          │
   │  ├─ common-websocket: session registry, broadcasters     │
   │  ├─ common-security: JWT decoding, auth filters          │
   │  ├─ common-feign: FeignClient base config                │
   │  └─ common-redis-cache: Redis caching helpers            │
   └──────────────────────────────────────────────────────────┘
```

---

## 1. AUTH-SERVICE

**File Location:** `chatappBE/auth-service/src/main/java/com/example/auth/`

### 1.1 Package Structure

```
auth/
├── controller/
│   └── AuthController.java           # REST endpoints
├── service/
│   ├── IAuthService.java
│   ├── impl/
│   │   ├── AuthService.java          # Main auth orchestrator
│   │   ├── LocalAuthService.java     # Email/password auth
│   │   ├── OAuthAuthService.java     # Google/OAuth2 flow
│   │   ├── TokenService.java         # JWT issuance
│   │   ├── TokenServiceFacade.java   # Token lifecycle (refresh, logout)
│   │   ├── PasswordService.java      # Password hashing/validation
│   │   ├── EmailService.java         # Email sending via Resend
│   │   ├── ForgotPasswordService.java# Password reset flow
│   │   ├── GoogleTokenVerifier.java  # Google token validation
│   │   ├── IdentityProviderService.java
│   │   ├── AuthSessionService.java   # Session & token issuance
│   │   ├── BrowserOAuthService.java  # Browser-based OAuth flow
│   │   └── UserProfileReadinessService.java
│   ├── ILocalAuthService.java
│   ├── IOAuthService.java
│   ├── ITokenService.java
│   ├── ITokenServiceFacade.java
│   ├── IPasswordService.java
│   ├── IEmailService.java
│   ├── IForgotPasswordService.java
│   ├── IGoogleTokenVerifier.java
│   └── IAuthService.java
├── repository/
│   ├── AccountRepository.java        # Account/Account entity
│   ├── RefreshTokenRepository.java   # Refresh token storage
│   ├── PasswordResetTokenRepository.java
│   ├── VerificationTokenRepository.java
│   ├── JwtKeyRepository.java         # JWT signing keys
│   ├── OAuthLoginExchangeRepository.java
│   └── IdentityProviderRepository.java
├── entity/
│   ├── Account.java                  # Main auth entity
│   ├── RefreshToken.java
│   ├── PasswordResetToken.java
│   ├── VerificationToken.java
│   ├── JwtKey.java                   # Asymmetric signing keys
│   ├── OAuthLoginExchange.java
│   └── IdentityProvider.java
├── jwt/
│   ├── IKeyManager.java
│   ├── IJwtVerifierService.java
│   ├── impl/
│   │   ├── KeyManager.java
│   │   └── JwtVerifierService.java
│   ├── KeyRecord.java
│   └── JwksUtils.java
├── dto/
│   ├── RegisterRequest.java
│   ├── LoginRequest.java
│   ├── AuthResponse.java
│   ├── RefreshRequest.java
│   ├── GoogleLoginRequest.java
│   └── (other auth DTOs)
├── kafka/
│   └── AccountCreatedEventProducer.java
├── scheduler/
│   ├── RefreshTokenCleanupScheduler.java
│   └── JwtKeyCleanupScheduler.java
├── configuration/
│   ├── SecurityConfig.java
│   ├── WebSocketConfig.java
│   └── (other configs)
├── exception/
│   └── AuthErrorCode.java
├── enums/
│   └── (auth enums)
├── integration/
│   └── resend/
│       └── ResendEmailClient.java    # Resend API client
├── client/
│   └── (OAuth clients)
└── AuthServiceApplication.java       # Main app class
```

### 1.2 Key Entities & Responsibilities

| Entity | Location | Purpose | Table |
|--------|----------|---------|-------|
| Account | `entity/Account.java` | Core auth entity (email, password, status) | `accounts` |
| RefreshToken | N/A | Refresh token persistence | `refresh_tokens` |
| PasswordResetToken | N/A | Password reset flow | `password_reset_tokens` |
| VerificationToken | N/A | Email verification | `verification_tokens` |
| JwtKey | `entity/JwtKey.java` | Asymmetric key pair for JWT signing | `jwt_keys` |
| OAuthLoginExchange | N/A | OAuth redirect tracking | `oauth_login_exchanges` |
| IdentityProvider | N/A | OAuth provider config | `identity_providers` |

### 1.3 Business Responsibility

**Auth-Service owns:**
- ✅ Account creation (register)
- ✅ Local authentication (email/password login)
- ✅ OAuth2 integration (Google)
- ✅ JWT token lifecycle (issue, refresh, verify, revoke)
- ✅ Password management (change, reset)
- ✅ Email verification
- ✅ Asymmetric JWT key generation & rotation
- ✅ Session management (refresh token cleanup)

**Does NOT own:**
- ❌ User profiles (user-service owns)
- ❌ Friendships (friendship-service owns)
- ❌ Chat/messages (chat-service owns)

### 1.4 Database Tables & Schema

**Primary Tables:**
```
accounts
├── id (UUID, PK)
├── email (VARCHAR 191, UNIQUE, NOT NULL)
├── passwordHash (VARCHAR)
├── enabled (BOOLEAN, NOT NULL, default true)
├── emailVerified (BOOLEAN, NOT NULL, default false)
├── createdAt (TIMESTAMP, NOT NULL, immutable)

refresh_tokens
├── id (UUID, PK)
├── accountId (UUID, FK -> accounts.id)
├── tokenHash (VARCHAR, NOT NULL)
├── expiresAt (TIMESTAMP, NOT NULL)

jwt_keys
├── id (UUID, PK)
├── (asymmetric key pair for JWT signing)
└── expiresAt (TIMESTAMP, for key rotation)

verification_tokens
├── id (UUID, PK)
├── accountId (UUID, FK -> accounts.id)
├── email (VARCHAR, nullable)
└── expiresAt (TIMESTAMP)

password_reset_tokens
├── id (UUID, PK)
├── accountId (UUID, FK -> accounts.id)
└── expiresAt (TIMESTAMP)
```

### 1.5 Kafka Events Published

| Event Type | Topic | Payload | When Published |
|------------|-------|---------|-----------------|
| `account.created` | `account.created` | `AccountCreatedPayload(accountId, email)` | After successful account registration |

**Evidence:** [AccountCreatedEventProducer.java](chatappBE/auth-service/src/main/java/com/example/auth/kafka/AccountCreatedEventProducer.java#L1)

```java
public boolean publish(Account account) {
    EventEnvelope<AccountCreatedPayload> envelope = new EventEnvelope<>(
        metadata,
        new AccountCreatedPayload(account.getId(), account.getEmail())
    );
    kafkaEventProducer.send(
        AccountEventType.ACCOUNT_CREATED.value(),
        account.getId().toString(),
        envelope
    );
}
```

### 1.6 Redis Interactions

**None.** Auth-service does NOT use Redis.

### 1.7 WebSocket Endpoints/Handlers

**None.** Auth-service is REST-only, no WebSocket.

### 1.8 Dependencies on Other Services

**Outbound (calls to other services):**
- ❌ No inter-service calls
- ✅ Kafka event producer (AccountCreated)

**Inbound (consumed by other services):**
- user-service: Consumes `account.created` → creates UserProfile
- notification-service: Consumes `account.created` → init mute settings

### 1.9 Key Classes

| Class | File | Responsibility |
|-------|------|-----------------|
| `AuthController` | `controller/AuthController.java` | REST API: /register, /login, /login/google, /refresh, /logout |
| `AuthService` | `service/impl/AuthService.java` | Orchestrates auth flows (register, login, token refresh) |
| `LocalAuthService` | `service/impl/LocalAuthService.java` | Email/password authentication |
| `OAuthAuthService` | `service/impl/OAuthAuthService.java` | Google OAuth integration |
| `TokenService` | `service/impl/TokenService.java` | JWT generation & signing |
| `TokenServiceFacade` | `service/impl/TokenServiceFacade.java` | Token refresh, logout, revocation |
| `KeyManager` | `jwt/impl/KeyManager.java` | Asymmetric key generation & rotation |
| `JwtVerifierService` | `jwt/impl/JwtVerifierService.java` | JWT validation for other services |
| `EmailService` | `service/impl/EmailService.java` | Email sending via Resend API |
| `GoogleTokenVerifier` | `service/impl/GoogleTokenVerifier.java` | Google ID token validation |
| `AccountCreatedEventProducer` | `kafka/AccountCreatedEventProducer.java` | Publishes ACCOUNT_CREATED event |

### 1.10 Configuration & Setup Patterns

- ✅ Uses **Spring Security OAuth2 Client** for Google OAuth
- ✅ Uses **JWT (io.jsonwebtoken)** for token generation
- ✅ Uses **Kafka** for event publishing (AccountCreated)
- ✅ Uses **Spring Data JPA** for data persistence
- ✅ Database: **PostgreSQL**
- ✅ Schedulers: Cleanup of expired tokens/keys via `@Scheduled`

### 1.11 Suspicious Code Patterns / Layering Violations

**None detected.** Architecture is clean:
- Clear service layer
- Repository pattern implemented
- Event-based async communication (not direct calls)
- Transactional consistency with `@Transactional`

---

## 2. USER-SERVICE

**File Location:** `chatappBE/user-service/src/main/java/com/example/user/`

### 2.1 Package Structure

```
user/
├── controller/
│   └── UserProfileController.java    # REST endpoints
├── service/
│   ├── IUserProfileService.java
│   └── impl/
│       └── UserProfileService.java   # Main user profile service
├── repository/
│   └── UserProfileRepository.java
├── entity/
│   └── UserProfile.java              # User profile entity
├── dto/
│   ├── UserProfileResponse.java
│   ├── UserBasicProfile.java
│   ├── UpdateProfileRequest.java
│   ├── AvatarUploadResponse.java
│   ├── AvatarMetadataRequest.java
│   └── AvatarAssetMetadata.java
├── kafka/
│   ├── KafkaConfiguration.java
│   ├── KafkaConsumerConfig.java
│   └── AccountCreatedConsumer.java
├── configuration/
│   ├── SwaggerConfig.java
│   ├── SecurityConfig.java
│   ├── RedisCacheConfig.java
│   ├── InternalServiceAuthFilter.java
│   └── DatabaseSchemaFixer.java
├── exception/
│   └── UserErrorCode.java
├── application/
│   └── UserKafkaAccountCreatedApplicationService.java
├── utils/
│   └── AvatarGenerator.java          # Random avatar generation
└── UserServiceApplication.java       # Main app class
```

### 2.2 Key Entities & Responsibilities

| Entity | Location | Purpose | Table |
|--------|----------|---------|-------|
| UserProfile | `entity/UserProfile.java` | User profile (display name, avatar, about me) | `user_profiles` |

### 2.3 Business Responsibility

**User-Service owns:**
- ✅ User profiles (display name, username, avatar, about me)
- ✅ Avatar storage metadata (public_id from Cloudinary)
- ✅ Profile updates
- ✅ User lookup/search by username

**Does NOT own:**
- ❌ Authentication (auth-service owns)
- ❌ Friendships (friendship-service owns)
- ❌ Chat/messages (chat-service owns)

### 2.4 Database Tables & Schema

**Primary Tables:**
```
user_profiles
├── accountId (UUID, PK, immutable, FK -> accounts.id)
├── username (VARCHAR 30, UNIQUE, NOT NULL)
├── displayName (VARCHAR 64)
├── avatarUrl (VARCHAR 255, NOT NULL)
├── avatarPublicId (VARCHAR, NOT NULL)       # Cloudinary public_id
├── aboutMe (VARCHAR 160)
├── backgroundColor (VARCHAR 7)              # Hex color for profile
├── createdAt (TIMESTAMP, NOT NULL, immutable)
└── updatedAt (TIMESTAMP, NOT NULL)

Indexes:
├── idx_username (on username)
```

### 2.5 Kafka Events Consumed

| Event Type | Topic | Consumer | Action |
|------------|-------|----------|--------|
| `account.created` | `account.created` | `AccountCreatedConsumer` | Create UserProfile with generated avatar |

**Evidence:** [AccountCreatedConsumer.java](chatappBE/user-service/src/main/java/com/example/user/kafka/AccountCreatedConsumer.java)

```java
@KafkaListener(topics = "account.created")
public void listen(EventEnvelope<AccountCreatedPayload> envelope) {
    var payload = envelope.payload();
    UUID accountId = payload.getAccountId();
    log.info("[USER] Received AccountCreated for {}", payload.getEmail());
    accountCreatedApplicationService.handleAccountCreated(payload);
}
```

### 2.6 Redis Interactions

**Cache configuration exists:** `RedisCacheConfig.java` - likely caches user profiles for read-heavy operations.

### 2.7 WebSocket Endpoints/Handlers

**None.** User-service is REST-only, no WebSocket.

### 2.8 Dependencies on Other Services

**Outbound:**
- ❌ No inter-service HTTP calls observed

**Inbound:**
- Chat-service, Friendship-service: May call to get user profiles via REST

### 2.9 Key Classes

| Class | File | Responsibility |
|-------|------|-----------------|
| `UserProfileController` | `controller/UserProfileController.java` | REST API: GET/PUT user profiles |
| `UserProfileService` | `service/impl/UserProfileService.java` | Profile CRUD, avatar management |
| `AccountCreatedConsumer` | `kafka/AccountCreatedConsumer.java` | Consume ACCOUNT_CREATED → create profile |
| `UserKafkaAccountCreatedApplicationService` | `application/UserKafkaAccountCreatedApplicationService.java` | Application service layer for event handling |
| `AvatarGenerator` | `utils/AvatarGenerator.java` | Random avatar generation at signup |

### 2.10 Configuration & Setup Patterns

- ✅ Uses **Spring Data JPA** for persistence
- ✅ Uses **Kafka** for event consumption
- ✅ Uses **Redis caching** for profile lookups
- ✅ Database: **PostgreSQL**
- ✅ **Application service layer** for event handling

### 2.11 Suspicious Code Patterns / Layering Violations

**None detected.**

---

## 3. CHAT-SERVICE

**File Location:** `chatappBE/chat-service/src/main/java/com/example/chat/`

### 3.1 Package Structure

```
chat/
├── modules/                          # Modular architecture (DDD)
│   ├── message/                      # Message aggregate root
│   │   ├── application/
│   │   │   ├── command/              # Command services
│   │   │   │   ├── IMessageCommandService.java
│   │   │   │   ├── IReactionCommandService.java
│   │   │   │   └── (impl/)
│   │   │   ├── query/                # Query services
│   │   │   │   ├── IMessageQueryService.java
│   │   │   │   ├── MessageQueryService.java
│   │   │   │   └── (repositories for queries)
│   │   │   ├── service/
│   │   │   │   └── ISystemMessageService.java
│   │   │   ├── port/
│   │   │   │   └── RoomPermissionService.java
│   │   │   ├── pipeline/             # Choreography pattern
│   │   │   │   ├── send/
│   │   │   │   │   ├── SendMessagePipeline.java
│   │   │   │   │   └── steps/
│   │   │   │   │       ├── GenerateSequenceStep.java
│   │   │   │   │       ├── ValidateMessageStep.java
│   │   │   │   │       ├── CreateMessageAggregateStep.java
│   │   │   │   │       ├── ExtractMentionStep.java
│   │   │   │   │       ├── PersistMentionStep.java
│   │   │   │   │       ├── MapAttachmentDraftStep.java
│   │   │   │   │       ├── PersistMessageStep.java
│   │   │   │   │       ├── PublishMessageEventStep.java
│   │   │   │   │       ├── ValidateRoomPermissionStep.java
│   │   │   │   │       └── CheckBlockedPairStep.java
│   │   │   │   ├── edit/
│   │   │   │   │   └── steps/
│   │   │   │   │       ├── AuthorizeEditMessageStep.java
│   │   │   │   │       ├── ApplyEditMessageStep.java
│   │   │   │   │       └── PublishEditEventStep.java
│   │   │   │   ├── reaction/
│   │   │   │   └── delete/
│   │   │   ├── dto/
│   │   │   │   └── request/ & response/
│   │   │   ├── dto/
│   │   │   │   ├── SendMessageRequest.java
│   │   │   │   ├── EditMessageRequest.java
│   │   │   │   ├── DeleteMessageRequest.java
│   │   │   │   └── MessageResponse.java
│   │   │   └── (other services)
│   │   ├── domain/
│   │   │   ├── entity/
│   │   │   │   ├── ChatMessage.java          # Message entity
│   │   │   │   ├── ChatAttachment.java       # Attachment entity
│   │   │   │   ├── ChatReaction.java         # Emoji reaction
│   │   │   │   ├── ChatMessageMention.java   # @mention tracking
│   │   │   │   └── RoomPinnedMessage.java    # Pinned messages
│   │   │   ├── model/
│   │   │   │   ├── MessageAggregate.java     # DDD aggregate root
│   │   │   │   └── AttachmentDraft.java
│   │   │   ├── enums/
│   │   │   │   ├── MessageType.java          # TEXT, ATTACHMENT, MIXED, SYSTEM
│   │   │   │   ├── AttachmentType.java       # IMAGE, VIDEO, FILE, etc.
│   │   │   │   ├── MessageBlockType.java
│   │   │   │   └── SystemEventType.java      # ROOM_CREATED, USER_JOINED, etc.
│   │   │   ├── event/
│   │   │   │   ├── MessageCreatedDomainEvent.java
│   │   │   │   ├── MessageUpdatedDomainEvent.java
│   │   │   │   └── MessageDeletedDomainEvent.java
│   │   │   ├── repository/
│   │   │   │   ├── ChatMessageRepository.java
│   │   │   │   ├── ChatReactionRepository.java
│   │   │   │   ├── ChatAttachmentRepository.java
│   │   │   │   ├── ChatMessageMentionRepository.java
│   │   │   │   ├── RoomPinnedMessageRepository.java
│   │   │   │   └── projection/               # Query projections
│   │   │   │       ├── ReactionCountProjection.java
│   │   │   │       ├── MessageReactionCountProjection.java
│   │   │   │       └── MessageReactionSummaryProjection.java
│   │   │   ├── service/
│   │   │   │   ├── IMessageSequenceService.java
│   │   │   │   ├── IMessagePreviewService.java
│   │   │   │   └── MentionParser.java
│   │   │   └── (other domain logic)
│   │   ├── infrastructure/
│   │   │   ├── redis/
│   │   │   │   ├── ChatRedisPublisher.java   # Redis event publisher
│   │   │   │   └── ChatMessageEventPublisherAdapter.java
│   │   │   ├── kafka/
│   │   │   │   └── KafkaConfiguration.java
│   │   │   ├── cache/
│   │   │   │   ├── MessageCacheService.java
│   │   │   │   └── CacheNames.java
│   │   │   ├── client/
│   │   │   │   ├── UserClient.java
│   │   │   │   ├── FriendshipClient.java
│   │   │   │   └── UserBasicProfile.java
│   │   │   ├── sequence/
│   │   │   │   └── RedisMessageSequenceService.java
│   │   │   └── service/
│   │   │       └── DefaultMessagePreviewService.java
│   │   ├── event/
│   │   │   ├── mapper/
│   │   │   │   ├── MessageTypeMapper.java
│   │   │   │   └── AttachmentTypeMapper.java
│   │   │   └── factory/
│   │   │       ├── ChatMessagePayloadFactory.java
│   │   │       ├── MessageUpdatedPayloadFactory.java
│   │   │       └── MessageDeletedPayloadFactory.java
│   │   └── controller/
│   │       ├── MessageCommandController.java # Send, edit, delete, forward
│   │       ├── MessageQueryController.java   # Get messages (paginated)
│   │       └── MessageReactionController.java# React to messages
│   │
│   └── room/                         # Room aggregate root
│       ├── service/
│       │   ├── IRoomService.java
│       │   ├── IRoomQueryService.java
│       │   ├── IRoomPinService.java
│       │   ├── IPrivateRoomService.java
│       │   ├── RoomMembershipGuard.java
│       │   └── (impl/)
│       ├── entity/
│       │   └── Room.java              # Room entity
│       ├── enums/
│       │   └── RoomType.java          # GROUP, PRIVATE, SYSTEM
│       ├── repository/
│       │   ├── RoomRepository.java
│       │   ├── RoomMemberRepository.java
│       │   ├── RoomBlockRepository.java
│       │   └── (other room repos)
│       ├── controller/
│       │   ├── RoomCommandController.java
│       │   └── RoomQueryController.java
│       └── (other room logic)
│
├── realtime/                         # Real-time event handling
│   ├── websocket/
│   │   ├── handler/
│   │   │   └── ChatWebSocketHandler.java    # WebSocket ingress
│   │   ├── session/
│   │   │   └── ChatSessionRegistry.java     # Session tracking
│   │   ├── broadcast/
│   │   │   ├── WebSocketUserBroadcaster.java
│   │   │   └── WebSocketRoomBroadcaster.java
│   │   ├── WsIncomingMessage.java
│   │   └── WsCommandType.java
│   ├── subscriber/                   # Redis event subscribers
│   │   ├── ChatMessageSentRedisSubscriber.java
│   │   ├── ChatMessageEditedRedisSubscriber.java
│   │   ├── ChatMessageDeletedRedisSubscriber.java
│   │   ├── ChatMessagePinnedRedisSubscriber.java
│   │   ├── ChatMessageUnpinnedRedisSubscriber.java
│   │   ├── ChatReactionUpdatedRedisSubscriber.java
│   │   ├── RealtimeEventDedupeGuard.java
│   │   └── (other subscribers)
│   ├── adapter/
│   │   ├── ChatCommandDispatcher.java        # Dispatch WS commands to pipeline
│   │   ├── ChatConnectionLifecycleAdapter.java
│   │   └── ChatRealtimeAdapter.java
│   ├── port/
│   │   └── ChatRealtimePort.java
│   ├── infrastructure/
│   │   └── ChatRealtimeAdapter.java
│   └── (other realtime logic)
│
├── config/
├── configuration/
│   ├── WebSocketConfig.java
│   ├── SecurityConfig.java
│   └── (other configs)
├── constants/
│   └── ChatRedisChannels.java        # Redis channel names
├── exception/
│   └── ChatErrorCode.java
├── ChatServiceApplication.java       # Main app class
└── (other top-level classes)
```

### 3.2 Key Entities & Responsibilities

| Entity | Location | Purpose | Table |
|--------|----------|---------|-------|
| **ChatMessage** | `modules/message/domain/entity/ChatMessage.java` | Message content, type, sender, seq | `chat_messages` |
| **ChatAttachment** | `modules/message/domain/entity/ChatAttachment.java` | Attachment metadata (URL, type, size) | `chat_attachments` |
| **ChatReaction** | `modules/message/domain/entity/ChatReaction.java` | Emoji reaction to message | `chat_reactions` |
| **ChatMessageMention** | `modules/message/domain/entity/ChatMessageMention.java` | @mention tracking | `chat_message_mentions` |
| **RoomPinnedMessage** | `modules/message/domain/entity/RoomPinnedMessage.java` | Pinned message tracking | `room_pinned_messages` |
| **Room** | `modules/room/entity/Room.java` | Chat room (group or private) | `rooms` |
| **MessageAggregate** | `modules/message/domain/model/MessageAggregate.java` | DDD aggregate (message + attachments) | N/A (domain model) |

### 3.3 Business Responsibility

**Chat-Service owns:**
- ✅ Chat messages (creation, editing, deletion)
- ✅ Chat rooms (creation, membership, settings)
- ✅ Attachments (metadata tracking, Cloudinary asset references)
- ✅ Message reactions (emoji reactions)
- ✅ Mentions (@user parsing and tracking)
- ✅ Pinned messages
- ✅ Message sequence/ordering (Redis-based seq generation)
- ✅ Private room support (1-on-1 DMs)
- ✅ Message preview generation

**Does NOT own:**
- ❌ User authentication (auth-service owns)
- ❌ User profiles (user-service owns)
- ❌ Friendships (friendship-service owns)
- ❌ Presence (presence-service owns)
- ❌ Notifications (notification-service owns)

### 3.4 Database Tables & Schema

**Primary Tables:**

```
chat_messages (main message store)
├── id (UUID, PK)
├── roomId (UUID, NOT NULL, FK -> rooms.id)
├── senderId (UUID, NOT NULL)
├── seq (BIGINT, NOT NULL) - per-room sequence number
├── clientMessageId (VARCHAR 100)
├── type (ENUM: TEXT, ATTACHMENT, MIXED, SYSTEM)
├── content (TEXT)
├── blocksJson (TEXT) - Editor.js blocks format
├── forwardedFromMessageId (UUID)
├── systemEventType (ENUM: ROOM_CREATED, USER_JOINED, etc.)
├── actorUserId (UUID) - for system events
├── targetMessageId (UUID)
├── replyToMessageId (UUID) - for threading/replies
├── createdAt (TIMESTAMP, NOT NULL, immutable)
├── editedAt (TIMESTAMP)
├── deleted (BOOLEAN, NOT NULL, default false)
├── deletedAt (TIMESTAMP)
├── deletedBy (UUID)

Unique Constraint: (roomId, seq)
Indexes:
├── idx_msg_room_created (roomId, createdAt)
├── idx_msg_room_id (roomId)
├── idx_msg_room_seq (roomId, seq)
├── idx_reply_to (replyToMessageId)
├── idx_message_sender (senderId)
├── idx_msg_client_id (clientMessageId)

chat_attachments
├── id (UUID, PK)
├── messageId (UUID, FK -> chat_messages.id)
├── type (ENUM: IMAGE, VIDEO, FILE, DOCUMENT, AUDIO)
├── url (VARCHAR)
├── publicId (VARCHAR) - Cloudinary public_id
├── size (BIGINT) - bytes
├── originalFilename (VARCHAR)
└── mimeType (VARCHAR)

chat_reactions
├── id (UUID, PK)
├── messageId (UUID, NOT NULL, FK -> chat_messages.id)
├── userId (UUID, NOT NULL)
├── emoji (VARCHAR, NOT NULL)
└── createdAt (TIMESTAMP, NOT NULL)

chat_message_mentions
├── id (UUID, PK)
├── messageId (UUID, FK -> chat_messages.id)
├── mentionedUserId (UUID)
└── (tracking @mentions)

room_pinned_messages
├── id (UUID, PK)
├── roomId (UUID, FK -> rooms.id)
├── messageId (UUID, FK -> chat_messages.id)
├── pinnedBy (UUID)
└── pinnedAt (TIMESTAMP)

rooms
├── id (UUID, PK)
├── name (VARCHAR)
├── avatarUrl (VARCHAR)
├── avatarPublicId (VARCHAR)
├── type (ENUM: GROUP, PRIVATE, SYSTEM)
├── createdBy (UUID, NOT NULL)
├── createdAt (TIMESTAMP, NOT NULL)
├── lastMessageId (UUID)
├── lastMessageSenderId (UUID)
├── lastMessageSenderName (VARCHAR)
├── lastMessagePreview (VARCHAR 200)
├── lastMessageAt (TIMESTAMP)
└── lastSeq (BIGINT)

Indexes:
├── idx_rooms_last_message_at
├── idx_rooms_created_at
├── idx_rooms_type

room_members
├── roomId (UUID, FK -> rooms.id)
├── userId (UUID)
├── (room membership)
```

### 3.5 Kafka Events Published

| Event Type | Topic | Payload | When |
|------------|-------|---------|------|
| `chat.message.sent` | `chat.message.sent` | `ChatMessagePayload` (roomId, senderId, content, etc.) | After message persistence |
| `chat.message.edited` | `chat.message.updated` | `MessageUpdatedPayload` | After message edit |
| `chat.message.deleted` | `chat.message.deleted` | `MessageDeletedPayload` | After message deletion |
| `chat.reaction.added` | `chat.reaction.updated` | `ChatReactionPayload` | After reaction added |
| `chat.message.pinned` | `chat.message.pinned` | `ChatMessagePinnedPayload` | After pinning |
| `chat.message.unpinned` | `chat.message.unpinned` | `ChatMessageUnpinnedPayload` | After unpinning |

**Evidence:** [ChatRedisPublisher.java](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/infrastructure/redis/ChatRedisPublisher.java)

```java
public void publishMessageSent(ChatMessagePayload payload) {
    publish(
        payload.getRoomId(),
        ChatEventType.MESSAGE_SENT.value(),
        payload
    );
}
```

### 3.6 Redis Topics Published/Subscribed

**Publishers:**
- ✅ Publishes `chat.message.sent`, `chat.message.edited`, `chat.message.deleted`, `chat.reaction.updated`, `chat.message.pinned`, `chat.message.unpinned` to Redis

**Subscribers:**
- ✅ `ChatMessageSentRedisSubscriber` → fan-out to room members and DM recipients
- ✅ `ChatMessageEditedRedisSubscriber` → broadcast edit events
- ✅ `ChatMessageDeletedRedisSubscriber` → broadcast delete events
- ✅ `ChatReactionUpdatedRedisSubscriber` → broadcast reaction updates
- ✅ `ChatMessagePinnedRedisSubscriber` → broadcast pin events
- ✅ `ChatMessageUnpinnedRedisSubscriber` → broadcast unpin events

**Evidence:** [ChatMessageSentRedisSubscriber.java](chatappBE/chat-service/src/main/java/com/example/chat/realtime/subscriber/ChatMessageSentRedisSubscriber.java)

```java
@Component
public class ChatMessageSentRedisSubscriber implements RedisEventSubscriber<ChatMessagePayload> {
    public void onEnvelope(EventEnvelope<ChatMessagePayload> envelope) {
        roomBroadcaster.sendToRoom(payload.getRoomId(), wsEvent);
        if (payload.isDirect()) {
            payload.getRecipientUserIds().forEach(userId ->
                userBroadcaster.sendToUser(userId, wsEvent));
        }
    }
}
```

### 3.7 WebSocket Endpoints/Handlers

**Service-Local WebSocket:** (NOT via realtime-edge)
- Endpoint: `/ws/chat` (legacy, being migrated)
- Handler: [ChatWebSocketHandler.java](chatappBE/chat-service/src/main/java/com/example/chat/realtime/websocket/handler/ChatWebSocketHandler.java)
- Purpose: Receive chat commands (send message, react, edit, delete)

**Evidence:**
```java
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionRegistry.register(session);
        lifecycleAdapter.onConnectionEstablished(session);
    }
    
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        commandDispatcher.dispatch(parsedMessage);
    }
}
```

### 3.8 Pipeline Architecture (Important!)

Chat uses a **choreography-based pipeline pattern** for message sending:

**SendMessagePipeline execution flow:**

1. **GenerateSequenceStep** → Get next seq# from Redis
2. **ValidateMessageStep** → Check content length, format
3. **CreateMessageAggregateStep** → Build MessageAggregate (domain model)
4. **ExtractMentionStep** → Parse @mentions from content
5. **PersistMentionStep** → Save mentions to DB
6. **MapAttachmentDraftStep** → Map drafts to attachments
7. **CheckBlockedPairStep** → Verify sender not blocked
8. **ValidateRoomPermissionStep** → Verify room access
9. **PersistMessageStep** → Save to DB
10. **PublishMessageEventStep** → Publish to Redis/Kafka

**Evidence:** [SendMessagePipeline.java](chatappBE/chat-service/src/main/java/com/example/chat/modules/message/application/pipeline/send/SendMessagePipeline.java)

### 3.9 Dependencies on Other Services

**Outbound (HTTP/REST calls via Feign):**
- ✅ Calls **user-service** to get user profiles
- ✅ Calls **friendship-service** to check block status
- ❌ Does NOT call auth-service, presence-service, notification-service

**Inbound (Kafka events consumed):**
- ❌ Does NOT consume any Kafka events (only publishes)

### 3.10 Key Classes

| Class | File | Responsibility |
|-------|------|-----------------|
| `MessageAggregate` | `modules/message/domain/model/MessageAggregate.java` | DDD aggregate root for message + attachments |
| `SendMessagePipeline` | `modules/message/application/pipeline/send/SendMessagePipeline.java` | Choreography pipeline for sending messages |
| `ChatWebSocketHandler` | `realtime/websocket/handler/ChatWebSocketHandler.java` | WebSocket ingress for chat commands |
| `ChatMessageSentRedisSubscriber` | `realtime/subscriber/ChatMessageSentRedisSubscriber.java` | Real-time message fan-out to clients |
| `ChatCommandDispatcher` | `realtime/adapter/ChatCommandDispatcher.java` | Route WS commands to domain services |
| `MessageCommandController` | `modules/message/controller/MessageCommandController.java` | REST API for message operations |
| `MessageQueryService` | `modules/message/application/query/MessageQueryService.java` | Paginated message queries |
| `ChatRedisPublisher` | `modules/message/infrastructure/redis/ChatRedisPublisher.java` | Redis event publishing |

### 3.11 Configuration & Setup Patterns

- ✅ **DDD** with aggregate roots (Message, Room)
- ✅ **Pipeline pattern** for choreography-based message sending
- ✅ **Ports & adapters** for realtime layer (`ChatRealtimePort`)
- ✅ **Redis** for message sequence generation
- ✅ **Kafka** for inter-service events
- ✅ **Feign clients** for calling user-service and friendship-service
- ✅ **Spring Data JPA** with custom repositories

### 3.12 Suspicious Code Patterns / Layering Violations

**None detected.** Clean architecture:
- Domain logic encapsulated in aggregates
- Application services orchestrate use cases
- Infrastructure layer clearly separated
- Real-time event handling via adapters

---

## 4. PRESENCE-SERVICE

**File Location:** `chatappBE/presence-service/src/main/java/com/example/presence/`

### 4.1 Package Structure

```
presence/
├── websocket/
│   ├── handler/
│   │   └── PresenceWebSocketHandler.java    # WebSocket ingress
│   ├── session/
│   │   ├── PresenceSessionRegistry.java     # Session tracking
│   │   └── IPresenceQuery.java
│   ├── broadcaster/
│   │   ├── WebSocketUserBroadcaster.java
│   │   ├── WebSocketRoomBroadcaster.java
│   │   └── WebSocketGlobalBroadcaster.java
│   ├── adapter/
│   │   └── PresenceConnectionLifecycleAdapter.java
│   └── (other websocket logic)
├── service/
│   ├── IPresenceService.java
│   ├── PresenceService.java                # Core presence logic
│   └── model/
│       └── StoredPresenceState.java        # Presence state model
├── state/                               # State management
│   ├── port/
│   │   ├── PresenceEphemeralStatePort.java
│   │   └── PresenceTtlCachePort.java
│   ├── redis/
│   │   ├── RedisPresenceEphemeralStateStore.java
│   │   └── RedisPresenceTtlCacheAdapter.java
│   └── (state stores)
├── redis/                               # Redis event handling
│   ├── PresenceRedisPublisher.java      # Redis event publisher
│   ├── UserOnlineSubscriber.java        # USER_ONLINE event
│   ├── UserOfflineSubscriber.java       # USER_OFFLINE event
│   ├── UserStatusChangedSubscriber.java # Status changed (online→away)
│   ├── UserTypingSubscriber.java        # User typing indicator
│   ├── UserStopTypingSubscriber.java    # Stop typing
│   ├── RoomJoinSubscriber.java          # User joined room
│   ├── RoomLeaveSubscriber.java         # User left room
│   ├── RoomOnlineUsersSubscriber.java   # Room members online
│   ├── PresenceKeyExpiredListener.java  # Redis expiry handler (TTL)
│   └── (other subscribers)
├── realtime/
│   └── port/
│       └── PresenceRealtimePort.java    # Port for publishing realtime events
├── controller/
│   ├── PresenceController.java          # REST API
│   ├── PresenceEdgeCommandController.java# Commands from realtime-edge
│   └── (controllers)
├── dto/
│   ├── UpdatePresenceStatusRequest.java
│   ├── PresenceWsCommand.java
│   ├── PresenceSelfResponse.java
│   ├── PresenceHeartbeatCommandRequest.java
│   └── HeartbeatPayload.java
├── configuration/
│   ├── WebSocketConfig.java
│   ├── PresenceRedisRegistryConfig.java
│   ├── PresenceRedisListenerConfig.java # Keyspace notifications for TTL
│   ├── PresenceRedisKeyspaceNotificationStartupCheck.java
│   ├── SecurityConfig.java
│   ├── RedisCacheConfig.java
│   └── (configs)
├── constants/
│   └── PresenceRedisChannels.java       # Redis channel names
├── authorization/
│   ├── RoomAuthorizationService.java
│   └── ChatRoomAuthorizationService.java
├── exception/
│   └── (error codes)
├── PresenceServiceApplication.java      # Main app class
└── (other classes)
```

### 4.2 Key Entities & Responsibilities

| Entity | Location | Purpose | Storage |
|--------|----------|---------|---------|
| **StoredPresenceState** | `service/model/StoredPresenceState.java` | User presence state (online/away/offline, mode) | Redis TTL cache |
| N/A | N/A | **Ephemeral state** (connection counts, online user sets) | Redis (ephemeral, no persistence) |

### 4.3 Business Responsibility

**Presence-Service owns:**
- ✅ User online/offline status
- ✅ User presence mode (AUTO, MANUAL, DO_NOT_DISTURB)
- ✅ Manual status (ONLINE, AWAY, INVISIBLE, DO_NOT_DISTURB)
- ✅ Active/away status (based on heartbeat)
- ✅ Room join/leave tracking
- ✅ Global online users list
- ✅ Room members online list
- ✅ Typing indicators (user is typing)
- ✅ Presence state persistence (TTL-based)

**Does NOT own:**
- ❌ User authentication (auth-service)
- ❌ User profiles (user-service)
- ❌ Chat/messages (chat-service)
- ❌ Friendships (friendship-service)

### 4.4 Database Tables & Schema

**Primary Storage: Redis (No PostgreSQL)**

```
Redis Data Structures:

1. TTL Cache (automatic expiry):
   Key: presence:user:{userId}
   Type: Hash
   Fields:
   ├── mode (AUTO | MANUAL)
   ├── manualStatus (ONLINE | AWAY | INVISIBLE | DND)
   ├── active (true | false)
   TTL: Configured (typically hours)
   
2. Ephemeral State (no persistence):
   Key: presence:connections:{userId}
   Type: String (counter)
   Value: Connection count (incremented per WS connection)
   
   Key: presence:online_users
   Type: Set
   Members: {userId} (all online users)
   
   Key: presence:room:{roomId}:online_users
   Type: Set
   Members: {userId} (users online in room)
   
3. Event Channels (pub/sub):
   ├── presence:global          (global presence events)
   ├── presence:user            (user-level events)
   ├── presence:room:{roomId}   (room-level events)
   └── (other channels)
```

**NO Database:** Presence-service stores everything in Redis.

### 4.5 Redis Topics Published

| Event Type | Channel | Payload | When |
|------------|---------|---------|------|
| `presence.user.online` | `presence:global` | `PresenceUserOnlinePayload` | First connection |
| `presence.user.offline` | `presence:global` | `PresenceUserOfflinePayload` | Last connection closes |
| `presence.user.status.changed` | `presence:global` | `PresenceUserStatePayload` | Status changes (online→away) |
| `presence.user.typing` | `presence:room:{roomId}` | `PresenceTypingPayload` | User starts typing |
| `presence.user.stop.typing` | `presence:room:{roomId}` | `PresenceStopTypingPayload` | User stops typing |
| `presence.room.user.joined` | `presence:room:{roomId}` | `PresenceRoomJoinPayload` | User joins room |
| `presence.room.user.left` | `presence:room:{roomId}` | `PresenceRoomLeavePayload` | User leaves room |
| `presence.room.online.users` | `presence:room:{roomId}` | `RoomOnlineUsersPayload` | Room members online |

**Evidence:** [PresenceRedisPublisher.java](chatappBE/presence-service/src/main/java/com/example/presence/redis/PresenceRedisPublisher.java)

```java
@Override
public void publishUserEvent(String eventType, Object payload) {
    redisPublisher.publish(userChannel(), msg(eventType, payload));
}
```

### 4.6 Redis Topics Subscribed

- ✅ Multiple Redis subscribers for own events (user online, offline, typing, etc.)
- ✅ `PresenceKeyExpiredListener` → Handles Redis TTL expiry for user presence

### 4.7 WebSocket Endpoints/Handlers

**Service-Local WebSocket:**
- Endpoint: `/ws/presence`
- Handler: [PresenceWebSocketHandler.java](chatappBE/presence-service/src/main/java/com/example/presence/websocket/handler/PresenceWebSocketHandler.java)
- Purpose: Receive presence commands (heartbeat, status changes, room join/leave, typing)

**Evidence:**
```java
@Component
public class PresenceWebSocketHandler extends TextWebSocketHandler {
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionRegistry.register(session);
        lifecycleAdapter.onConnectionEstablished(session);
        // Send global presence snapshot
        userBroadcaster.sendToUser(userId, 
            RealtimeWsEvent.builder()
                .type("presence.global.snapshot")
                .payload(GlobalOnlineUsersPayload.builder()
                    .users(presenceService.getAllPresenceUsers())
                    .build())
                .build());
    }
}
```

### 4.8 Dependencies on Other Services

**Outbound:**
- ❌ No inter-service HTTP calls

**Inbound:**
- Realtime-edge-service: Routes presence commands to this service

### 4.9 Key Classes

| Class | File | Responsibility |
|-------|------|-----------------|
| `PresenceService` | `service/PresenceService.java` | Core presence logic (online, offline, heartbeat, etc.) |
| `PresenceWebSocketHandler` | `websocket/handler/PresenceWebSocketHandler.java` | WebSocket ingress |
| `PresenceRedisPublisher` | `redis/PresenceRedisPublisher.java` | Redis event publishing |
| `PresenceConnectionLifecycleAdapter` | `websocket/adapter/PresenceConnectionLifecycleAdapter.java` | Translate WS events to presence events |
| `RedisPresenceTtlCacheAdapter` | `state/redis/RedisPresenceTtlCacheAdapter.java` | Redis TTL cache for presence state |
| `PresenceKeyExpiredListener` | `redis/PresenceKeyExpiredListener.java` | Handle Redis key expiry (user offline by TTL) |

### 4.10 Configuration & Setup Patterns

- ✅ **Redis-first architecture** (no database)
- ✅ **Redis keyspace notifications** for TTL expiry handling
- ✅ **Redis pub/sub** for real-time events
- ✅ **TTL-based presence** (automatic offline if no heartbeat)
- ✅ **Connection counting** (support multiple WS connections per user)
- ✅ **Broadcaster pattern** for WebSocket push

### 4.11 Suspicious Code Patterns / Layering Violations

**None detected.**

---

## 5. NOTIFICATION-SERVICE

**File Location:** `chatappBE/notification-service/src/main/java/com/example/notification/`

### 5.1 Package Structure

```
notification/
├── websocket/
│   ├── NotificationWebSocketHandler.java    # WebSocket ingress
│   ├── NotificationSessionRegistry.java     # Session tracking
│   ├── NotificationWebSocketPublisher.java  # WS message pushing
│   ├── WebSocketUserBroadcaster.java
│   ├── redis/
│   │   ├── RedisNotificationSubscriber.java
│   │   └── RedisNotificationPublisher.java
│   └── (other websocket logic)
├── service/
│   ├── impl/
│   │   ├── NotificationCommandService.java  # Notification CRUD
│   │   ├── NotificationQueryService.java    # Notification queries
│   │   ├── NotificationDomainService.java   # Domain logic
│   │   ├── NotificationPushService.java     # Push to clients
│   │   ├── RoomMuteSettingService.java      # Mute logic
│   │   └── NotificationModePolicy.java      # Notification mode policies
│   ├── INotificationCommandService.java
│   ├── (other service interfaces)
│   └── (other services)
├── controller/
│   ├── NotificationController.java          # REST API
│   ├── NotificationRealtimeCommandController.java
│   ├── RoomMuteController.java
│   └── (controllers)
├── entity/
│   ├── Notification.java                # Notification entity
│   ├── RoomMuteSetting.java             # Mute setting
│   ├── RoomMuteSettingId.java           # Composite key
│   ├── NotificationType.java            # FRIEND_REQUEST, MENTION, etc.
│   └── RoomNotificationMode.java        # Notification mode
├── dto/
│   ├── NotificationResponse.java
│   ├── NotificationListResponse.java
│   ├── UnreadCountResponse.java
│   ├── RoomSettingsUpdateRequest.java
│   ├── RoomSettingsResponse.java
│   ├── NotificationRealtimeCommandRequest.java
│   └── (other DTOs)
├── repository/
│   ├── NotificationRepository.java      # Notification queries
│   └── RoomMuteSettingRepository.java   # Mute settings queries
├── kafka/
│   ├── AccountCreatedEventConsumer.java        # ACCOUNT_CREATED
│   ├── MessageCreatedEventConsumer.java        # MESSAGE_SENT (for @mentions)
│   ├── FriendRequestEventConsumer.java         # FRIEND_REQUEST_*
│   ├── ReactionEventConsumer.java             # REACTION_* (optional)
│   ├── NotificationEventProducer.java          # Produce NOTIFICATION_REQUESTED
│   ├── NotificationEventDedupeGuard.java      # Dedup notifications
│   ├── KafkaConfiguration.java
│   └── KafkaConsumerConfig.java
├── application/
│   ├── NotificationKafkaEventApplicationService.java
│   ├── NotificationReactionEventApplicationService.java
│   ├── NotificationMessageEventApplicationService.java
│   ├── NotificationFriendRequestEventApplicationService.java
│   └── (other application services)
├── realtime/
│   ├── port/
│   │   └── NotificationRealtimePort.java
│   └── NotificationRealtimeEventTypes.java
├── constants/
│   └── NotificationRedisChannels.java   # Redis channel names
├── configuration/
│   ├── WebSocketConfig.java
│   ├── NotificationRedisListenerConfig.java
│   ├── LocalValidationJwtDecoderConfig.java
│   ├── KafkaProducerAdapterConfig.java
│   ├── SecurityConfig.java
│   └── (configs)
├── exception/
│   └── (error codes)
├── NotificationServiceApplication.java  # Main app class
└── (other classes)
```

### 5.2 Key Entities & Responsibilities

| Entity | Location | Purpose | Table |
|--------|----------|---------|-------|
| **Notification** | `entity/Notification.java` | Notification record (friend request, mention, etc.) | `notifications` |
| **RoomMuteSetting** | `entity/RoomMuteSetting.java` | Mute settings per room per user | `room_mute_settings` |
| **NotificationType** | `entity/NotificationType.java` | Enum: FRIEND_REQUEST, MENTION, REACTION, etc. | (enum) |
| **RoomNotificationMode** | `entity/RoomNotificationMode.java` | Notification mode per room | (enum) |

### 5.3 Business Responsibility

**Notification-Service owns:**
- ✅ Notification creation/storage (friend requests, mentions, reactions, etc.)
- ✅ Notification queries (list, unread count)
- ✅ Notification marking as read
- ✅ Room mute settings (user can mute/unmute per room)
- ✅ Notification modes per room (ALL_MESSAGES, MENTIONS_ONLY, MUTED)
- ✅ Notification delivery to clients via WebSocket

**Does NOT own:**
- ❌ Friend relationships (friendship-service)
- ❌ Chat messages (chat-service)
- ❌ User authentication (auth-service)

### 5.4 Database Tables & Schema

**Primary Tables:**

```
notifications
├── id (UUID, PK)
├── userId (UUID, NOT NULL) - recipient
├── type (ENUM: FRIEND_REQUEST, MENTION, REACTION, etc.)
├── referenceId (UUID) - ID of referenced entity
├── roomId (UUID)
├── actorId (UUID) - user who triggered notification
├── actorDisplayName (VARCHAR)
├── senderName (VARCHAR)
├── preview (VARCHAR 1000)
├── isRead (BOOLEAN)
├── actionRequired (BOOLEAN) - for FRIEND_REQUEST to preserve in read-all
├── createdAt (TIMESTAMP)

room_mute_settings
├── roomId (UUID, composite PK)
├── userId (UUID, composite PK)
├── mode (ENUM: ALL_MESSAGES, MENTIONS_ONLY, MUTED)
├── createdAt (TIMESTAMP)
└── updatedAt (TIMESTAMP)
```

### 5.5 Kafka Events Consumed

| Event Type | Topic | Consumer | Action |
|------------|-------|----------|--------|
| `account.created` | `account.created` | `AccountCreatedEventConsumer` | Initialize mute settings for new user |
| `chat.message.sent` | `chat.message.sent` | `MessageCreatedEventConsumer` | Check for @mentions, create notification |
| `friendship.request.sent` | `friendship.request.events` | `FriendRequestEventConsumer` | Create FRIEND_REQUEST notification |
| `friendship.request.accepted` | `friendship.request.events` | `FriendRequestEventConsumer` | Create FRIEND_REQUEST_ACCEPTED notification |
| `friendship.request.declined` | `friendship.request.events` | `FriendRequestEventConsumer` | Create FRIEND_REQUEST_DECLINED notification |
| `chat.reaction.updated` | `chat.reaction.updated` | `ReactionEventConsumer` | Create REACTION notification (optional) |

**Evidence:** [MessageCreatedEventConsumer.java](chatappBE/notification-service/src/main/java/com/example/notification/kafka/MessageCreatedEventConsumer.java)

```java
@KafkaListener(topics = "chat.message.sent", groupId = "notification-service")
public void listen(EventEnvelope<ChatMessagePayload> envelope) {
    applicationService.handleChatMessageSentEvent(
        envelope.metadata().getEventId(),
        envelope.payload()
    );
}
```

### 5.6 Kafka Events Published

| Event Type | Topic | Payload | When |
|------------|-------|---------|------|
| `notification.requested` | `notification.requested` | `NotificationRequestedPayload` | After notification created |

**Evidence:** [NotificationEventProducer.java](chatappBE/notification-service/src/main/java/com/example/notification/kafka/NotificationEventProducer.java)

### 5.7 Redis Topics Published

| Event Type | Channel | Payload | When |
|------------|---------|---------|------|
| `notification.requested` | `notification.{userId}` | `NotificationRequestedPayload` | Notification created |
| (other notification events) | (other channels) | (payloads) | (various) |

### 5.8 WebSocket Endpoints/Handlers

**Service-Local WebSocket:**
- Endpoint: `/ws/notifications`
- Handler: [NotificationWebSocketHandler.java](chatappBE/notification-service/src/main/java/com/example/notification/websocket/NotificationWebSocketHandler.java)
- Purpose: Push notifications to connected clients

**Evidence:**
```java
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionRegistry.register(session);
        activeConnections.incrementAndGet();
        UUID userId = sessionRegistry.getUserId(session);
        log.info("[NOTI] CONNECT user={} session={}", userId, session.getId());
    }
}
```

### 5.9 Dependencies on Other Services

**Outbound:**
- ❌ No inter-service HTTP calls

**Inbound:**
- Chat-service: Sends message.sent event (for @mentions)
- Friendship-service: Sends friend request events
- Realtime-edge-service: Routes notification commands

### 5.10 Key Classes

| Class | File | Responsibility |
|-------|------|-----------------|
| `NotificationCommandService` | `service/impl/NotificationCommandService.java` | Create/update notifications |
| `NotificationQueryService` | `service/impl/NotificationQueryService.java` | Query notifications |
| `NotificationDomainService` | `service/impl/NotificationDomainService.java` | Domain logic |
| `NotificationWebSocketHandler` | `websocket/NotificationWebSocketHandler.java` | WebSocket ingress |
| `NotificationWebSocketPublisher` | `websocket/NotificationWebSocketPublisher.java` | Push to WebSocket |
| `MessageCreatedEventConsumer` | `kafka/MessageCreatedEventConsumer.java` | Consume MESSAGE_SENT for @mentions |
| `FriendRequestEventConsumer` | `kafka/FriendRequestEventConsumer.java` | Consume friend request events |
| `NotificationEventProducer` | `kafka/NotificationEventProducer.java` | Produce NOTIFICATION_REQUESTED |

### 5.11 Configuration & Setup Patterns

- ✅ **Kafka consumers** for multiple event types
- ✅ **Application service layer** for event handling
- ✅ **Domain service layer** for business logic
- ✅ **Deduplication guard** for Kafka consumer idempotency
- ✅ **WebSocket push** for real-time notifications

### 5.12 Suspicious Code Patterns / Layering Violations

**None detected.**

---

## 6. FRIENDSHIP-SERVICE

**File Location:** `chatappBE/friendship-service/src/main/java/com/example/friendship/`

### 6.1 Package Structure

```
friendship/
├── websocket/
│   ├── FriendshipWebSocketHandler.java      # WebSocket ingress
│   ├── FriendshipSessionRegistry.java       # Session tracking
│   └── (other websocket logic)
├── service/
│   ├── IFriendCommandService.java
│   ├── IFriendQueryService.java
│   ├── impl/
│   │   ├── FriendCommandService.java        # Friend requests, accept, decline
│   │   └── FriendQueryService.java          # Friend list queries
│   └── (other services)
├── controller/
│   ├── FriendController.java                # Public API
│   ├── InternalFriendController.java        # Internal API for other services
│   ├── FriendshipRealtimeCommandController.java
│   └── (controllers)
├── entity/
│   └── Friendship.java                      # Friendship relationship
├── enums/
│   └── FriendshipStatus.java                # PENDING, ACCEPTED, BLOCKED
├── dto/
│   ├── UserProfileResponse.java
│   ├── UpdateProfileRequest.java
│   ├── UnreadCountResponse.java
│   ├── SendFriendRequestByUsernameRequest.java
│   ├── FriendshipRealtimeCommandRequest.java
│   └── (other DTOs)
├── repository/
│   └── FriendshipRepository.java            # Friendship queries
├── kafka/
│   ├── FriendshipEventProducer.java         # Produce friendship events
│   ├── FriendshipEventDedupeGuard.java      # Dedup producer
│   └── (other Kafka classes)
├── configuration/
│   ├── FriendshipWebSocketConfig.java
│   ├── SecurityConfig.java
│   ├── SwaggerConfig.java
│   ├── KafkaProducerAdapterConfig.java
│   ├── JacksonConfig.java
│   ├── InternalServiceAuthFilter.java
│   └── (configs)
├── client/
│   └── UserClient.java                      # Call user-service for profiles
├── FriendshipServiceApplication.java        # Main app class
└── (other classes)
```

### 6.2 Key Entities & Responsibilities

| Entity | Location | Purpose | Table |
|--------|----------|---------|-------|
| **Friendship** | `entity/Friendship.java` | Friendship relationship (pending, accepted, blocked) | `friendships` |

### 6.3 Business Responsibility

**Friendship-Service owns:**
- ✅ Friendship relationships (PENDING, ACCEPTED, BLOCKED states)
- ✅ Friend requests (send, accept, decline, cancel)
- ✅ Friend list queries
- ✅ Block/unblock users
- ✅ Friendship status checks

**Does NOT own:**
- ❌ User profiles (user-service)
- ❌ Authentication (auth-service)
- ❌ Chat/messages (chat-service)
- ❌ Notifications (notification-service)

### 6.4 Database Tables & Schema

**Primary Tables:**

```
friendships
├── id (UUID, PK)
├── userLow (UUID, NOT NULL) - lesser UUID value
├── userHigh (UUID, NOT NULL) - greater UUID value
├── status (ENUM: PENDING, ACCEPTED, BLOCKED)
├── actionUserId (UUID, NOT NULL) - who took action
├── createdAt (TIMESTAMP, NOT NULL, immutable)
└── updatedAt (TIMESTAMP, NOT NULL)

Unique Constraint: (userLow, userHigh)
Indexes:
├── idx_friend_user_low (userLow)
├── idx_friend_user_high (userHigh)
└── idx_friend_status (status)
```

### 6.5 Kafka Events Published

| Event Type | Topic | Payload | When |
|------------|-------|---------|------|
| `friendship.request.sent` | `friendship.request.events` | `FriendRequestPayload` | Friend request sent |
| `friendship.request.accepted` | `friendship.request.events` | `FriendRequestPayload` | Friend request accepted |
| `friendship.request.declined` | `friendship.request.events` | `FriendRequestPayload` | Friend request declined |
| `friendship.request.cancelled` | `friendship.request.events` | `FriendRequestPayload` | Friend request cancelled |
| `friendship.status.changed` | `friendship.events` | `FriendshipPayload` | Friendship status changed |

**Evidence:** [FriendshipEventProducer.java](chatappBE/friendship-service/src/main/java/com/example/friendship/kafka/FriendshipEventProducer.java)

### 6.6 Redis Topics Published

**None observed.** Friendship events go via Kafka.

### 6.7 WebSocket Endpoints/Handlers

**Service-Local WebSocket:**
- Endpoint: `/ws/friendship`
- Handler: [FriendshipWebSocketHandler.java](chatappBE/friendship-service/src/main/java/com/example/friendship/websocket/FriendshipWebSocketHandler.java)
- Purpose: Real-time friendship status updates

**Evidence:**
```java
@Component
public class FriendshipWebSocketHandler extends TextWebSocketHandler {
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionRegistry.register(session);
        UUID userId = sessionRegistry.getUserId(session);
        log.info("[FRIEND-WS] ✅ CONNECT success - userId={} sessionId={}", userId, session.getId());
    }
}
```

### 6.8 Dependencies on Other Services

**Outbound:**
- ✅ Calls **user-service** to get user profiles (Feign client `UserClient`)

**Inbound:**
- Chat-service: May call internal API to check block status
- Notification-service: Consumes friendship events

### 6.9 Key Classes

| Class | File | Responsibility |
|-------|------|-----------------|
| `FriendCommandService` | `service/impl/FriendCommandService.java` | Friend request/accept/decline logic |
| `FriendQueryService` | `service/impl/FriendQueryService.java` | Friend list queries |
| `FriendshipWebSocketHandler` | `websocket/FriendshipWebSocketHandler.java` | WebSocket ingress |
| `FriendshipEventProducer` | `kafka/FriendshipEventProducer.java` | Publish friendship events |
| `UserClient` | `client/UserClient.java` | Call user-service for profiles |

### 6.10 Configuration & Setup Patterns

- ✅ **Feign client** for calling user-service
- ✅ **Kafka producer** for friendship events
- ✅ **Deduplication guard** for event idempotency
- ✅ **WebSocket** for real-time updates

### 6.11 Suspicious Code Patterns / Layering Violations

**None detected.**

---

## 7. UPLOAD-SERVICE

**File Location:** `chatappBE/upload-service/src/main/java/com/example/upload/`

### 7.1 Package Structure

```
upload/
├── controller/
│   └── UploadController.java                # REST API
├── service/
│   ├── UploadSigningService.java            # Signing service
│   ├── UploadPolicyRegistry.java            # Policy lookup
│   └── (other services)
├── domain/
│   ├── UploadPurpose.java                   # Enum: PROFILE_AVATAR, CHAT_ATTACHMENT, etc.
│   └── UploadPolicy.java                    # Policy config per purpose
├── dto/
│   ├── PrepareUploadRequest.java
│   ├── PrepareUploadResponse.java
│   ├── ConfirmUploadRequest.java
│   ├── UploadAssetResponse.java
│   ├── UploadPurposeDeserializer.java
│   └── (other DTOs)
├── application/
│   ├── PrepareUploadCommand.java
│   ├── PrepareUploadResult.java
│   ├── ConfirmUploadCommand.java
│   ├── ConfirmUploadResult.java
│   └── (other app classes)
├── contract/
│   └── UploadAssetMetadata.java             # Shared contract
├── config/
│   ├── CloudinaryConfig.java                # Cloudinary client config
│   ├── UploadPolicyProperties.java          # Policies from properties
│   └── SecurityConfig.java
├── UploadServiceApplication.java            # Main app class
└── (other classes)
```

### 7.2 Key Entities & Responsibilities

| Entity | Location | Purpose | Storage |
|--------|----------|---------|---------|
| **UploadPurpose** | `domain/UploadPurpose.java` | Enum: PROFILE_AVATAR, CHAT_ATTACHMENT, ROOM_AVATAR | (enum/config) |
| **UploadPolicy** | `domain/UploadPolicy.java` | Policy config (max size, allowed formats, folder) | (config/properties) |

### 7.3 Business Responsibility

**Upload-Service owns:**
- ✅ Upload preparation (generate signed URLs for Cloudinary)
- ✅ Upload confirmation (verify asset uploaded to Cloudinary)
- ✅ Upload policy validation (max size, format restrictions)
- ✅ Asset metadata (public_id, URL generation)

**Does NOT own:**
- ❌ Actual file storage (Cloudinary owns)
- ❌ User profiles (user-service)
- ❌ Chat attachments storage (only metadata)

### 7.4 Database Tables & Schema

**Primary Storage: None.** Upload-service is **stateless**.
- Stores **no data** in database
- All asset metadata stored by calling services (user-service, chat-service)

### 7.5 External Dependencies

- ✅ **Cloudinary** (external asset storage provider)

### 7.6 Key Classes

| Class | File | Responsibility |
|-------|------|-----------------|
| `UploadSigningService` | `service/UploadSigningService.java` | Generate signed URLs for uploads |
| `UploadPolicyRegistry` | `service/UploadPolicyRegistry.java` | Get policy by purpose |
| `UploadController` | `controller/UploadController.java` | REST API for prepare/confirm |

### 7.7 Configuration & Setup Patterns

- ✅ **Cloudinary SDK** for asset operations
- ✅ **HMAC-SHA256 signing** for upload token generation
- ✅ **Pluggable policies** per upload purpose
- ✅ **Stateless design** (no database)

### 7.8 Suspicious Code Patterns / Layering Violations

**None detected.** Clean wrapper around Cloudinary.

---

## 8. GATEWAY-SERVICE

**File Location:** `chatappBE/gateway-service/src/main/java/com/example/gateway/`

### 8.1 Package Structure

```
gateway/
├── config/
│   ├── GatewayConfig.java                   # Route config, CORS, rate limit
│   └── SecurityConfig.java
├── filter/
│   └── JwtAuthFilterGatewayFilterFactory.java  # JWT validation filter
├── controller/
│   └── FallbackController.java              # Fallback responses
├── health/
│   ├── GatewayReadinessProperties.java      # Health config
│   └── DownstreamReadinessIndicator.java    # Liveness probe
├── GatewayApplication.java                  # Main app class
└── (other classes)
```

### 8.2 Key Entities & Responsibilities

**NONE.** Gateway-service is a **thin routing layer**.

### 8.3 Business Responsibility

**Gateway-Service owns:**
- ✅ API routing (Spring Cloud Gateway)
- ✅ CORS handling
- ✅ JWT validation
- ✅ Rate limiting
- ✅ Request/response transformations
- ✅ Health checks for downstream services

**Does NOT own:**
- ❌ Any business logic
- ❌ Any data

### 8.4 Database Tables & Schema

**None.**

### 8.5 Configuration

Uses **Spring Cloud Gateway** with routes defined in `application.yaml`:
```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: lb://auth-service
          predicates:
            - Path=/api/v1/auth/**
        - id: user-service
          uri: lb://user-service
          predicates:
            - Path=/api/v1/users/**
        # ... other routes
```

### 8.6 Key Classes

| Class | File | Responsibility |
|-------|------|-----------------|
| `GatewayConfig` | `config/GatewayConfig.java` | CORS, rate limiting config |
| `JwtAuthFilterGatewayFilterFactory` | `filter/JwtAuthFilterGatewayFilterFactory.java` | JWT validation before routing |
| `DownstreamReadinessIndicator` | `health/DownstreamReadinessIndicator.java` | Liveness probe |

### 8.7 Suspicious Code Patterns / Layering Violations

**None detected.**

---

## 9. REALTIME-EDGE-SERVICE

**File Location:** `chatappBE/realtime-edge-service/src/main/java/com/example/realtime/`

### 9.1 Package Structure

```
realtime/
├── adapter/
│   ├── in/
│   │   ├── websocket/
│   │   │   ├── RealtimeWebSocketHandler.java    # WebSocket ingress (MAIN)
│   │   │   └── JwtHandshakeInterceptor.java     # JWT validation
│   │   ├── kafka/
│   │   │   ├── FriendshipKafkaEventConsumer.java# Consume friendship events
│   │   │   ├── NotificationKafkaEventConsumer.java# Consume notification events
│   │   │   └── (other Kafka consumers)
│   │   └── redis/
│   │       └── RedisEventListener.java
│   └── out/
│       ├── friendship/
│       │   ├── RestFriendshipCommandRouter.java
│       │   └── IFriendshipCommandRouter.java
│       ├── notification/
│       │   ├── RestNotificationCommandRouter.java
│       │   └── INotificationCommandRouter.java
│       ├── chat/
│       │   ├── RestChatCommandRouter.java
│       │   └── IChatCommandRouter.java
│       └── presence/
│           ├── PresenceDomainClient.java
│           ├── EdgePresenceLifecycleBridge.java
│           └── (presence clients)
├── connection/
│   ├── RealtimeSession.java                 # Session model
│   ├── RealtimeSessionRegistry.java         # Session tracking
│   ├── RealtimeWebSocketSessionStore.java   # WebSocket store
│   ├── IRealtimeSessionRegistry.java
│   ├── InMemoryRealtimeSessionRegistry.java
│   ├── RedisRealtimeSessionRegistry.java
│   ├── EdgeSessionMaintenanceJob.java
│   ├── EdgeSessionMetricsBinder.java
│   └── (connection logic)
├── delivery/
│   ├── ChatRealtimeDeliveryService.java     # Deliver chat events to WS
│   ├── FriendshipRealtimeDeliveryService.java
│   ├── NotificationRealtimeDeliveryService.java
│   ├── PresenceRealtimeDeliveryService.java
│   └── (delivery services)
├── dispatch/
│   ├── EdgeCrossInstanceDispatchCoordinator.java  # Multi-instance dispatch
│   ├── EdgeDeliveryHandoffPublisher.java
│   ├── EdgeDeliveryHandoffListener.java
│   ├── EdgeDeliveryHandoffEvent.java
│   └── (dispatch logic)
├── routing/
│   ├── CommandDispatcher.java               # Route WS commands to services
│   └── command/
│       ├── IChatCommandRouter.java
│       ├── INotificationCommandRouter.java
│       ├── IFriendshipCommandRouter.java
│       ├── NotificationCommandRequest.java
│       └── FriendshipCommandRequest.java
├── subscription/
│   └── ChannelSubscriptionManager.java      # Manage channel subscriptions
├── protocol/
│   ├── RealtimeClientMessage.java           # Incoming WS message
│   ├── RealtimeServerMessage.java           # Outgoing WS message
│   └── (protocol classes)
├── config/
│   ├── WebSocketConfig.java
│   ├── RedisListenerConfig.java
│   └── LocalValidationJwtDecoderConfig.java
├── RealtimeEdgeApplication.java             # Main app class
└── (other classes)
```

### 9.2 Key Classes & Responsibilities

| Class | File | Purpose |
|-------|------|---------|
| **RealtimeWebSocketHandler** | `adapter/in/websocket/RealtimeWebSocketHandler.java` | Main WebSocket ingress handler |
| **RealtimeSession** | `connection/RealtimeSession.java` | Model of a realtime session |
| **RealtimeSessionRegistry** | `connection/RealtimeSessionRegistry.java` | Track all active sessions |
| **CommandDispatcher** | `routing/CommandDispatcher.java` | Route WS commands to domain services |
| **ChatRealtimeDeliveryService** | `delivery/ChatRealtimeDeliveryService.java` | Deliver chat events to WS clients |
| **FriendshipRealtimeDeliveryService** | `delivery/FriendshipRealtimeDeliveryService.java` | Deliver friendship events |
| **NotificationRealtimeDeliveryService** | `delivery/NotificationRealtimeDeliveryService.java` | Deliver notifications |
| **PresenceRealtimeDeliveryService** | `delivery/PresenceRealtimeDeliveryService.java` | Deliver presence events |
| **EdgeCrossInstanceDispatchCoordinator** | `dispatch/EdgeCrossInstanceDispatchCoordinator.java` | Multi-instance broadcast |

### 9.3 Business Responsibility

**Realtime-Edge-Service owns:**
- ✅ **Unified WebSocket endpoint** for all real-time traffic
- ✅ **Command routing** (dispatch WS commands to domain services via REST)
- ✅ **Event delivery** (push Kafka/Redis events to WS clients)
- ✅ **Multi-instance coordination** (cross-instance broadcast)
- ✅ **Channel subscription management**
- ✅ **Session lifecycle**

**Does NOT own:**
- ❌ Business logic for any domain (delegated to service-specific endpoints)
- ❌ Persistence
- ❌ Authentication (only validates JWT)

### 9.4 Architecture: The "Unified WebSocket Edge"

```
                  ┌─────────────────────────────────────────────────┐
                  │      Realtime-Edge-Service WebSocket Layer       │
                  │  (Unified ingress point for all real-time comms) │
                  └──────┬────────────────────────────────────────────┘
                         │
         ┌───────────────┼───────────────┐
         │               │               │
      (WS)            (WS)            (WS)
    /ws/chat       /ws/friendship   /ws/notifications
    /ws/presence   /realtime        /ws/...
         │               │               │
         v               v               v
    ┌──────────────────────────────────────────┐
    │  RealtimeWebSocketHandler                │
    │  - Extract userId from JWT               │
    │  - Register session in session registry  │
    │  - Track subscriptions (room, user, etc) │
    └──────────────────────────────────────────┘
         │                    │
         │                    │ Incoming WS Message
         │                    v
         │           ┌──────────────────────────────────────┐
         │           │  CommandDispatcher                   │
         │           │  - Route to service-specific router  │
         │           │  - Call REST API on domain service   │
         │           └──────────────────────────────────────┘
         │                    │
         │ ┌─────────────────┘
         │ │
         v v
    ┌──────────────────────────────────────────────────────────┐
    │          Kafka Event Consumers (in realtime-edge)       │
    │  ├─ FriendshipKafkaEventConsumer                         │
    │  ├─ NotificationKafkaEventConsumer                       │
    │  └─ (other Kafka consumers for events)                   │
    └────────────┬────────────────────────────────────────────┘
                 │ Produce delivery events
                 v
    ┌──────────────────────────────────────────────────────────┐
    │         Realtime Delivery Services                       │
    │  ├─ ChatRealtimeDeliveryService                          │
    │  ├─ FriendshipRealtimeDeliveryService                    │
    │  ├─ NotificationRealtimeDeliveryService                  │
    │  └─ PresenceRealtimeDeliveryService                      │
    └────────────┬────────────────────────────────────────────┘
                 │
         ┌───────┴──────────────────────┐
         │                              │
      Local                         Remote
      sessions                     instances
         │                         (broadcast
         v                         handoff via
    ┌─────────────────────────────────────┐
    │  Send JSON to WebSocket session     │
    │  {                                  │
    │    "type": "chat.message.sent",     │
    │    "payload": {...},                │
    │    "eventId": "abc123"              │
    │  }                                  │
    └─────────────────────────────────────┘
```

### 9.5 Key Flows

#### Flow 1: User sends a chat message

```
Client → RealtimeEdgeService/ws/chat
   │
   ├─ Parse WsMessage(command: "send_message", payload: {...})
   │
   ├─ CommandDispatcher routes to RestChatCommandRouter
   │
   ├─ RestChatCommandRouter calls chat-service REST API
   │   POST /api/v1/messages (with JWT from session)
   │
   ├─ Chat-service processes and publishes to Redis
   │   redis.publish("chat.message.sent", ChatMessagePayload)
   │
   ├─ Chat-service WebSocket subscribers fan-out in their own layer
   │   (Duplicate delivery possible until unified)
   │
   └─ Via Kafka, realtime-edge consumes event
       └─ ChatRealtimeDeliveryService pushes to all connected clients
           on room:{roomId} channel
```

#### Flow 2: Kafka event arrives → push to WS clients

```
Kafka topic: friendship.request.events
   │
   ├─ FriendshipKafkaEventConsumer consumes
   │
   ├─ FriendshipRealtimeDeliveryService.deliverToUser(userId, ...)
   │
   ├─ Find all sessions subscribed to "friendship:user" channel
   │
   ├─ Split by ownership (local vs remote instances)
   │
   ├─ Local: push directly via WebSocket
   │
   └─ Remote: publish handoff event via Redis for remote instances to deliver
```

### 9.6 Dependencies on Other Services

**Inbound (receives from):**
- ✅ Kafka: Friendship, Notification, Chat events
- ✅ Redis: Event subscriptions

**Outbound (calls to):**
- ✅ REST calls to **chat-service**, **friendship-service**, **notification-service** (via command routers)
- ✅ Presence-service lifecycle bridge

### 9.7 Kafka Events Consumed

| Event Type | Topic | Consumer | Action |
|------------|-------|----------|--------|
| `friendship.request.sent`, etc. | `friendship.request.events`, `friendship.events` | `FriendshipKafkaEventConsumer` | Deliver to WS clients |
| `notification.requested`, etc. | `notification.events` | `NotificationKafkaEventConsumer` | Deliver to WS clients |
| `chat.message.sent`, `chat.reaction.updated`, etc. | (via Redis primarily) | (not Kafka consumer) | Deliver to WS clients |

**Evidence:** [FriendshipKafkaEventConsumer.java](chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/in/kafka/FriendshipKafkaEventConsumer.java)

### 9.8 Key Classes

| Class | File | Responsibility |
|-------|------|-----------------|
| `RealtimeWebSocketHandler` | `adapter/in/websocket/RealtimeWebSocketHandler.java` | Main WS handler (ingress) |
| `CommandDispatcher` | `routing/CommandDispatcher.java` | Route WS commands to services |
| `ChatRealtimeDeliveryService` | `delivery/ChatRealtimeDeliveryService.java` | Deliver chat events to WS |
| `FriendshipRealtimeDeliveryService` | `delivery/FriendshipRealtimeDeliveryService.java` | Deliver friendship events |
| `EdgeCrossInstanceDispatchCoordinator` | `dispatch/EdgeCrossInstanceDispatchCoordinator.java` | Multi-instance coordination |
| `RealtimeSessionRegistry` | `connection/RealtimeSessionRegistry.java` | Session tracking |

### 9.9 Multi-Instance Architecture

Realtime-edge supports **horizontal scaling**:
- Multiple instances can run
- Sessions hashed to instances
- Cross-instance broadcast via `EdgeDeliveryHandoffPublisher` (Redis-based)
- Metrics: `Metrics.counter("realtime.delivery.local.success", "deliveryType", "friendship")`

### 9.10 Suspicious Code Patterns / Layering Violations

**Minor architectural concern:**
- ⚠️ **Duplicate WebSocket delivery in transition phase:** Both realtime-edge AND service-local WebSocket handlers are active, potentially delivering duplicate messages. This is noted in code comments as "preserve legacy client behavior for service-specific websocket endpoints" during migration.

**Evidence:** [RealtimeWebSocketHandler.java](chatappBE/realtime-edge-service/src/main/java/com/example/realtime/adapter/in/websocket/RealtimeWebSocketHandler.java#L50)

```java
// Preserve legacy client behavior for service-specific websocket endpoints.
String endpointPath = session.getUri() != null ? session.getUri().getPath() : "";
if (endpointPath.startsWith("/ws/friendship")) {
    subscribeSession(realtimeSession, "friendship:" + userId);
} else if (endpointPath.startsWith("/ws/chat")) {
    // Chat clients explicitly join per-room channels via JOIN/LEAVE commands.
}
// Phase-1 notification compatibility is preserved
```

---

## 10. Common Modules

**Location:** `chatappBE/common/`

### 10.1 Modules Summary

| Module | Purpose | Key Classes |
|--------|---------|------------|
| **common-core** | Base exceptions, error codes, utilities | `BusinessException`, `CommonErrorCode`, `PipelineExecutor` |
| **common-web** | Base controllers, API responses, CORS | `BaseController`, `ApiResponse`, `CorsProperties` |
| **common-kafka** | Kafka event envelope, producer/consumer | `EventEnvelope`, `EventMetadata`, `KafkaEventProducer` |
| **common-redis** | Redis pub/sub, event publisher | `RedisEventPublisher`, `RedisEventSubscriber` |
| **common-events** | Shared event contracts | Event payloads (AccountCreated, ChatMessage, etc.) |
| **common-websocket** | Session registry, broadcasters | `IUserBroadcaster`, `IRoomBroadcaster` |
| **common-security** | JWT utilities, security filters | `JwtHelper`, security configurations |
| **common-feign** | Feign client configuration | Base Feign client setup |
| **common-redis-cache** | Redis caching utilities | Cache helpers, TTL management |

### 10.2 Key Shared Abstractions

| Class | Module | Purpose |
|-------|--------|---------|
| `EventEnvelope<T>` | common-kafka | Wraps domain events with metadata |
| `EventMetadata` | common-kafka | Event ID, type, source, timestamp, correlation ID |
| `IUserBroadcaster` | common-websocket | Send WebSocket message to specific user |
| `IRoomBroadcaster` | common-websocket | Send WebSocket message to room members |
| `RedisEventPublisher` | common-redis | Publish events to Redis channels |
| `RedisEventSubscriber<T>` | common-redis | Subscribe to Redis events (type-safe) |
| `PipelineExecutor<T>` | common-core | Execute choreography pipelines |
| `BusinessException` | common-core | Standard business exception |

---

## 11. Answers to Specific Questions

### 11.1 Which service has WebSocket endpoints?

**Multiple services:**
1. ✅ **Chat-service** → `/ws/chat` (send messages, react, edit, delete)
2. ✅ **Presence-service** → `/ws/presence` (heartbeat, status changes, room join/leave, typing)
3. ✅ **Notification-service** → `/ws/notifications` (notification push)
4. ✅ **Friendship-service** → `/ws/friendship` (friendship status updates)
5. ✅ **Realtime-Edge-Service** → `/ws/chat`, `/ws/presence`, `/ws/friendship`, `/ws/notifications`, `/realtime` (unified ingress)

### 11.2 Is realtime-edge-service actually used?

✅ **YES, heavily used.** It's a **unified WebSocket ingress layer** that:
- Accepts all real-time connections
- Routes commands to domain services via REST
- Consumes Kafka events and delivers to WebSocket clients
- Supports multi-instance deployment
- Provides cross-instance broadcast coordination

**However, there's a transition phase:**
- Service-local WebSocket handlers still exist (being migrated)
- Code comment indicates: "preserve legacy client behavior for service-specific websocket endpoints"
- Eventually, all traffic should go through realtime-edge

### 11.3 Does chat-service own chat messages and rooms?

✅ **YES, absolutely.**
- Entity: `ChatMessage`, `Room`
- Table: `chat_messages`, `rooms`
- Responsibility: create, edit, delete messages; manage rooms; reactions; mentions; pinning

### 11.4 Does friendship-service own friend relationships?

✅ **YES.**
- Entity: `Friendship`
- Table: `friendships` (with unique constraint on `(userLow, userHigh)`)
- Responsibility: friend requests, accept/decline, block/unblock

### 11.5 Does presence-service own presence/online status?

✅ **YES, completely.**
- Storage: Redis (TTL-based, no database)
- Responsibility: online/offline status, active/away, manual status, connection counting, room presence
- Uses Redis keyspace notifications for TTL expiry

### 11.6 Does notification-service own notifications?

✅ **YES.**
- Entity: `Notification`
- Table: `notifications`
- Responsibility: create, store, query, mark read, room mute settings

### 11.7 Is auth-service only for OAuth2/JWT or does it do more?

✅ **Auth-only, but comprehensive:**
- ✅ Account creation/deletion (email/password)
- ✅ Local authentication (email/password login)
- ✅ OAuth2/Google (Google ID token verification, Google OAuth exchange)
- ✅ JWT lifecycle (issuance, refresh, validation, revocation)
- ✅ Password management (change, reset)
- ✅ Email verification
- ✅ **Does NOT:** manage user profiles, friendships, chat

### 11.8 Is user-service only for profiles or does it do more?

✅ **Profile-focused, minimal:**
- ✅ User profile CRUD (display name, username, avatar, about me)
- ✅ Avatar URL/public_id management (Cloudinary)
- ✅ Consumed account.created event to create profiles
- ✅ **Does NOT:** manage authentication, friendships, chat

### 11.9 Is upload-service a thin wrapper around Cloudinary or does it have logic?

✅ **Thin wrapper with signing logic:**
- ✅ Generates signed URLs for browser upload to Cloudinary
- ✅ Validates upload policies (size, format per purpose)
- ✅ Confirms uploads (verifies asset exists in Cloudinary)
- ✅ **Does NOT:** store files, track asset metadata (calling services do)

### 11.10 What is gateway-service responsible for?

✅ **API Gateway (Spring Cloud Gateway):**
- ✅ Route requests to services (by path predicates)
- ✅ JWT validation (before forwarding)
- ✅ CORS handling
- ✅ Rate limiting (IP-based)
- ✅ Fallback responses
- ✅ **Does NOT:** business logic, persistence

### 11.11 What is realtime-edge-service's actual current role?

✅ **Unified real-time ingress and event delivery:**
- ✅ Accepts WebSocket connections on multiple endpoints
- ✅ Routes user commands to domain services via REST
- ✅ Consumes Kafka events (friendship, notification, etc.)
- ✅ Pushes events to connected WebSocket clients
- ✅ Supports multi-instance deployment with cross-instance coordination
- ✅ Maintains session registry and subscriptions

---

## 12. Data Flow Examples

### Example 1: Sending a Chat Message

```
Client → Realtime-Edge /ws/chat
  │ {"command": "send_message", "payload": {"roomId": "...", "content": "..."}}
  v
RealtimeWebSocketHandler
  │ Extract userId from JWT
  │ Parse message
  v
CommandDispatcher
  │ Determine it's a chat command
  v
ChatWebSocketHandler (via REST call to chat-service)
  │ POST /api/v1/messages
  v
Chat-Service
  │ SendMessagePipeline:
  │   1. Generate sequence number (Redis)
  │   2. Validate message
  │   3. Create MessageAggregate
  │   4. Extract mentions
  │   5. Persist mentions
  │   6. Map attachments
  │   7. Check block status (call friendship-service)
  │   8. Validate room permission
  │   9. Persist message (DB)
  │   10. Publish event to Redis
  v
Redis: chat.message.sent
  │ ChatMessageSentRedisSubscriber
  └─ Fan-out to room members + DM recipients via WebSocket
  
Realtime-Edge: Kafka consumer
  │ FriendshipKafkaEventConsumer (different service triggered mention)
  │ NotificationEventConsumer (mention notification created)
  v
ChatRealtimeDeliveryService
  │ Find sessions subscribed to room:{roomId}
  v
Send JSON to WebSocket:
  {"type": "chat.message.sent", "payload": {...}, "eventId": "..."}
```

### Example 2: Friend Request Accepted

```
Client → Realtime-Edge /ws/friendship
  │ {"command": "accept_request", "payload": {"friendshipId": "..."}}
  v
CommandDispatcher
  v
RestFriendshipCommandRouter
  │ POST /api/v1/friends/{id}/accept (to friendship-service)
  v
Friendship-Service
  │ Update status to ACCEPTED
  │ Publish: friendship.request.accepted event to Kafka
  v
Kafka: friendship.request.events
  ├─ NotificationService consumes
  │  └─ Creates FRIEND_REQUEST_ACCEPTED notification
  │     └─ Publishes notification.requested to Kafka
  │
  ├─ Realtime-Edge consumes
  │  └─ FriendshipKafkaEventConsumer
  │     └─ Pushes to /ws/friendship clients
  │
  └─ Notification-Service consumes notification.requested
     └─ Pushes to /ws/notifications clients
```

### Example 3: Presence Update (Typing Indicator)

```
Client → Realtime-Edge /ws/presence
  │ {"command": "typing", "payload": {"roomId": "..."}}
  v
CommandDispatcher
  v
RestPresenceCommandRouter
  │ Calls presence-service directly
  v
Presence-Service
  │ Publishes: presence.user.typing to Redis
  v
Redis: presence:room:{roomId}
  │
  ├─ PresenceService subscribers
  │  └─ Broadcast to room members
  │
  └─ Realtime-Edge Redis listener
     └─ Pushes to all clients in room
```

---

## 13. Observability & Consistency Patterns

### 13.1 Event Deduplication

| Service | Location | Pattern |
|---------|----------|---------|
| Chat-Service | `RealtimeEventDedupeGuard` | Dedup Redis events by eventId |
| Notification-Service | `NotificationEventDedupeGuard` | Dedup Kafka events |
| Friendship-Service | `FriendshipEventDedupeGuard` | Dedup Kafka events |
| Realtime-Edge | (not observed) | Assumed in delivery services |

### 13.2 Transactional Guarantees

| Service | Pattern |
|---------|---------|
| Auth-Service | `@Transactional` with after-commit event publishing |
| User-Service | Standard Spring `@Transactional` |
| Chat-Service | Pipeline steps participate in transaction, event publish on success |
| Friendship-Service | `@Transactional` with Kafka producer |
| Notification-Service | Kafka consumers trigger DB inserts within transaction |

### 13.3 Error Handling

| Pattern | Location | Use |
|---------|----------|-----|
| `BusinessException` | common-core | Domain-specific exceptions |
| Error codes (enum) | Each service | Standardized error response codes |
| Fallback responses | Gateway-Service | Circuit breaker fallbacks |
| Retries | Kafka consumers | Automatic Kafka retry with backoff |

---

## 14. Key Findings & Architectural Strengths

### Strengths

1. ✅ **Clean domain separation** - Each service owns clear business responsibility
2. ✅ **Event-driven** - Kafka for inter-service, Redis for intra-service real-time
3. ✅ **DDD practices** - Chat-service uses aggregates, domain models
4. ✅ **Pipeline choreography** - Chat message send uses step-based pipeline
5. ✅ **Real-time unified edge** - Realtime-edge consolidates WebSocket ingress
6. ✅ **Scalable presence** - Redis-based presence with TTL (no polling)
7. ✅ **Stateless services** - Upload-service example of pure computation
8. ✅ **Feign clients** - Type-safe inter-service HTTP calls
9. ✅ **No shared database** - Each service has its own DB (database-per-service)
10. ✅ **HMAC signing** - Upload tokens securely signed

### Areas of Potential Concern

1. ⚠️ **Duplicate WebSocket delivery in transition** - Both service-local and realtime-edge handlers active
2. ⚠️ **Kafka → Redis conversion** - Some services consume Kafka then publish Redis (double hop)
3. ⚠️ **Missing Saga pattern** - Multi-step operations may not have compensation logic
4. ⚠️ **Presence TTL edge cases** - Race condition possible between heartbeat and TTL expiry
5. ⚠️ **No distributed tracing** - Correlation IDs present in EventMetadata but no obvious tracing setup

---

## 15. Database Schema Summary

### Auth-Service
- accounts
- refresh_tokens
- password_reset_tokens
- verification_tokens
- jwt_keys
- oauth_login_exchanges
- identity_providers

### User-Service
- user_profiles

### Chat-Service
- chat_messages
- chat_attachments
- chat_reactions
- chat_message_mentions
- room_pinned_messages
- rooms
- room_members

### Presence-Service
- **NONE** (Redis only)

### Notification-Service
- notifications
- room_mute_settings

### Friendship-Service
- friendships

### Upload-Service
- **NONE** (stateless)

### Gateway-Service
- **NONE** (stateless)

### Realtime-Edge-Service
- **NONE** (ephemeral session state in Redis)

---

## 16. Conclusion

This is a **well-architected microservices system** with:
- Clear domain boundaries
- Event-driven communication
- Redis-based real-time layer
- Unified WebSocket ingress (realtime-edge)
- Proper service encapsulation
- Scalable design patterns

The architecture supports the stated goal: a real-time chat application with friend management, presence tracking, and notification delivery.


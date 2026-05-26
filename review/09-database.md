# 09. Database

## Storage Model
Primary durable store is PostgreSQL, one logical DB per domain (auth, user, chat, friendship, notification, presence DB configured though presence is mostly ephemeral).

## ORM Strategy
- Spring Data JPA + Hibernate.
- Most services use `spring.jpa.hibernate.ddl-auto: update` in checked config.
- entities include explicit indexes and many unique constraints.

## Entity Highlights
Auth:
- `Account` unique email
- `RefreshToken` hashed token + account index + expiry index
- verification/reset/oAuth exchange token entities with token-hash uniqueness

User:
- `UserProfile` primary key `account_id`, unique username

Friendship:
- `Friendship` unique pair (`user_low`,`user_high`) + status index

Chat:
- `Room`, `RoomMember`, `RoomBan`
- `ChatMessage` unique (`room_id`,`seq`) + client id index + sender/reply indexes
- reaction/mention/pin entities with target indexes

Notification:
- `Notification`, `RoomMuteSetting`

## Indexing Quality
Strengths:
- practical lookup indexes for core query paths (room seq, room members, token hashes).

Weaknesses:
- index naming and column naming styles mix camel and snake in annotations; may produce inconsistent physical schema depending on naming strategy.

## Transaction Boundaries
- command services and pipeline steps use transactional boundaries around writes.
- publish-after-commit pattern used in chat send flow to avoid publishing uncommitted data.

## Concurrency Strategy
- refresh token rotation uses atomic update query (`revokeIfNotRevoked`).
- room message sequence uses Redis increment fallback to DB snapshot.

## N+1 / Query Risks
Potential hotspots:
- room/member enrichment and profile lookups can fan out if not batched.
- user/friendship cross-service enrichment pushes some N+1 risk to API layer rather than DB layer.

## Soft Delete / Lifecycle
- messages use `deleted` flags + timestamps rather than hard delete.
- token entities use `revoked`/`used` flags for lifecycle auditing.

## Migration Strategy Risk
Major risk: schema evolution relies on runtime auto-update rather than explicit migration scripts for most service tables. This reduces change governance and rollback predictability.

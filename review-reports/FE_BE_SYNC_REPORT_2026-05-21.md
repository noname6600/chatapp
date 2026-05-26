# FE-BE Sync Report

## Backend Changes Detected

* APIs changed
  * Gateway exposes friendship under `/api/v1/friends/**`; FE still targeted `/api/v1/friendship/**` and also duplicated `/friends` in service paths.
  * Gateway exposes upload-service under `/api/v1/uploads/**`; FE still targeted `/api/v1/upload/**` and also duplicated `/uploads` in service paths.
  * Chat room avatar endpoint now accepts confirmed upload metadata JSON at `POST /api/v1/rooms/{roomId}/avatar`; FE was still sending multipart `FormData` directly to chat-service.
* WS events changed
  * Realtime edge inbound presence command handling currently accepts `presence.room.stop_typing` for stop-typing commands, while FE emitted `presence.room.stop-typing`.
* DTO changes
  * Room avatar metadata contract requires `publicId`, `secureUrl`, `resourceType`, `format`, `bytes`, `width`, `height` after upload-service confirmation.
  * Upload-service supports explicit `room-avatar` purpose in addition to `chat-attachment` and `user-avatar`.
* auth changes
  * No FE auth contract change required in this pass; current auth REST paths still align with `POST /api/v1/auth/*`.
* payload changes
  * Room avatar update flow now depends on upload-service `prepare` + Cloudinary upload + `confirm` before room-service metadata apply.
* removed endpoints/events
  * No confirmed removal in FE-touched flows yet; `/rooms/{roomId}/invite` remains present as a legacy self-join alias, not a direct member-invite API.
* renamed fields
  * No FE field rename applied in this pass.
* validation changes
  * Room avatar metadata now validates `resourceType=image` and requires positive width/height/bytes.

## FE Files Modified

* `chatappFE/src/config/api.config.ts`
  * reason for change: aligned FE gateway base segments with current gateway routes.
  * backend dependency affected: gateway-service route predicates for friendship-service and upload-service.
  * exact contract mismatch fixed: FE used `friendship` and `upload` base segments while gateway exposes `friends` and `uploads`.
* `chatappFE/src/api/friend.service.ts`
  * reason for change: removed duplicated `/friends` segment after base URL alignment.
  * backend dependency affected: `FriendController` under `@RequestMapping("/api/v1/friends")`.
  * exact contract mismatch fixed: FE produced paths like `/api/v1/friends/friends/request/{id}` instead of `/api/v1/friends/request/{id}`.
* `chatappFE/src/api/upload.service.ts`
  * reason for change: aligned upload-service endpoints and upload purpose enum with BE contract.
  * backend dependency affected: `UploadController` under `@RequestMapping("/api/v1/uploads")` and `UploadPurpose.ROOM_AVATAR`.
  * exact contract mismatch fixed: FE called `/uploads/prepare` and `/uploads/confirm` under an already-upload-scoped base, and lacked `room-avatar` purpose support.
* `chatappFE/src/api/room.service.ts`
  * reason for change: switched room avatar flow from direct multipart upload to confirmed upload metadata apply.
  * backend dependency affected: `POST /api/v1/rooms/{roomId}/avatar` with `RoomAvatarMetadataRequest`.
  * exact contract mismatch fixed: FE sent `multipart/form-data`; BE now requires `{ publicId, secureUrl, resourceType, format, bytes, width, height }` JSON.
* `chatappFE/src/websocket/presence.socket.ts`
  * reason for change: adapted stop-typing outbound command to actual realtime edge inbound handler.
  * backend dependency affected: `RealtimeWebSocketHandler.handlePresenceMessage` switch-case.
  * exact contract mismatch fixed: FE emitted `presence.room.stop-typing`; edge handler currently accepts `presence.room.stop_typing`.
* `chatappFE/src/api/user.avatar-upload.test.ts`
  * reason for change: extended upload-service contract coverage to room avatar flow.
  * backend dependency affected: upload-service confirm contract plus room avatar apply endpoint.
  * exact contract mismatch fixed: validates FE now posts confirmed metadata rather than raw file payload.
* `chatappFE/src/websocket/presence.socket.test.ts`
  * reason for change: added focused regression test for outbound stop-typing command mapping.
  * backend dependency affected: realtime edge inbound presence command parser.
  * exact contract mismatch fixed: validates FE now sends `presence.room.stop_typing`.

## API Sync Fixes

* endpoint updates
  * Friendship REST base updated from `.../friendship` to `.../friends`.
  * Upload REST base updated from `.../upload` to `.../uploads`.
  * Friendship service method paths now resolve directly under `/api/v1/friends/*` without a duplicate `/friends` prefix.
  * Upload service methods now resolve to `/api/v1/uploads/prepare` and `/api/v1/uploads/confirm` without a duplicate `/uploads` prefix.
* request mapping fixes
  * Room avatar updates now use upload-service `prepare`/Cloudinary/`confirm` before posting confirmed metadata to room-service.
* response parsing fixes
  * No response-body shape change required in this pass; existing `ApiResponse<T>` unwrap remains compatible.
* auth/header fixes
  * Existing bearer auth injection remains compatible; no auth header change required for this pass.

## WebSocket Sync Fixes

* event rename fixes
  * No inbound event rename applied; outbound stop-typing command is now translated to the current edge handler command token.
* payload compatibility fixes
  * Presence stop-typing outbound frame now serializes as `{ type: "presence.room.stop_typing", roomId }`.
* reconnect/state fixes
  * No reconnect logic change required in this pass.
* listener cleanup fixes
  * No listener cleanup change required in this pass.

## Type/DTO Sync Fixes

* interface updates
  * `UploadPurpose` union extended with `"room-avatar"`.
* enum updates
  * No FE enum rename applied; websocket adapter uses a dedicated outbound command constant for the edge-specific stop-typing token.
* nullable handling
  * Existing optional upload duration handling preserved.
* optional field handling
  * Room avatar flow intentionally posts only metadata fields required by `RoomAvatarMetadataRequest`.

## Runtime Issues Fixed

* compile errors
  * None introduced in the touched slice.
* runtime crashes
  * Prevented room avatar update failures caused by posting unsupported multipart payloads to room-service.
* stale state bugs
  * Prevented friendship API calls from silently targeting invalid duplicated paths after gateway route changes.
* undefined/null issues
  * No direct nullability fix in this pass.
* serialization/deserialization issues
  * Corrected presence stop-typing command serialization for realtime edge ingestion.

## Validation Results

* lint result
  * Baseline before sync edits: `npm run lint` failed with 54 errors and 14 warnings, largely pre-existing FE lint debt outside the contract slice.
  * Post-sync full lint: still fails with the same repo-wide 54 errors and 14 warnings; touched presence adapter files were rechecked separately with `npx eslint src/websocket/presence.socket.ts src/websocket/presence.socket.test.ts` and passed.
* typecheck result
  * Included in baseline build; `tsc -b` passed.
  * Post-sync typecheck: `npx tsc -b` passed.
* build result
  * Baseline before sync edits: `npm run build` passed.
  * Focused post-edit contract validation: `npx vitest run src/api/user.avatar-upload.test.ts src/websocket/presence.socket.test.ts` passed with 2 files and 5 tests green.
  * Post-sync full build: `npm run build` passed.
* remaining warnings
  * Vite chunk-size warning on production build output.
* known limitations
  * Post-sync full test run still fails outside the touched contract slice.
  * Current failing buckets remain unrelated FE debt:
    * mocked `createBaseApi` expectations in `src/store/friend.store.test.tsx` and `src/components/chat/MessageList.reply-linking.test.tsx`
    * missing router wrappers in `src/layouts/ChatPageLayout.test.tsx` and `src/components/presence/PresenceTypingIntegration.test.tsx`
    * jsdom environment gaps in `src/components/chat/draft/MediaBlock.test.tsx` and `src/components/chat/draft/TextBlock.test.tsx`
    * existing store/component behavior assertions in `src/store/chat.pagination.test.tsx` and `src/components/friend/AddFriendPanel.test.tsx`

## Remaining FE Risks

* unresolved BE ambiguity
  * `RealtimeWebSocketHandler` accepts inbound stop-typing as `presence.room.stop_typing`, while shared BE presence enum still advertises `presence.room.stop-typing`; FE must currently adapt to handler behavior.
  * Legacy room invite route `/rooms/{roomId}/invite` no longer behaves like a direct server-side member invite and should not be reused for FE invite workflows.
* missing backend documentation
  * No dedicated FE-facing contract doc enumerates realtime edge inbound command aliases versus outbound event names.
* potential runtime edge cases
  * Existing FE tests indicate unrelated router wrapping and jsdom environment gaps that may obscure later regressions.
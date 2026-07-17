package com.chatweb.chat.modules.room.controller;

import com.chatweb.chat.modules.room.dto.*;
import com.chatweb.chat.modules.message.application.dto.response.MessageResponse;
import com.chatweb.chat.modules.room.service.IPrivateRoomService;
import com.chatweb.chat.modules.room.service.IRoomPinService;
import com.chatweb.chat.modules.room.service.IRoomQueryService;
import com.chatweb.chat.modules.room.service.IRoomService;
import com.chatweb.chat.modules.room.service.RoomMembershipGuard;
import com.chatweb.common.web.controller.BaseController;
import com.chatweb.common.core.exception.BusinessException;
import com.chatweb.common.core.exception.CommonErrorCode;
import com.chatweb.common.web.response.ApiResponse;
import com.chatweb.common.security.jwt.JwtHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController extends BaseController {

    private final IRoomService roomService;
    private final IRoomQueryService roomQueryService;
    private final IPrivateRoomService privateRoomService;
        private final IRoomPinService roomPinService;
        private final RoomMembershipGuard roomMembershipGuard;

    @PostMapping
    public ResponseEntity<ApiResponse<RoomResponse>> createRoom(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateRoomRequest request
    ) {
        UUID userId = JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized"));
        return ResponseEntity.ok(ApiResponse.success(
                roomService.createRoom(userId, request.getName())
        ));
    }

    @PostMapping("/private-chat")
    public ResponseEntity<ApiResponse<RoomResponse>> createOrGetPrivateChat(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam UUID userId
    ) {
        UUID me = JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized"));
        return ResponseEntity.ok(ApiResponse.success(
                privateRoomService.getOrCreatePrivateRoom(me, userId)
        ));
    }

    @PostMapping("/join-by-code")
    public ResponseEntity<ApiResponse<Void>> joinByCode(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String code
    ) {
                roomService.joinByCode(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), code);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

        @PostMapping("/{roomId}/join")
        public ResponseEntity<ApiResponse<Void>> joinByInviteRoomId(
                        @AuthenticationPrincipal Jwt jwt,
                        @PathVariable UUID roomId
        ) {
                roomService.joinByInviteRoomId(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), roomId);
                return ResponseEntity.ok(ApiResponse.success(null));
        }

        @PostMapping("/{roomId}/invite")
        public ResponseEntity<ApiResponse<Void>> joinByLegacyInviteRoute(
                        @AuthenticationPrincipal Jwt jwt,
                        @PathVariable UUID roomId,
                        @RequestParam(required = false) UUID userId
        ) {
                roomService.joinByInviteRoomId(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), roomId);
                return ResponseEntity.ok(ApiResponse.success(null));
        }

    @GetMapping("/{roomId}/code")
    public ResponseEntity<ApiResponse<String>> getRoomCode(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId
    ) {
        UUID userId = JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized"));
        roomMembershipGuard.ensureRoomMember(roomId, userId);
        return ResponseEntity.ok(
                ApiResponse.success(roomService.getRoomCode(roomId))
        );
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<RoomResponse>>> myRooms(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                roomQueryService.roomsOfUser(JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")))
        ));
    }

    @PutMapping("/{roomId}/name")
    public ResponseEntity<ApiResponse<RoomResponse>> renameRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @Valid @RequestBody UpdateRoomRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                roomService.renameRoom(roomId, JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), request.getName())
        ));
    }

    @GetMapping("/{roomId}/members")
    public ResponseEntity<ApiResponse<List<RoomMemberResponse>>> getMembers(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId
    ) {
        UUID userId = JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized"));
        roomMembershipGuard.ensureRoomMember(roomId, userId);
        return ResponseEntity.ok(ApiResponse.success(
                roomQueryService.membersOfRoom(roomId)
        ));
    }

    @GetMapping("/{roomId}/members/page")
    public ResponseEntity<ApiResponse<PagedRoomMembersResponse>> getMembersPaged(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String query
    ) {
        UUID userId = JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized"));
        roomMembershipGuard.ensureRoomMember(roomId, userId);
        return ResponseEntity.ok(ApiResponse.success(
                roomQueryService.membersOfRoom(roomId, page, size, query)
        ));
    }

    @PostMapping("/members/bulk")
    public ResponseEntity<ApiResponse<Map<UUID, List<RoomMemberResponse>>>> getMembersBulk(
            @RequestBody List<UUID> roomIds
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                roomQueryService.membersOfRooms(roomIds)
        ));
    }

    @GetMapping("/{roomId}/member-count")
    public ResponseEntity<ApiResponse<Long>> getMemberCount(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId
    ) {
        UUID userId = JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized"));
        roomMembershipGuard.ensureRoomMember(roomId, userId);
        return ResponseEntity.ok(ApiResponse.success(
                roomQueryService.memberCount(roomId)
        ));
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId
    ) {
                roomService.leaveRoom(roomId, JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{roomId}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @PathVariable UUID userId
    ) {
                roomService.removeMember(roomId, JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

        @PostMapping("/{roomId}/members/{userId}/kick")
        public ResponseEntity<ApiResponse<Void>> kickMember(
                        @AuthenticationPrincipal Jwt jwt,
                        @PathVariable UUID roomId,
                        @PathVariable UUID userId
        ) {
                roomService.removeMember(roomId, JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), userId);
                return ResponseEntity.ok(ApiResponse.success(null));
        }

        @PostMapping("/{roomId}/members/{userId}/ban")
        public ResponseEntity<ApiResponse<Void>> banMember(
                        @AuthenticationPrincipal Jwt jwt,
                        @PathVariable UUID roomId,
                        @PathVariable UUID userId
        ) {
                roomService.banMember(roomId, JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), userId);
                return ResponseEntity.ok(ApiResponse.success(null));
        }

        @DeleteMapping("/{roomId}/members/{userId}/ban")
        public ResponseEntity<ApiResponse<Void>> unbanMember(
                        @AuthenticationPrincipal Jwt jwt,
                        @PathVariable UUID roomId,
                        @PathVariable UUID userId
        ) {
                roomService.unbanMember(roomId, JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), userId);
                return ResponseEntity.ok(ApiResponse.success(null));
        }

        @PostMapping("/{roomId}/members/ban-bulk")
        public ResponseEntity<ApiResponse<Void>> bulkBanMembers(
                        @AuthenticationPrincipal Jwt jwt,
                        @PathVariable UUID roomId,
                        @Valid @RequestBody BulkMemberModerationRequest request
        ) {
                roomService.bulkBanMembers(roomId, JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), request.getUserIds());
                return ResponseEntity.ok(ApiResponse.success(null));
        }

        @PostMapping("/{roomId}/members/{userId}/transfer-ownership")
        public ResponseEntity<ApiResponse<Void>> transferOwnership(
                        @AuthenticationPrincipal Jwt jwt,
                        @PathVariable UUID roomId,
                        @PathVariable UUID userId
        ) {
                roomService.transferOwnership(roomId, JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), userId);
                return ResponseEntity.ok(ApiResponse.success(null));
        }

        @GetMapping("/{roomId}/members/banned/page")
        public ResponseEntity<ApiResponse<PagedBannedMembersResponse>> getBannedMembersPaged(
                        @PathVariable UUID roomId,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size
        ) {
                return ResponseEntity.ok(ApiResponse.success(
                                roomQueryService.bannedMembersOfRoom(roomId, page, size)
                ));
        }

        @PostMapping("/{roomId}/read")
        public ResponseEntity<ApiResponse<Void>> markRoomRead(
                        @AuthenticationPrincipal Jwt jwt,
                        @PathVariable UUID roomId
        ) {
                roomService.markRoomRead(roomId, JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")));
                return ResponseEntity.ok(ApiResponse.success(null));
        }

        @PostMapping("/{roomId}/pins")
        public ResponseEntity<ApiResponse<Void>> pinMessage(
                        @AuthenticationPrincipal Jwt jwt,
                        @PathVariable UUID roomId,
                        @RequestParam UUID messageId
        ) {
                roomPinService.pinMessage(roomId, JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), messageId);
                return ResponseEntity.ok(ApiResponse.success(null));
        }

        @DeleteMapping("/{roomId}/pins/{messageId}")
        public ResponseEntity<ApiResponse<Void>> unpinMessage(
                        @AuthenticationPrincipal Jwt jwt,
                        @PathVariable UUID roomId,
                        @PathVariable UUID messageId
        ) {
                roomPinService.unpinMessage(roomId, JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")), messageId);
                return ResponseEntity.ok(ApiResponse.success(null));
        }

        @GetMapping("/{roomId}/pins")
        public ResponseEntity<ApiResponse<List<MessageResponse>>> getPinnedMessages(
                        @AuthenticationPrincipal Jwt jwt,
                        @PathVariable UUID roomId
        ) {
                List<MessageResponse> pinnedMessages = roomPinService.getPinnedMessages(roomId, JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized")));
                return ResponseEntity.ok(ApiResponse.success(pinnedMessages));
        }

    @PostMapping("/{roomId}/avatar")
    public ResponseEntity<ApiResponse<RoomAvatarUploadResponse>> applyAvatarMetadata(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @Valid @RequestBody RoomAvatarMetadataRequest request
    ) {
        UUID userId = JwtHelper.extractUserId(jwt).orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHORIZED, "Unauthorized"));
        RoomAvatarUploadResponse response = roomService.applyAvatarMetadata(roomId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

}



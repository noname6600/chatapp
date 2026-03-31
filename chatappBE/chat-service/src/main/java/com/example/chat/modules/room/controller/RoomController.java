package com.example.chat.modules.room.controller;

import com.example.chat.modules.room.dto.*;
import com.example.chat.modules.room.service.IPrivateRoomService;
import com.example.chat.modules.room.service.IRoomQueryService;
import com.example.chat.modules.room.service.IRoomService;
import com.example.common.web.controller.BaseController;
import com.example.common.web.exception.BusinessException;
import com.example.common.web.exception.ErrorCode;
import com.example.common.web.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping
    public ResponseEntity<ApiResponse<RoomResponse>> createRoom(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateRoomRequest request
    ) {
        UUID userId = currentUserId(jwt);
        return ResponseEntity.ok(ApiResponse.success(
                roomService.createRoom(userId, request.getName())
        ));
    }

    @PostMapping("/private-chat")
    public ResponseEntity<ApiResponse<RoomResponse>> createOrGetPrivateChat(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam UUID userId
    ) {
        UUID me = currentUserId(jwt);
        return ResponseEntity.ok(ApiResponse.success(
                privateRoomService.getOrCreatePrivateRoom(me, userId)
        ));
    }

    @PostMapping("/join-by-code")
    public ResponseEntity<ApiResponse<Void>> joinByCode(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam String code
    ) {
        roomService.joinByCode(currentUserId(jwt), code);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{roomId}/code")
    public ResponseEntity<ApiResponse<String>> getRoomCode(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId
    ) {
        return ResponseEntity.ok(
                ApiResponse.success(roomService.getRoomCode(roomId))
        );
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<RoomResponse>>> myRooms(
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                roomQueryService.roomsOfUser(currentUserId(jwt))
        ));
    }

    @PutMapping("/{roomId}/name")
    public ResponseEntity<ApiResponse<RoomResponse>> renameRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @Valid @RequestBody UpdateRoomRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                roomService.renameRoom(roomId, currentUserId(jwt), request.getName())
        ));
    }

    @GetMapping("/{roomId}/members")
    public ResponseEntity<ApiResponse<List<RoomMemberResponse>>> getMembers(
            @PathVariable UUID roomId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                roomQueryService.membersOfRoom(roomId)
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
            @PathVariable UUID roomId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                roomQueryService.memberCount(roomId)
        ));
    }

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveRoom(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId
    ) {
        roomService.leaveRoom(roomId, currentUserId(jwt));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

        @PostMapping("/{roomId}/read")
        public ResponseEntity<ApiResponse<Void>> markRoomRead(
                        @AuthenticationPrincipal Jwt jwt,
                        @PathVariable UUID roomId
        ) {
                roomService.markRoomRead(roomId, currentUserId(jwt));
                return ResponseEntity.ok(ApiResponse.success(null));
        }

    @PostMapping(
            value = "/{roomId}/avatar",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<ApiResponse<RoomAvatarUploadResponse>> uploadAvatar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID roomId,
            @RequestPart("file") MultipartFile file
    ) {
        validate(file);
        return ResponseEntity.ok(ApiResponse.success(
                roomService.uploadAvatar(roomId, currentUserId(jwt), file)
        ));
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Avatar file is required");
        }

        String type = file.getContentType();

        if (type == null || !type.startsWith("image/")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "File must be an image");
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Avatar too large");
        }
    }
}
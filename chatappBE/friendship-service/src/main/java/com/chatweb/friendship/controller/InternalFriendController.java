package com.chatweb.friendship.controller;

import com.chatweb.common.web.controller.BaseController;
import com.chatweb.common.web.response.ApiResponse;
import com.chatweb.friendship.service.IFriendQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/friends")
@RequiredArgsConstructor
public class InternalFriendController extends BaseController {

    private final IFriendQueryService queryService;

    @GetMapping("/blocked-between")
    public ResponseEntity<ApiResponse<Boolean>> blockedBetween(
            @RequestParam UUID user1,
            @RequestParam UUID user2
    ) {
        return ok(queryService.existsBlockBetween(user1, user2));
    }
}

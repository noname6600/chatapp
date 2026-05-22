package com.chatweb.chat.modules.message.infrastructure.client;

import com.chatweb.common.feign.FeignJwtConfig;
import com.chatweb.common.web.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(
        name = "friendship-service",
        url = "${services.friendship.url}",
        configuration = FeignJwtConfig.class
)
public interface FriendshipClient {

    @GetMapping("/api/v1/internal/friends/blocked-between")
    ApiResponse<Boolean> isBlockedBetween(
            @RequestParam UUID user1,
            @RequestParam UUID user2
    );
}

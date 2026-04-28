package com.example.chat.modules.message.infrastructure.client;

import com.example.chat.config.FeignConfig;
import com.example.common.web.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
        name = "friendship-service",
        url = "${services.friendship.url}",
        configuration = FeignConfig.class
)
public interface FriendshipClient {

    @GetMapping("/api/v1/friends/status/{userId}")
    ApiResponse<String> getStatus(@PathVariable UUID userId);
}

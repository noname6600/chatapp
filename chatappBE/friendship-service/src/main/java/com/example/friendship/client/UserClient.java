package com.example.friendship.client;

import com.example.common.web.response.ApiResponse;
import com.example.friendship.config.FeignConfig;
import com.example.friendship.dto.UserProfileResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(
        name = "user-service",
    url = "${services.user.url:http://localhost:8082}",
        configuration = FeignConfig.class
)
public interface UserClient {

    @GetMapping("/api/v1/users/search")
    ApiResponse<UserProfileResponse> searchByUsername(@RequestParam("username") String username);
}

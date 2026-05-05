package com.example.chat.modules.message.infrastructure.client;

import com.example.common.feign.FeignJwtConfig;
import com.example.common.web.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(
        name = "user-service",
        url = "${services.user.url}",
        configuration = FeignJwtConfig.class
)
public interface UserClient {

    @PostMapping("/api/v1/users/bulk")
    ApiResponse<List<UserBasicProfile>> getUsersBulk(@RequestBody List<UUID> ids);
}

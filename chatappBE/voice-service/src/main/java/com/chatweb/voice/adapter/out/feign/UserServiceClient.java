package com.chatweb.voice.adapter.out.feign;

import com.chatweb.common.feign.FeignJwtConfig;
import com.chatweb.common.web.response.ApiResponse;
import com.chatweb.voice.adapter.out.feign.dto.UserSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "user-service", url = "${services.user.url}", configuration = FeignJwtConfig.class)
public interface UserServiceClient {

    @PostMapping("/api/v1/users/bulk")
    ApiResponse<List<UserSummaryResponse>> getUsersBatch(@RequestBody List<UUID> userIds);
}

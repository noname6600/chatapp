package com.chatweb.voice.adapter.out.feign;

import com.chatweb.voice.adapter.out.feign.dto.UserSummaryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "user-service", url = "${services.user.url}")
public interface UserServiceClient {

    @PostMapping("/api/v1/users/internal/batch")
    List<UserSummaryResponse> getUsersBatch(@RequestBody List<UUID> userIds);
}

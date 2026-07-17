package com.chatweb.voice.adapter.out.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "chat-service", url = "${services.chat.url}")
public interface ChatServiceClient {

    @GetMapping("/api/v1/rooms/{roomId}/internal/is-member")
    boolean isMember(
            @PathVariable UUID roomId,
            @RequestParam UUID userId,
            @RequestHeader("X-Internal-Auth") String internalAuthToken
    );
}

package com.chatweb.chat.modules.room.service.impl;

import com.chatweb.common.core.avatar.AvatarUrlGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GroupAvatarGenerator {

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    public String generate(UUID roomId, String roomName) {
        return AvatarUrlGenerator.generate(cloudName, roomId, roomName, "G");
    }
}

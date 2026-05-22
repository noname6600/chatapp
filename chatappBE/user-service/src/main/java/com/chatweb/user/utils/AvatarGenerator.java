package com.chatweb.user.utils;

import com.chatweb.common.core.avatar.AvatarUrlGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AvatarGenerator {

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    public String generate(UUID userId, String text) {
        return AvatarUrlGenerator.generate(cloudName, userId, text, "U");
    }
}

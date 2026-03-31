package com.example.chat.modules.room.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class GroupAvatarGenerator {

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    public String generate(UUID roomId, String roomName) {

        String initials = extractInitials(roomName);
        String safeInitials = URLEncoder.encode(initials, StandardCharsets.UTF_8);

        String color = colorFromRoomId(roomId);

        return String.format(
                "https://res.cloudinary.com/%s/image/upload/" +
                        "w_400,h_400,c_fill," +
                        "b_rgb:%s," +
                        "r_max," +
                        "l_text:Arial_140_bold:%s," +
                        "co_rgb:000000," +
                        "g_center/" +
                        "blank.png",
                cloudName,
                color,
                safeInitials
        );
    }

    private String extractInitials(String text) {
        if (text == null || text.isBlank()) return "G";

        String[] parts = text.trim().split("\\s+");

        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase();
        }

        return (parts[0].charAt(0) + "" + parts[1].charAt(0)).toUpperCase();
    }

    private String colorFromRoomId(UUID roomId) {

        String[] palette = {
                "FF6B6B",
                "FFD93D",
                "6BCB77",
                "4D96FF",
                "B983FF",
                "FF9F1C",
                "2EC4B6"
        };

        int hash = Math.abs(roomId.hashCode());
        int index = hash % palette.length;

        return palette[index];
    }
}

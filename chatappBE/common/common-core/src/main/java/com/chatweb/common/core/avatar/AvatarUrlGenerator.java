package com.chatweb.common.core.avatar;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class AvatarUrlGenerator {

    private static final String[] PALETTE = {
            "FF6B6B", "FFD93D", "6BCB77", "4D96FF", "B983FF", "FF9F1C", "2EC4B6"
    };

    private AvatarUrlGenerator() {}

    public static String generate(String cloudName, UUID id, String text, String defaultInitial) {
        String initials = extractInitials(text, defaultInitial);
        String safeInitials = URLEncoder.encode(initials, StandardCharsets.UTF_8);
        String color = colorFromId(id);
        return String.format(
                "https://res.cloudinary.com/%s/image/upload/" +
                        "w_400,h_400,c_fill," +
                        "b_rgb:%s," +
                        "r_max," +
                        "l_text:Arial_140_bold:%s," +
                        "co_rgb:000000," +
                        "g_center/" +
                        "blank.png",
                cloudName, color, safeInitials
        );
    }

    private static String extractInitials(String text, String defaultInitial) {
        if (text == null || text.isBlank()) return defaultInitial;
        String[] parts = text.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, 1).toUpperCase();
        return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
    }

    private static String colorFromId(UUID id) {
        return PALETTE[Math.abs(id.hashCode()) % PALETTE.length];
    }
}

package com.example.chat.modules.message.domain.service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MentionParser {

    private static final Pattern USER_MENTION_PATTERN =
            Pattern.compile("<@([0-9a-fA-F\\-]{36})>");

    private MentionParser() {}

    public static Set<UUID> parseUserIds(String content) {

        if (content == null || content.isBlank()) {
            return Set.of();
        }

        Matcher matcher = USER_MENTION_PATTERN.matcher(content);

        Set<UUID> ids = new HashSet<>();

        while (matcher.find()) {

            try {
                ids.add(UUID.fromString(matcher.group(1)));
            } catch (IllegalArgumentException ignored) {
            }

        }

        return ids;
    }
}
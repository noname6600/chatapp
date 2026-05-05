package com.example.common.event.validation;

import java.util.regex.Pattern;

/**
 * Shared validator for event-name and identity contract checks.
 * 
 * Enforces naming and identity rules used across transports:
 * - Event type values must follow the lowercase.dot.separated.hyphenated-names pattern
 * - Metadata must contain all required fields (eventId, eventType, sourceService, createdAt, correlationId)
 * 
 * Pattern explanation:
 * ^[a-z0-9-]+(\.[a-z0-9-]+)*$
 * - Starts with lowercase letters, numbers, or hyphens
 * - Can contain dots as separators
 * - Each segment can contain lowercase letters, numbers, or hyphens
 * - Underscores are NOT allowed
 * - UPPERCASE letters are NOT allowed
 * - Spaces are NOT allowed
 * 
 * Valid examples:
 * - chat.message.sent
 * - presence.user.status-changed
 * - notification.email-sent
 * - account.created
 * 
 * Invalid examples (will fail validation):
 * - chat.message_sent (underscore not allowed)
 * - Chat.message.sent (uppercase not allowed)
 * - presence.user.status_changed (underscore not allowed)
 * 
 * @since 2.0
 */
public final class EventContractValidator {
    
    /**
     * Regex pattern for valid event type values.
     * 
     * Enforces: lowercase letters, numbers, hyphens, dots only.
     * Underscores and uppercase letters are not permitted.
     */
    public static final String EVENT_NAME_PATTERN = "^[a-z0-9-]+(\\.[a-z0-9-]+)*$";
    
    private static final Pattern PATTERN = Pattern.compile(EVENT_NAME_PATTERN);

    private EventContractValidator() {
    }

    public static void validateEventNameOrThrow(String eventName) {
        if (eventName == null || eventName.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (!PATTERN.matcher(eventName).matches()) {
            throw new IllegalArgumentException("eventType must match lower-dot convention: " + eventName);
        }
    }

    public static void validateIdentityOrThrow(String eventId, String correlationId, String sourceService, java.time.Instant createdAt) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId is required");
        }
        if (sourceService == null || sourceService.isBlank()) {
            throw new IllegalArgumentException("sourceService is required");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt is required");
        }
    }
    
    /**
     * Validates that an event type string matches the required pattern.
     *
     * @param eventType the event type string to validate
     * @return {@code true} if the event type is non-null and matches the pattern,
     *         {@code false} if eventType is null or does not match
     */
    public static boolean isValidEventType(String eventType) {
        if (eventType == null) {
            return false;
        }
        return PATTERN.matcher(eventType).matches();
    }
}

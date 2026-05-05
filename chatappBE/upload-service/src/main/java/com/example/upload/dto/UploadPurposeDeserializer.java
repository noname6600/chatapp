package com.example.upload.dto;

import com.example.upload.domain.UploadPurpose;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;

import java.io.IOException;
import java.util.Locale;

public class UploadPurposeDeserializer extends JsonDeserializer<UploadPurpose> {

    @Override
    public UploadPurpose deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String raw = parser.getValueAsString();
        if (raw == null) {
            return null;
        }

        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        for (UploadPurpose purpose : UploadPurpose.values()) {
            if (purpose.name().equalsIgnoreCase(normalized) || purpose.value().equalsIgnoreCase(normalized)) {
                return purpose;
            }
        }

        String compact = normalized.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
        if ("avatar".equals(compact) || "useravatar".equals(compact)) {
            return UploadPurpose.USER_AVATAR;
        }
        if ("attachment".equals(compact) || "chatattachment".equals(compact)) {
            return UploadPurpose.CHAT_ATTACHMENT;
        }

        throw JsonMappingException.from(parser, "Unsupported purpose value: " + raw);
    }
}

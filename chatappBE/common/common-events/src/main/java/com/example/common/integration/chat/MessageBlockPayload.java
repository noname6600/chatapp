package com.example.common.integration.chat;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public final class MessageBlockPayload {

    private final String type;

    private final String text;

    private final AttachmentPayload attachment;

    @JsonCreator
    public MessageBlockPayload(
            @JsonProperty("type") String type,
            @JsonProperty("text") String text,
            @JsonProperty("attachment") AttachmentPayload attachment
    ) {
        this.type = type;
        this.text = text;
        this.attachment = attachment;
    }
}
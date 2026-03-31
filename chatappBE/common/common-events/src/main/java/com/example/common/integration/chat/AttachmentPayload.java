package com.example.common.integration.chat;


import com.example.common.integration.enums.AttachmentType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@Builder
public final class AttachmentPayload {

    private final UUID id;

    private final AttachmentType type;

    private final String url;

    private final String publicId;

    private final String fileName;

    private final Long size;

    private final Integer width;

    private final Integer height;

    private final Integer duration;

    @JsonCreator
    public AttachmentPayload(
            @JsonProperty("id") UUID id,
            @JsonProperty("type") AttachmentType type,
            @JsonProperty("url") String url,
            @JsonProperty("publicId") String publicId,
            @JsonProperty("fileName") String fileName,
            @JsonProperty("size") Long size,
            @JsonProperty("width") Integer width,
            @JsonProperty("height") Integer height,
            @JsonProperty("duration") Integer duration
    ) {
        this.id = id;
        this.type = type;
        this.url = url;
        this.publicId = publicId;
        this.fileName = fileName;
        this.size = size;
        this.width = width;
        this.height = height;
        this.duration = duration;
    }
}

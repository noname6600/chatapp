package com.example.chat.modules.message.domain.model;


import com.example.chat.modules.message.domain.enums.AttachmentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AttachmentDraft {

    private final AttachmentType type;
    private final String url;

    private final String publicId;
    private final String fileName;

    private final Long size;
    private final Integer width;
    private final Integer height;
    private final Integer duration;
}

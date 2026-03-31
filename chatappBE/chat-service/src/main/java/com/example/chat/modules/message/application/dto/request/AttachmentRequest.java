package com.example.chat.modules.message.application.dto.request;


import com.example.chat.modules.message.domain.enums.AttachmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttachmentRequest {

    @NotNull(message = "type is required")
    private AttachmentType type;

    @NotBlank(message = "url is required")
    private String url;

    @NotBlank(message = "publicId is required")
    private String publicId;

    private String fileName;

    @Positive(message = "size must be positive")
    private Long size;

    @Positive(message = "width must be positive")
    private Integer width;

    @Positive(message = "height must be positive")
    private Integer height;

    @Positive(message = "duration must be positive")
    private Integer duration;

}

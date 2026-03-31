package com.example.chat.modules.message.application.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReactionResponse {

    private String emoji;

    private long count;

    private boolean reactedByMe;

}

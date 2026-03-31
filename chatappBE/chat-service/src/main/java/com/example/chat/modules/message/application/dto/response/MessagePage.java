package com.example.chat.modules.message.application.dto.response;

import lombok.*;

import java.util.List;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MessagePage {

    private List<MessageResponse> messages;

    private boolean hasMore;

}

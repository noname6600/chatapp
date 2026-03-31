package com.example.chat.modules.message.domain.service;

import java.util.UUID;

public interface IMessageSequenceService {

    long nextSeq(UUID roomId);

}

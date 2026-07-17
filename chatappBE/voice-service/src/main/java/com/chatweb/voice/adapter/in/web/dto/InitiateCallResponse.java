package com.chatweb.voice.adapter.in.web.dto;

import java.util.UUID;

public record InitiateCallResponse(UUID callId, String status) {}

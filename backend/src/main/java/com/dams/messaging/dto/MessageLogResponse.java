package com.dams.messaging.dto;

import com.dams.messaging.entity.MessageLog;

import java.time.Instant;

public record MessageLogResponse(
    Long id,
    String channel,
    String toPhone,
    String templateCode,
    String body,
    String status,
    String relatedType,
    Long relatedId,
    String error,
    Instant createdAt
) {
    public static MessageLogResponse of(MessageLog m) {
        return new MessageLogResponse(
            m.getId(), m.getChannel(), m.getToPhone(), m.getTemplateCode(), m.getBody(),
            m.getStatus(), m.getRelatedType(), m.getRelatedId(), m.getError(), m.getCreatedAt());
    }
}

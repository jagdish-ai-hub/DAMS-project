package com.dams.messaging.dto;

import com.dams.messaging.entity.MessageTemplate;

public record MessageTemplateResponse(
    Long id,
    String code,
    String channel,
    String body,
    boolean active
) {
    public static MessageTemplateResponse of(MessageTemplate t) {
        return new MessageTemplateResponse(
            t.getId(), t.getCode(), t.getChannel(), t.getBody(), t.isActive());
    }
}

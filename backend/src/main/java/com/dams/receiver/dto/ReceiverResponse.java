package com.dams.receiver.dto;

import com.dams.receiver.entity.Receiver;

import java.time.Instant;

public record ReceiverResponse(
    Long id,
    String name,
    String phone,
    boolean active,
    Instant createdAt
) {
    public static ReceiverResponse of(Receiver r) {
        return new ReceiverResponse(r.getId(), r.getName(), r.getPhone(), r.isActive(), r.getCreatedAt());
    }
}

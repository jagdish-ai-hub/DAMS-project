package com.dams.jobcard.dto;

import com.dams.jobcard.entity.ClaimAction;

import java.time.Instant;
import java.time.LocalDate;

public record ClaimActionResponse(
    Long id,
    Long jobCardId,
    String jobCardReference,
    String action,
    Long ownerUserId,
    String ownerName,
    LocalDate dueDate,
    boolean overdue,
    Instant doneAt,
    Long createdBy,
    Instant createdAt
) {
    public static ClaimActionResponse of(ClaimAction a, String jobCardReference,
                                         String ownerName, boolean overdue) {
        return new ClaimActionResponse(
            a.getId(), a.getJobCardId(), jobCardReference, a.getAction(),
            a.getOwnerUserId(), ownerName, a.getDueDate(), overdue,
            a.getDoneAt(), a.getCreatedBy(), a.getCreatedAt());
    }
}

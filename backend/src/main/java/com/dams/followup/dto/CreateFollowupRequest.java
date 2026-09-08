package com.dams.followup.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Open (or re-promise) a collection follow-up on a receive document. */
@Getter
@Setter
@NoArgsConstructor
public class CreateFollowupRequest {

    @NotNull(message = "receiveDocumentId is required")
    private Long receiveDocumentId;

    @NotNull(message = "dueDate is required")
    private LocalDate dueDate;

    @Size(max = 500, message = "Promise note must be at most 500 characters")
    private String promiseNote;
}

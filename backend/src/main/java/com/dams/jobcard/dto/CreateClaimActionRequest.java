package com.dams.jobcard.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Record the next step on an open claim: what, who owns it, by when. */
@Getter
@Setter
@NoArgsConstructor
public class CreateClaimActionRequest {

    @NotNull(message = "jobCardId is required")
    private Long jobCardId;

    @NotBlank(message = "action is required")
    @Size(max = 500, message = "Action must be at most 500 characters")
    private String action;

    private Long ownerUserId;

    @NotNull(message = "dueDate is required")
    private LocalDate dueDate;
}

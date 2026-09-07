package com.dams.cash.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Cashier asks for a locked day to be reopened — the reason is mandatory. */
@Getter
@Setter
@NoArgsConstructor
public class CreateReopenRequest {

    @NotNull(message = "closeDate is required")
    private LocalDate closeDate;

    @NotBlank(message = "A reason is required to request a reopen")
    @Size(max = 500, message = "Reason must be at most 500 characters")
    private String reason;
}

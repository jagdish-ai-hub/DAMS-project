package com.dams.cash.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** The Owner reopens a branch's latest closed cash day (rev 60). The reason is mandatory. */
@Getter
@Setter
@NoArgsConstructor
public class ReopenDayRequest {

    @NotNull(message = "branchId is required")
    private Long branchId;

    @NotNull(message = "closeDate is required")
    private LocalDate closeDate;

    @NotBlank(message = "A reason is required to reopen a closed day")
    @Size(max = 300, message = "Reason must be at most 300 characters")
    private String reason;
}

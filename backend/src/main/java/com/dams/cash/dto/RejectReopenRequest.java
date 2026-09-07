package com.dams.cash.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** FM rejects a reopen request — the reason is kept on the row for the cashier to see. */
@Getter
@Setter
@NoArgsConstructor
public class RejectReopenRequest {

    @NotBlank(message = "A reason is required to reject a reopen request")
    @Size(max = 500, message = "Reason must be at most 500 characters")
    private String reason;
}

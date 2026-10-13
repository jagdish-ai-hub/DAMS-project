package com.dams.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** rev 76 — sending an expense for Finance Manager review needs the cashier's reason. */
@Getter
@Setter
@NoArgsConstructor
public class RequestApprovalRequest {
    @NotBlank(message = "Write why this expense is needed")
    @Size(max = 500, message = "The reason must be at most 500 characters")
    private String reason;
}

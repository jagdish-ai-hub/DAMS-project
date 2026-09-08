package com.dams.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Create/update payload for a customer. Phone is optional. */
@Getter
@Setter
@NoArgsConstructor
public class CustomerRequest {

    @NotBlank(message = "Customer name is required")
    @Size(max = 160, message = "Customer name must be at most 160 characters")
    private String name;

    @Size(max = 32, message = "Phone must be at most 32 characters")
    private String phone;

    /**
     * B2B ceiling (FEAT-46). Null = no limit. Warn-first in v1: never blocks
     * posting, but the counter sees exposure-vs-limit. Owner/FM/Accountant
     * writable via the same update path.
     */
    private java.math.BigDecimal creditLimit;
}

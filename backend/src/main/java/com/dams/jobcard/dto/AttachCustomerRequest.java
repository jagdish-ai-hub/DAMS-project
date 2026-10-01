package com.dams.jobcard.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Attach a customer to a job card that has none: an existing {@code customerId}, or a new {@code customerName}. */
@Getter
@Setter
@NoArgsConstructor
public class AttachCustomerRequest {

    private Long customerId;

    @Size(max = 160, message = "Customer name must be at most 160 characters")
    private String customerName;

    @Size(max = 32, message = "Phone must be at most 32 characters")
    private String customerPhone;
}

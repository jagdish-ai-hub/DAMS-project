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
}

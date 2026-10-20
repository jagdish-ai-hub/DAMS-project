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

    /** rev 77 — null leaves the saved address alone; an empty string clears it. */
    @Size(max = 500, message = "Address must be at most 500 characters")
    private String address;

    /** rev 84 — null leaves the saved GST No alone; an empty string clears it. Stored trimmed and upper-cased. */
    @Size(max = 15, message = "GST No must be at most 15 characters")
    private String gstNo;
}

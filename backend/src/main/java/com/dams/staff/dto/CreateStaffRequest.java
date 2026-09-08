package com.dams.staff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Add (or reactivate) a staff member on the advance ledger. */
@Getter
@Setter
@NoArgsConstructor
public class CreateStaffRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 200, message = "Name must be at most 200 characters")
    private String name;

    @Size(max = 20, message = "Phone must be at most 20 characters")
    private String phone;
}

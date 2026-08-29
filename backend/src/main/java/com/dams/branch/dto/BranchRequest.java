package com.dams.branch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Create/update payload for a branch. `code` is normalised to upper-case server-side;
 * uniqueness is per-org, checked in the service.
 */
@Getter
@Setter
@NoArgsConstructor
public class BranchRequest {

    @NotBlank(message = "Branch name is required")
    @Size(max = 120, message = "Branch name must be at most 120 characters")
    private String name;

    @NotBlank(message = "Branch code is required")
    @Pattern(regexp = "^[A-Za-z0-9]{2,5}$", message = "Branch code must be 2–5 letters or digits")
    private String code;

    /** Optional on update; ignored on create (new branches are active). */
    private Boolean active;
}

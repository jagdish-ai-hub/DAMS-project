package com.dams.user.dto;

import com.dams.user.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Create/update payload for a team member.
 *
 * Branch binding depends on {@code role}:
 *   OWNER / FINANCE_MANAGER — org-wide, no branch fields
 *   ACCOUNTANT              — {@code branchIds} (one or more)
 *   CASHIER                 — {@code homeBranchId} (exactly one)
 *
 * {@code email} is set on create only (it's the login) and ignored on update.
 * An Owner cannot create a SUPER_ADMIN.
 */
@Getter
@Setter
@NoArgsConstructor
public class UserRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;

    @Email(message = "A valid email is required")
    @NotBlank(message = "Email is required")
    private String email;

    @NotNull(message = "Role is required")
    private Role role;

    private Long homeBranchId;

    private List<Long> branchIds;

    /** Update only — ignored on create (new users start active). */
    private Boolean active;
}

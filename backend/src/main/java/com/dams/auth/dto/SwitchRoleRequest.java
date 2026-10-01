package com.dams.auth.dto;

import com.dams.user.entity.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Switch into {@code role} at {@code branchId}. Naming the user's own primary role switches
 * back to it (the branch is then ignored and may be omitted).
 */
@Getter
@Setter
@NoArgsConstructor
public class SwitchRoleRequest {

    @NotNull(message = "Role is required")
    private Role role;

    private Long branchId;
}

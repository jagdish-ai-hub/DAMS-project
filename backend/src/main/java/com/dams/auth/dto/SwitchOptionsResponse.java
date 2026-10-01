package com.dams.auth.dto;

import com.dams.user.entity.Role;

import java.util.List;

/**
 * What the Switch role picker offers: pick a branch, then one of its {@code roles}.
 * {@code primaryRole} is the user's own role (the "switch back" target); {@code actingRole} /
 * {@code actingBranchId} say where they are now (equal to primary / null when not switched).
 */
public record SwitchOptionsResponse(
    Role primaryRole,
    Role actingRole,
    Long actingBranchId,
    List<BranchOption> branches
) {

    /**
     * One branch and the roles the caller may take there. {@code FINANCE_MANAGER} is org-wide, so
     * it is listed under every branch — the Finance Manager still sees all branches while acting.
     */
    public record BranchOption(Long branchId, String code, String name, List<Role> roles) {
    }
}

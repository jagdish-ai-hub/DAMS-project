package com.dams.auth.dto;

import com.dams.user.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Returned by /auth/login, /auth/accept-invite and /auth/switch-role.
 * One access token only — no refresh token in v1 (see plan.md rev 3).
 *
 * {@code role} is the ACTING role (what the token authorises). {@code primaryRole} is the user's
 * own role — they differ only while the user has switched (plan.md rev 55), in which case
 * {@code actingBranchId} is the one branch the session is scoped to.
 */
@Getter
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;
    private Role role;
    private Long orgId;         // null for SUPER_ADMIN
    private Long homeBranchId;  // set for CASHIER only; null otherwise
    private String name;
    private Role primaryRole;
    private Long actingBranchId;    // null unless switched
    private boolean canSwitchRole;  // Owner always; others only with an Owner-granted extra role

    /** A user in their own role who cannot switch — the pre-rev-55 shape. */
    public LoginResponse(String accessToken, Role role, Long orgId, Long homeBranchId, String name) {
        this(accessToken, role, orgId, homeBranchId, name, role, null, false);
    }
}

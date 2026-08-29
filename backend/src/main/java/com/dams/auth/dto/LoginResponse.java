package com.dams.auth.dto;

import com.dams.user.entity.Role;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Returned by /auth/login and /auth/accept-invite.
 * One access token only — no refresh token in v1 (see plan.md rev 3).
 */
@Getter
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;
    private Role role;
    private Long orgId;         // null for SUPER_ADMIN
    private Long homeBranchId;  // set for CASHIER only; null otherwise
    private String name;
}

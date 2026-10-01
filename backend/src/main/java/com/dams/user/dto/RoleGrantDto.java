package com.dams.user.dto;

import com.dams.user.entity.Role;

import java.util.List;

/**
 * An extra role a user may switch into (plan.md rev 55), as sent by the Owner and echoed back.
 * {@code FINANCE_MANAGER} is org-wide so its {@code branchIds} is empty / ignored;
 * {@code ACCOUNTANT} and {@code CASHIER} name every branch the role is granted at.
 */
public record RoleGrantDto(Role role, List<Long> branchIds) {
}

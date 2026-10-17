package com.dams.user.dto;

import com.dams.user.entity.Role;

import java.util.List;

/**
 * An extra role a user may switch into (plan.md rev 55), as sent by the Owner and echoed back.
 * Every switchable role lists the branches it is granted at, including
 * {@code FINANCE_MANAGER}.
 */
public record RoleGrantDto(Role role, List<Long> branchIds) {
}

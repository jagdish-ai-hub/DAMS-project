package com.dams.user.dto;

import com.dams.user.entity.Role;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Team member as shown in the Owner "Users" table.
 * {@code invitePending} = the user has an unaccepted invite (no password yet).
 * {@code branchAccessLabel} is the human summary the table shows.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserResponse(
    Long id,
    String name,
    String email,
    Role role,
    boolean active,
    boolean invitePending,
    Long homeBranchId,
    List<Long> branchIds,
    String branchAccessLabel,
    Instant createdAt,
    String inviteLink   // only populated on create
) {
}

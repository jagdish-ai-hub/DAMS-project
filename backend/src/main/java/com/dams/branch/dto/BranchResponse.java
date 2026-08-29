package com.dams.branch.dto;

import com.dams.branch.entity.Branch;

import java.time.Instant;

/**
 * Branch as shown in the Owner "Team & Branches" table — includes the assigned-user
 * count the mockup displays.
 */
public record BranchResponse(
    Long id,
    String name,
    String code,
    boolean active,
    long assignedUserCount,
    Instant createdAt
) {
    public static BranchResponse of(Branch b, long assignedUserCount) {
        return new BranchResponse(b.getId(), b.getName(), b.getCode(), b.isActive(), assignedUserCount, b.getCreatedAt());
    }
}

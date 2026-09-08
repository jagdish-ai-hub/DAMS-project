package com.dams.organization.dto;

import com.dams.organization.entity.Organization;

import java.time.Instant;

public record OrgSettingsResponse(
    Long id,
    String name,
    boolean multiBranchCashierAccess,
    java.math.BigDecimal cashVarianceCountersignThreshold,
    boolean digestEnabled,
    boolean active,
    Instant createdAt
) {
    public static OrgSettingsResponse of(Organization o) {
        return new OrgSettingsResponse(
            o.getId(), o.getName(), o.isMultiBranchCashierAccess(),
            o.getCashVarianceCountersignThreshold(), o.isDigestEnabled(),
            o.isActive(), o.getCreatedAt());
    }
}

package com.dams.organization.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Owner-editable settings for their own organization. Both fields optional on PATCH. */
@Getter
@Setter
@NoArgsConstructor
public class OrgSettingsRequest {

    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;

    /**
     * When true, cashiers can search/see customers and job cards across all branches.
     * Never changes which branch a cashier's documents post under. Default OFF.
     */
    private Boolean multiBranchCashierAccess;

    /**
     * |Cash variance| above this closes PENDING countersign instead of locking
     * clean (FEAT-41). Null/zero disables the feature (existing behaviour).
     */
    private java.math.BigDecimal cashVarianceCountersignThreshold;

    /**
     * Nightly owner daybook via message (FEAT-42). Explicit opt-in — pushing
     * WhatsApp to an owner who never asked is spam, not a feature.
     */
    private Boolean digestEnabled;
}

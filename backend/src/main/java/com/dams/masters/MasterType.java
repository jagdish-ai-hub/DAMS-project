package com.dams.masters;

import com.dams.common.exception.DamsException;

import java.util.Arrays;

/**
 * The eight Owner-editable master lists, addressed by URL slug:
 *   /api/v1/masters/{slug}
 * Each maps to one table/entity. `hasClaimFlag` / `hasModeFlags` /
 * `hasClaimTriggerFlag` / `isExpenseSubCategory` tell the service which request fields apply.
 */
public enum MasterType {

    RECEIVE_CATEGORIES("receive-categories"),
    RECEIVE_STATUSES("receive-statuses"),
    SETTLEMENT_MODES("settlement-modes"),
    EXPENSE_CATEGORIES("expense-categories"),
    EXPENSE_SUB_CATEGORIES("expense-sub-categories"),
    EXPENSE_MODES("expense-modes"),
    EXPENSE_STATUSES("expense-statuses"),
    BANKS("banks");

    private final String slug;

    MasterType(String slug) {
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }

    public boolean hasClaimFlag() {
        return this == RECEIVE_CATEGORIES;
    }

    /** requires_bank / requires_ref — carried by both settlement and expense modes. */
    public boolean hasModeFlags() {
        return this == SETTLEMENT_MODES || this == EXPENSE_MODES;
    }

    /** triggers_claim — the expense business status that moves an expense onto a claim. */
    public boolean hasClaimTriggerFlag() {
        return this == EXPENSE_STATUSES;
    }

    public boolean isExpenseSubCategory() {
        return this == EXPENSE_SUB_CATEGORIES;
    }

    public static MasterType fromSlug(String slug) {
        return Arrays.stream(values())
            .filter(t -> t.slug.equals(slug))
            .findFirst()
            .orElseThrow(() -> DamsException.notFound("Master list", "type", slug));
    }
}

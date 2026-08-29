package com.dams.masters;

import com.dams.common.exception.DamsException;

import java.util.Arrays;

/**
 * The eight Owner-editable master lists, addressed by URL slug:
 *   /api/v1/masters/{slug}
 * Each maps to one table/entity. `hasClaimFlag` / `hasModeFlags` / `isExpenseSubCategory`
 * tell the service which request fields apply.
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

    public boolean hasModeFlags() {
        return this == SETTLEMENT_MODES;
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

package com.dams.user.entity;

/**
 * Role hierarchy for DAMS. Stored as a string in the database (not a PG enum)
 * so Flyway migrations stay simple and role additions don't require DDL.
 *
 * Hierarchy: SUPER_ADMIN > OWNER > FINANCE_MANAGER > ACCOUNTANT > CASHIER.
 * INVENTORY_MANAGER and STORE_KEEPER (rev 91) belong to the Spare Parts module, not to the
 * finance hierarchy: they see stock in every branch and act at their assigned branches only.
 * See AGENT.md for full role descriptions.
 */
public enum Role {
    SUPER_ADMIN,
    OWNER,
    FINANCE_MANAGER,
    ACCOUNTANT,
    CASHIER,
    INVENTORY_MANAGER,
    STORE_KEEPER;

    /**
     * True for the roles whose branches are stored as user_branch_access rows (one or more
     * branches): the two reviewers and, since rev 91, the two Spare Parts roles.
     * CASHIER has exactly one home branch; OWNER and SUPER_ADMIN have none.
     */
    public boolean usesBranchAccessRows() {
        return this == ACCOUNTANT || this == FINANCE_MANAGER || isSparePartsRole();
    }

    /** The two Spare Parts module roles (rev 91). */
    public boolean isSparePartsRole() {
        return this == INVENTORY_MANAGER || this == STORE_KEEPER;
    }
}

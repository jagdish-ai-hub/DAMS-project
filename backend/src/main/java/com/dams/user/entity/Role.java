package com.dams.user.entity;

/**
 * Role hierarchy for DAMS. Stored as a string in the database (not a PG enum)
 * so Flyway migrations stay simple and role additions don't require DDL.
 *
 * Hierarchy: SUPER_ADMIN > OWNER > FINANCE_MANAGER > ACCOUNTANT > CASHIER
 * See AGENT.md for full role descriptions.
 */
public enum Role {
    SUPER_ADMIN,
    OWNER,
    FINANCE_MANAGER,
    ACCOUNTANT,
    CASHIER
}

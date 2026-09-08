package com.dams.user.entity;

/**
 * Role hierarchy for DAMS. Stored as a string in the database (not a PG enum)
 * so Flyway migrations stay simple and role additions don't require DDL.
 *
 * Hierarchy: SUPER_ADMIN > OWNER > FINANCE_MANAGER > ACCOUNTANT > CASHIER.
 * AUDITOR sits outside the hierarchy: org-wide read-only (dashboard, audit,
 * masters, follow-ups, exports) with zero write access anywhere. See AGENT.md.
 */
public enum Role {
    SUPER_ADMIN,
    OWNER,
    FINANCE_MANAGER,
    ACCOUNTANT,
    CASHIER,
    AUDITOR
}

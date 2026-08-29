-- V1: Organization, AppUser, UserBranchAccess
-- These are the platform-level tables. org_id is NULL for SUPER_ADMIN.

CREATE TABLE organization (
    id                          BIGSERIAL    PRIMARY KEY,
    name                        VARCHAR(255) NOT NULL,
    multi_branch_cashier_access BOOLEAN      NOT NULL DEFAULT FALSE,
    active                      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at                  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Use app_user not "user" — "user" is a reserved word in PostgreSQL
CREATE TABLE app_user (
    id                BIGSERIAL    PRIMARY KEY,
    org_id            BIGINT       REFERENCES organization(id) ON DELETE RESTRICT,
    -- The one branch every document a CASHIER creates posts under. Exactly one, mandatory
    -- for CASHIER, NULL for every other role. FK to branch(id) added in V2 once that table exists.
    -- The org-level multi_branch_cashier_access toggle changes only what a cashier can search/see,
    -- never which branch their documents post under. See AGENT.md / plan.md locked decisions.
    home_branch_id    BIGINT,
    name              VARCHAR(255) NOT NULL,
    email             VARCHAR(255) NOT NULL UNIQUE,
    password_hash     VARCHAR(255),
    role              VARCHAR(50)  NOT NULL,  -- SUPER_ADMIN / OWNER / FINANCE_MANAGER / ACCOUNTANT / CASHIER
    active            BOOLEAN      NOT NULL DEFAULT TRUE,
    invite_token      VARCHAR(255),
    invite_expires_at TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- branch_id references branch table (created in V2).
-- Constraint added in V2 once the branch table exists.
-- Only ACCOUNTANT rows live here; CASHIER uses app_user.home_branch_id, and FM/OWNER are org-wide.
CREATE TABLE user_branch_access (
    user_id   BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    branch_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, branch_id)
);

-- Seed: the platform SUPER_ADMIN. Password 'Welcome12345@' (bcrypt, cost 10).
-- Change it after first login via POST /api/v1/auth/change-password (Settings screen).
-- This is the only credential seeded in V1. The dummy dealership + one login per role
-- are seeded in V5 (test phase only). Real organizations use the email/invite flow.
INSERT INTO app_user (name, email, password_hash, role, active)
VALUES (
    'Super Admin',
    'dams@jjsoftware.com',
    '$2b$10$fXHBCMNqhko2nC4ZwE7JKuj8AzSdhG2kVjVgR/cE1IrwesYQGCxqu',
    'SUPER_ADMIN',
    TRUE
);

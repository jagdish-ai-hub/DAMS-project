-- rev 55: multi-role users. The Owner grants a user extra roles they may "switch into".
--
--   user_role_grant   one row per (user, extra role, branch). FINANCE_MANAGER is org-wide, so
--                     its row has branch_id NULL; ACCOUNTANT / CASHIER rows name a branch.
--                     Never for OWNER / SUPER_ADMIN, and never the user's own primary role
--                     (enforced in UserService). The Owner needs no rows — the Owner may act
--                     as any role at any branch.
--   audit_event.actor_role  set only while the actor is acting in a switched role, so history
--                     can read "Ajay · as Cashier". NULL = acting in their own role.

CREATE TABLE user_role_grant (
    id         BIGSERIAL PRIMARY KEY,
    org_id     BIGINT      NOT NULL REFERENCES organization(id),
    user_id    BIGINT      NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role       VARCHAR(50) NOT NULL,
    branch_id  BIGINT      REFERENCES branch(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT user_role_grant_role_chk CHECK (role IN ('FINANCE_MANAGER', 'ACCOUNTANT', 'CASHIER')),
    CONSTRAINT user_role_grant_branch_chk CHECK ((role = 'FINANCE_MANAGER') = (branch_id IS NULL)),
    CONSTRAINT user_role_grant_uq UNIQUE NULLS NOT DISTINCT (user_id, role, branch_id)
);

CREATE INDEX idx_user_role_grant_user ON user_role_grant (user_id);

ALTER TABLE audit_event ADD COLUMN actor_role VARCHAR(50);

ALTER TABLE audit_event DROP CONSTRAINT audit_event_event_type_chk;
ALTER TABLE audit_event ADD CONSTRAINT audit_event_event_type_chk CHECK (event_type IN (
    'CREATED', 'SUBMITTED', 'VERIFIED', 'APPROVED', 'QUERIED', 'REJECTED',
    'CLOSED', 'OVERRIDE', 'LINE_ADDED', 'SETTLED', 'CATEGORY_CHANGED', 'CLAIM_TYPE_CHANGED',
    'TRANSFERRED_TO_CLAIM', 'APPROVAL_REQUESTED', 'PRE_APPROVED', 'ROLE_SWITCHED'
));

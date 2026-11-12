-- rev 94: Spare Parts module (docs/modules/spare-parts.md). ADD-ONLY: creates the module's own
-- inv_* tables, one switch column on organization, and widens two CHECK lists. No existing row
-- is changed and no existing table loses anything, so the live Finance flows are unaffected.
--
--   organization.spare_parts_enabled   Super Admin's per-organization switch, OFF by default.
--   inv_part               part master per organization (code unique per org)
--   inv_stock              quantity per branch + part (the running total of the ledger)
--   inv_movement           the stock LEDGER: append-only signed rows; quantity is never edited
--   inv_import             one row per confirmed Excel upload
--   inv_transfer           a branch-to-branch request: REQUESTED -> SENT -> RECEIVED
--   inv_role_permission    the Owner-editable permission grid per module role
--
-- Two new roles (INVENTORY_MANAGER, STORE_KEEPER) are stored as plain strings in app_user.role
-- (no CHECK there). They may also be granted as extra roles, so the grant CHECK is widened.

ALTER TABLE organization ADD COLUMN spare_parts_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- The new roles may be granted to a user as extra (switchable) roles, per branch.
ALTER TABLE user_role_grant DROP CONSTRAINT user_role_grant_role_chk;
ALTER TABLE user_role_grant ADD CONSTRAINT user_role_grant_role_chk CHECK (role IN (
    'FINANCE_MANAGER', 'ACCOUNTANT', 'CASHIER', 'INVENTORY_MANAGER', 'STORE_KEEPER'
));

-- One new audit event: the Owner changing what the Spare Parts roles may do. (Super Admin's own
-- on/off switch is written to the server log, not the audit table: audit rows need an org context.)
ALTER TABLE audit_event DROP CONSTRAINT audit_event_event_type_chk;
ALTER TABLE audit_event ADD CONSTRAINT audit_event_event_type_chk CHECK (event_type IN (
    'CREATED', 'SUBMITTED', 'VERIFIED', 'APPROVED', 'QUERIED', 'REJECTED',
    'CLOSED', 'OVERRIDE', 'LINE_ADDED', 'SETTLED', 'CATEGORY_CHANGED', 'CLAIM_TYPE_CHANGED',
    'TRANSFERRED_TO_CLAIM', 'APPROVAL_REQUESTED', 'PRE_APPROVED', 'ROLE_SWITCHED',
    'JOB_CARD_CUSTOMER_ATTACHED', 'STATUS_CHANGED', 'CASH_REOPENED', 'VEHICLE_MOVED',
    'SPARE_PERMISSIONS_CHANGED'
));

CREATE TABLE inv_part (
    id          BIGSERIAL     PRIMARY KEY,
    org_id      BIGINT        NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    part_code   VARCHAR(40)   NOT NULL,           -- normalised: upper-case, no spaces
    description VARCHAR(255)  NOT NULL,
    unit_price  NUMERIC(12,2),                    -- org-wide; NULL when not known
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    CONSTRAINT inv_part_org_code_uq UNIQUE (org_id, part_code),
    CONSTRAINT inv_part_price_chk   CHECK (unit_price IS NULL OR unit_price >= 0)
);

CREATE TABLE inv_stock (
    id         BIGSERIAL    PRIMARY KEY,
    org_id     BIGINT       NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    branch_id  BIGINT       NOT NULL REFERENCES branch(id)       ON DELETE RESTRICT,
    part_id    BIGINT       NOT NULL REFERENCES inv_part(id)     ON DELETE RESTRICT,
    bin        VARCHAR(60),                       -- storage bin / shelf, free text
    quantity   INTEGER      NOT NULL DEFAULT 0,   -- running total of inv_movement for this row
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT inv_stock_branch_part_uq UNIQUE (branch_id, part_id),
    CONSTRAINT inv_stock_qty_chk        CHECK (quantity >= 0)
);

CREATE INDEX inv_stock_org_idx  ON inv_stock (org_id);
CREATE INDEX inv_stock_part_idx ON inv_stock (part_id);

CREATE TABLE inv_import (
    id            BIGSERIAL    PRIMARY KEY,
    org_id        BIGINT       NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    file_name     VARCHAR(255) NOT NULL,
    mode          VARCHAR(10)  NOT NULL,          -- ADD = add to stock, SET = set stock to the file
    rows_total    INTEGER      NOT NULL,
    rows_posted   INTEGER      NOT NULL,
    rows_rejected INTEGER      NOT NULL,
    created_by    BIGINT       REFERENCES app_user(id) ON DELETE SET NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT inv_import_mode_chk CHECK (mode IN ('ADD', 'SET'))
);

CREATE INDEX inv_import_org_idx ON inv_import (org_id);

CREATE TABLE inv_transfer (
    id               BIGSERIAL    PRIMARY KEY,
    org_id           BIGINT       NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    from_branch_id   BIGINT       NOT NULL REFERENCES branch(id) ON DELETE RESTRICT,  -- supplies the part
    to_branch_id     BIGINT       NOT NULL REFERENCES branch(id) ON DELETE RESTRICT,  -- asked for it
    part_id          BIGINT       NOT NULL REFERENCES inv_part(id) ON DELETE RESTRICT,
    qty_requested    INTEGER      NOT NULL,
    qty_sent         INTEGER,                      -- set when the supplying branch marks Sent
    status           VARCHAR(12)  NOT NULL DEFAULT 'REQUESTED',
    job_card_no      VARCHAR(40),                  -- optional: the job the part is for (typed text)
    note             VARCHAR(500),
    decline_reason   VARCHAR(500),                 -- also holds the cancel reason
    requested_by     BIGINT       REFERENCES app_user(id) ON DELETE SET NULL,
    requested_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    sent_by          BIGINT       REFERENCES app_user(id) ON DELETE SET NULL,
    sent_at          TIMESTAMPTZ,
    received_by      BIGINT       REFERENCES app_user(id) ON DELETE SET NULL,
    received_at      TIMESTAMPTZ,
    closed_by        BIGINT       REFERENCES app_user(id) ON DELETE SET NULL,         -- declined / cancelled
    closed_at        TIMESTAMPTZ,
    CONSTRAINT inv_transfer_status_chk   CHECK (status IN ('REQUESTED', 'SENT', 'RECEIVED', 'DECLINED', 'CANCELLED')),
    CONSTRAINT inv_transfer_branches_chk CHECK (from_branch_id <> to_branch_id),
    CONSTRAINT inv_transfer_qty_chk      CHECK (qty_requested > 0 AND (qty_sent IS NULL OR qty_sent > 0))
);

CREATE INDEX inv_transfer_org_status_idx ON inv_transfer (org_id, status);
CREATE INDEX inv_transfer_from_idx       ON inv_transfer (from_branch_id);
CREATE INDEX inv_transfer_to_idx         ON inv_transfer (to_branch_id);

-- The ledger. Never updated or deleted (a mistake is fixed by a correcting row), so there is
-- deliberately no updated_at. qty_after is the branch quantity right after this row.
CREATE TABLE inv_movement (
    id            BIGSERIAL    PRIMARY KEY,
    org_id        BIGINT       NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    branch_id     BIGINT       NOT NULL REFERENCES branch(id)       ON DELETE RESTRICT,
    part_id       BIGINT       NOT NULL REFERENCES inv_part(id)     ON DELETE RESTRICT,
    movement_type VARCHAR(20)  NOT NULL,
    qty_change    INTEGER      NOT NULL,
    qty_after     INTEGER      NOT NULL,
    reference     VARCHAR(60),                    -- job card number, or the request number
    vehicle_no    VARCHAR(20),
    note          VARCHAR(500),
    import_id     BIGINT       REFERENCES inv_import(id)   ON DELETE RESTRICT,
    transfer_id   BIGINT       REFERENCES inv_transfer(id) ON DELETE RESTRICT,
    created_by    BIGINT       REFERENCES app_user(id) ON DELETE SET NULL,
    acting_role   VARCHAR(50),                    -- set only while the user acts in a switched role
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT inv_movement_type_chk CHECK (movement_type IN (
        'UPLOAD_ADD', 'UPLOAD_SET', 'MANUAL_ADD', 'CORRECTION', 'BILLED', 'TRANSFER_OUT', 'TRANSFER_IN'
    )),
    CONSTRAINT inv_movement_change_chk CHECK (qty_change <> 0),
    CONSTRAINT inv_movement_after_chk  CHECK (qty_after >= 0)
);

CREATE INDEX inv_movement_org_time_idx    ON inv_movement (org_id, created_at DESC);
CREATE INDEX inv_movement_branch_part_idx ON inv_movement (branch_id, part_id);
CREATE INDEX inv_movement_reference_idx   ON inv_movement (org_id, reference);

CREATE TABLE inv_role_permission (
    id         BIGSERIAL    PRIMARY KEY,
    org_id     BIGINT       NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    role       VARCHAR(30)  NOT NULL,
    permission VARCHAR(30)  NOT NULL,
    allowed    BOOLEAN      NOT NULL,
    updated_by BIGINT       REFERENCES app_user(id) ON DELETE SET NULL,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT inv_role_permission_role_chk CHECK (role IN ('INVENTORY_MANAGER', 'STORE_KEEPER')),
    CONSTRAINT inv_role_permission_uq       UNIQUE (org_id, role, permission)
);

-- V17: the Cash page tables (AGENT.md locked decision #1).
--
--   cash_document      — one internal bank <-> drawer movement (IN from Bank / OUT to Bank).
--                        Single amount, NO sub-lines, NO customer/vehicle/job-card. Its own
--                        {branch}-{MONKEY}-C-{seq} number, same maker-checker workflow as
--                        every other document.
--   branch_cash_opening — the first-ever drawer opening for a branch, set once by the
--                        Accountant. Every later day's opening is the previous day's
--                        cash_day_close.counted_amount.
--   cash_day_close     — end-of-day close for a (branch, date). Its existence is the lock:
--                        once it exists, no cash movement — and no cash-mode settlement or
--                        expense line (enforced in the service layer) — may be dated on or
--                        before close_date for that branch.
--
-- Drawer position for a (branch, date):
--   opening + cash-mode receipts + cash IN - cash-mode expenses - cash OUT
-- counting every non-DRAFT, non-REJECTED document (the money is physically in the drawer
-- regardless of review state; REJECTED means it was returned / never received).

-- ---------------------------------------------------------------------------
-- cash_document
-- ---------------------------------------------------------------------------
CREATE TABLE cash_document (
    id                BIGSERIAL     PRIMARY KEY,
    org_id            BIGINT        NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    branch_id         BIGINT        NOT NULL REFERENCES branch(id)       ON DELETE RESTRICT,

    -- e.g. OOR-AUG26-C-001. NULL until submitted.
    document_no       VARCHAR(40),

    direction         VARCHAR(3)    NOT NULL,          -- IN (from bank) / OUT (to bank)
    transaction_date  DATE          NOT NULL,
    amount            NUMERIC(14,2) NOT NULL,
    bank_id           BIGINT        REFERENCES bank(id) ON DELETE RESTRICT,
    transaction_ref   VARCHAR(80),
    remark            VARCHAR(300),

    workflow_status   VARCHAR(12)   NOT NULL DEFAULT 'DRAFT',

    created_by        BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    last_modified_by  BIGINT        REFERENCES app_user(id)          ON DELETE RESTRICT,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    submitted_at      TIMESTAMPTZ,

    CONSTRAINT cash_document_direction_chk CHECK (direction IN ('IN', 'OUT')),
    CONSTRAINT cash_document_amount_chk    CHECK (amount > 0),
    CONSTRAINT cash_document_workflow_chk
        CHECK (workflow_status IN ('DRAFT','SUBMITTED','VERIFIED','APPROVED','QUERIED','REJECTED')),
    CONSTRAINT cash_document_org_no_uq UNIQUE (org_id, document_no)
);

CREATE INDEX cash_document_org_idx       ON cash_document (org_id);
CREATE INDEX cash_document_day_idx       ON cash_document (org_id, branch_id, transaction_date);
CREATE INDEX cash_document_queue_idx     ON cash_document (org_id, branch_id, workflow_status);
CREATE INDEX cash_document_mine_idx      ON cash_document (org_id, created_by, created_at DESC);
CREATE INDEX cash_document_no_lower_idx  ON cash_document (org_id, lower(document_no));

-- ---------------------------------------------------------------------------
-- branch_cash_opening  (one per branch, ever)
-- ---------------------------------------------------------------------------
CREATE TABLE branch_cash_opening (
    id            BIGSERIAL     PRIMARY KEY,
    org_id        BIGINT        NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    branch_id     BIGINT        NOT NULL REFERENCES branch(id)       ON DELETE RESTRICT,
    opening_date  DATE          NOT NULL,
    amount        NUMERIC(14,2) NOT NULL,
    set_by        BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT branch_cash_opening_org_branch_uq UNIQUE (org_id, branch_id)
);

-- ---------------------------------------------------------------------------
-- cash_day_close  (one per branch per date; its existence locks the date)
-- ---------------------------------------------------------------------------
CREATE TABLE cash_day_close (
    id                BIGSERIAL     PRIMARY KEY,
    org_id            BIGINT        NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    branch_id         BIGINT        NOT NULL REFERENCES branch(id)       ON DELETE RESTRICT,
    close_date        DATE          NOT NULL,
    opening_amount    NUMERIC(14,2) NOT NULL,
    computed_closing  NUMERIC(14,2) NOT NULL,   -- snapshot of the drawer formula at close time
    counted_amount    NUMERIC(14,2) NOT NULL,   -- physically counted cash
    variance          NUMERIC(14,2) NOT NULL,   -- counted_amount - computed_closing
    variance_remark   VARCHAR(300),             -- required (service layer) when variance <> 0
    closed_by         BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    closed_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT cash_day_close_org_branch_date_uq UNIQUE (org_id, branch_id, close_date)
);

CREATE INDEX cash_day_close_branch_idx ON cash_day_close (org_id, branch_id);

-- V14: the expense money-flow tables.
--
--   expense_document — the review wrapper around one expense cause: money paid OUT to a
--                      receiver (vendor / payee), optionally tagged to a job card. Mirrors
--                      receive_document, with three real differences:
--                        * billed to a receiver, not a customer;
--                        * job_card_id is optional (overhead expenses have none);
--                        * no pending-amount / auto-settle math — the Accountant closes it
--                          explicitly (Stage 7), so lines stay addable until then. The extra
--                          workflow_status value CLOSED carries that.
--   expense_line     — one payment made under that document. Same column set as
--                      settlement_line (amount, mode, bank, ref, override trail).
--
-- There is deliberately NO "one open document per ..." partial unique index here. That
-- invariant exists on the receive side only to keep a job card's Pending Amount
-- unambiguous; expenses aggregate toward no such figure, so New Expense always creates a
-- fresh document.
--
-- over_limit is a stored flag on the document, recomputed on every line change: true when
-- any line's amount exceeds its sub-category's limit_amount. It drives the FM-approval
-- requirement downstream; the per-line warning shown on screen is derived at read time.
--
-- See AGENT.md "Core data model" and plan.md "Transactional documents".

-- ---------------------------------------------------------------------------
-- expense_document
-- ---------------------------------------------------------------------------
CREATE TABLE expense_document (
    id                  BIGSERIAL     PRIMARY KEY,
    org_id              BIGINT        NOT NULL REFERENCES organization(id)           ON DELETE RESTRICT,
    branch_id           BIGINT        NOT NULL REFERENCES branch(id)                 ON DELETE RESTRICT,
    -- Optional: an expense may sit on a job card (taxi / fuel / local purchase for a repair)
    -- or be pure branch overhead (stationery, daily wages) with no job card.
    job_card_id         BIGINT        REFERENCES job_card(id)                        ON DELETE RESTRICT,
    receiver_id         BIGINT        NOT NULL REFERENCES receiver(id)               ON DELETE RESTRICT,
    expense_category_id BIGINT        NOT NULL REFERENCES expense_category(id)       ON DELETE RESTRICT,
    business_status_id  BIGINT        NOT NULL REFERENCES expense_business_status(id) ON DELETE RESTRICT,

    -- Human-readable number, e.g. OOR-AUG26-E-001. NULL until submitted — a draft that may
    -- be abandoned never burns a number.
    document_no         VARCHAR(40),

    workflow_status     VARCHAR(12)   NOT NULL DEFAULT 'DRAFT',

    -- true when any line's amount exceeds its sub-category limit_amount. Recomputed by the
    -- service on every line change; never trusted from the request.
    over_limit          BOOLEAN       NOT NULL DEFAULT FALSE,

    created_by          BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    last_modified_by    BIGINT        REFERENCES app_user(id)          ON DELETE RESTRICT,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    submitted_at        TIMESTAMPTZ,

    CONSTRAINT expense_document_workflow_chk
        CHECK (workflow_status IN ('DRAFT','SUBMITTED','VERIFIED','APPROVED','QUERIED','REJECTED','CLOSED')),
    CONSTRAINT expense_document_org_no_uq UNIQUE (org_id, document_no)
);

CREATE INDEX expense_document_org_idx      ON expense_document (org_id);
CREATE INDEX expense_document_job_card_idx ON expense_document (org_id, job_card_id);
CREATE INDEX expense_document_receiver_idx ON expense_document (org_id, receiver_id);
-- review queue (Stage 7) filters by branch + workflow_status
CREATE INDEX expense_document_queue_idx    ON expense_document (org_id, branch_id, workflow_status);
-- My Entries lists a user's own documents newest-first
CREATE INDEX expense_document_mine_idx     ON expense_document (org_id, created_by, created_at DESC);
-- universal search matches document_no (case-insensitive)
CREATE INDEX expense_document_org_no_lower_idx ON expense_document (org_id, lower(document_no));

-- The receive side never added this: universal search now also matches receive_document.document_no.
CREATE INDEX receive_document_org_no_lower_idx ON receive_document (org_id, lower(document_no));

-- ---------------------------------------------------------------------------
-- expense_line
-- ---------------------------------------------------------------------------
CREATE TABLE expense_line (
    id                   BIGSERIAL     PRIMARY KEY,
    org_id               BIGINT        NOT NULL REFERENCES organization(id)         ON DELETE RESTRICT,
    expense_document_id  BIGINT        NOT NULL REFERENCES expense_document(id)     ON DELETE RESTRICT,

    line_no              INTEGER       NOT NULL,
    -- Stored, e.g. OOR-AUG26-E-001-L1. NULL until the parent document is submitted; never
    -- reused even if a line is later voided.
    line_id              VARCHAR(48),

    transaction_date     DATE          NOT NULL,
    sub_category_id      BIGINT        NOT NULL REFERENCES expense_sub_category(id) ON DELETE RESTRICT,
    expense_mode_id      BIGINT        NOT NULL REFERENCES expense_mode(id)         ON DELETE RESTRICT,
    amount               NUMERIC(14,2) NOT NULL,
    original_amount      NUMERIC(14,2),                 -- set when an amount is overridden
    bank_id              BIGINT        REFERENCES bank(id) ON DELETE RESTRICT,
    transaction_ref      VARCHAR(80),
    remark               VARCHAR(300),

    -- Accountant override trail (Stage 7) — kept here so the column set is stable
    overridden_by        BIGINT        REFERENCES app_user(id) ON DELETE RESTRICT,
    override_reason      VARCHAR(300),
    overridden_at        TIMESTAMPTZ,

    created_by           BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT expense_line_amount_chk CHECK (amount > 0),
    CONSTRAINT expense_line_doc_no_uq  UNIQUE (expense_document_id, line_no)
);

CREATE INDEX expense_line_doc_idx ON expense_line (org_id, expense_document_id);

-- ---------------------------------------------------------------------------
-- audit_event: moving an expense onto a claim is a new kind of recorded change
-- ---------------------------------------------------------------------------
ALTER TABLE audit_event DROP CONSTRAINT audit_event_event_type_chk;
ALTER TABLE audit_event ADD CONSTRAINT audit_event_event_type_chk CHECK (event_type IN (
    'CREATED', 'SUBMITTED', 'VERIFIED', 'APPROVED', 'QUERIED', 'REJECTED',
    'CLOSED', 'OVERRIDE', 'LINE_ADDED', 'SETTLED', 'CATEGORY_CHANGED', 'TRANSFERRED_TO_CLAIM'
));

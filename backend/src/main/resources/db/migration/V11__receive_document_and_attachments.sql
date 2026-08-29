-- V11: the receive money-flow tables.
--
--   receive_document  — the review / settlement wrapper around one job card. NOT the case.
--   settlement_line   — one payment received against that document.
--   claim_close       — the FM's final, immutable close of a claim (one per job card).
--                       The write path (POST /job-cards/{id}/close-claim) is Stage 8; this
--                       stage creates the table and reads it (Pending Amount = 0 once a row
--                       exists; PATCH /job-cards guards on it).
--   attachment        — polymorphic PDF/image receipts, 0..n per document or line.
--
-- See AGENT.md "Core data model" and plan.md "Transactional documents".

-- ---------------------------------------------------------------------------
-- receive_document
-- ---------------------------------------------------------------------------
CREATE TABLE receive_document (
    id                BIGSERIAL     PRIMARY KEY,
    org_id            BIGINT        NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    branch_id         BIGINT        NOT NULL REFERENCES branch(id)       ON DELETE RESTRICT,
    job_card_id       BIGINT        NOT NULL REFERENCES job_card(id)     ON DELETE RESTRICT,

    -- Human-readable number, e.g. OOR-AUG26-R-001. NULL until the document is submitted —
    -- gap-free numbering never burns a number on a draft that may be abandoned.
    document_no       VARCHAR(40),

    workflow_status   VARCHAR(12)   NOT NULL DEFAULT 'DRAFT',

    -- Computed flag: flips true when the job card's Pending Amount reaches 0, or (Stage 8)
    -- when a ClaimClose is written. Independent of workflow_status. Named "settled", not
    -- "closed", to avoid clashing with the "Close" business-status value.
    settled           BOOLEAN       NOT NULL DEFAULT FALSE,

    created_by        BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    last_modified_by  BIGINT        REFERENCES app_user(id)          ON DELETE RESTRICT,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    submitted_at      TIMESTAMPTZ,

    CONSTRAINT receive_document_workflow_chk
        CHECK (workflow_status IN ('DRAFT','SUBMITTED','VERIFIED','APPROVED','QUERIED','REJECTED')),
    -- document_no is unique per org once assigned; multiple NULLs (drafts) are allowed.
    CONSTRAINT receive_document_org_no_uq UNIQUE (org_id, document_no)
);

CREATE INDEX receive_document_org_idx        ON receive_document (org_id);
CREATE INDEX receive_document_job_card_idx   ON receive_document (org_id, job_card_id);
-- review queue (Stage 7) filters by branch + workflow_status
CREATE INDEX receive_document_queue_idx      ON receive_document (org_id, branch_id, workflow_status);
-- My Entries lists a user's own documents newest-first
CREATE INDEX receive_document_mine_idx       ON receive_document (org_id, created_by, created_at DESC);

-- At most ONE open (settled = false) receive document per job card. "Add Payment" appends a
-- line to that one document; it can never create a second. This exact bug was found and
-- fixed in the HTML prototype — see AGENT.md "Add Payment behavior".
CREATE UNIQUE INDEX receive_document_one_open_per_job_card
    ON receive_document (org_id, job_card_id)
    WHERE settled = false;

-- ---------------------------------------------------------------------------
-- settlement_line
-- ---------------------------------------------------------------------------
CREATE TABLE settlement_line (
    id                   BIGSERIAL     PRIMARY KEY,
    org_id               BIGINT        NOT NULL REFERENCES organization(id)     ON DELETE RESTRICT,
    receive_document_id  BIGINT        NOT NULL REFERENCES receive_document(id) ON DELETE RESTRICT,

    line_no              INTEGER       NOT NULL,
    -- Stored, e.g. OOR-AUG26-R-001-L1. NULL until the parent document is submitted and gets
    -- its number; never reused even if a line is later voided.
    line_id              VARCHAR(48),

    transaction_date     DATE          NOT NULL,
    settlement_mode_id   BIGINT        NOT NULL REFERENCES settlement_mode(id)  ON DELETE RESTRICT,
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

    CONSTRAINT settlement_line_amount_chk   CHECK (amount > 0),
    CONSTRAINT settlement_line_doc_no_uq    UNIQUE (receive_document_id, line_no)
);

CREATE INDEX settlement_line_doc_idx ON settlement_line (org_id, receive_document_id);

-- ---------------------------------------------------------------------------
-- claim_close  (table now; write path in Stage 8)
-- ---------------------------------------------------------------------------
CREATE TABLE claim_close (
    id              BIGSERIAL     PRIMARY KEY,
    org_id          BIGINT        NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    job_card_id     BIGINT        NOT NULL REFERENCES job_card(id)     ON DELETE RESTRICT,
    final_amount    NUMERIC(14,2) NOT NULL,
    overridden      BOOLEAN       NOT NULL DEFAULT FALSE,
    override_reason VARCHAR(300),
    closed_by       BIGINT        NOT NULL REFERENCES app_user(id)     ON DELETE RESTRICT,
    closed_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT claim_close_org_job_card_uq UNIQUE (org_id, job_card_id)
);

CREATE INDEX claim_close_org_idx ON claim_close (org_id);

-- ---------------------------------------------------------------------------
-- attachment  (polymorphic, 0..n per parent)
-- ---------------------------------------------------------------------------
CREATE TABLE attachment (
    id            BIGSERIAL     PRIMARY KEY,
    org_id        BIGINT        NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    parent_type   VARCHAR(20)   NOT NULL,
    parent_id     BIGINT        NOT NULL,
    r2_object_key VARCHAR(255)  NOT NULL,
    filename      VARCHAR(255)  NOT NULL,
    content_type  VARCHAR(100)  NOT NULL,
    size_bytes    BIGINT        NOT NULL,
    -- true once the parent document is Approved or Closed — no replace, no delete.
    frozen        BOOLEAN       NOT NULL DEFAULT FALSE,
    uploaded_by   BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    uploaded_at   TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT attachment_parent_type_chk
        CHECK (parent_type IN ('RECEIVE_DOCUMENT','SETTLEMENT_LINE','EXPENSE_DOCUMENT','EXPENSE_LINE'))
);

CREATE INDEX attachment_parent_idx ON attachment (org_id, parent_type, parent_id);

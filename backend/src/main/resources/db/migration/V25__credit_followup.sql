-- V25: credit follow-up (FEAT-35) — who owes, by when, and what was promised.
--
-- Receive documents posted on credit (mode 'Credit (Due)', category 'B2B Credit',
-- or any unpaid balance) get an explicit follow-up row: a due date plus the
-- customer's promise, owned by someone. The dashboard outstanding list is
-- display-only; THIS table is the collection workflow. Overdue is derived at
-- read time (OPEN + due_date < today), never stored, so it can never go stale.

CREATE TABLE credit_followup (
    id                  BIGSERIAL     PRIMARY KEY,
    org_id              BIGINT        NOT NULL REFERENCES organization(id)      ON DELETE RESTRICT,
    receive_document_id BIGINT        NOT NULL REFERENCES receive_document(id) ON DELETE RESTRICT,
    due_date            DATE          NOT NULL,
    promise_note        VARCHAR(500),
    status              VARCHAR(10)   NOT NULL DEFAULT 'OPEN',
    reminded_count      INTEGER       NOT NULL DEFAULT 0,
    last_reminded_at    TIMESTAMPTZ,
    closed_at           TIMESTAMPTZ,
    created_by          BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT credit_followup_status_chk CHECK (status IN ('OPEN','PROMISED','CLOSED'))
);

-- One live follow-up per document — closing then re-promising reuses the row.
CREATE UNIQUE INDEX credit_followup_one_open_per_doc
    ON credit_followup (org_id, receive_document_id)
    WHERE status IN ('OPEN','PROMISED');

CREATE INDEX credit_followup_due_idx ON credit_followup (org_id, due_date)
    WHERE status IN ('OPEN','PROMISED');

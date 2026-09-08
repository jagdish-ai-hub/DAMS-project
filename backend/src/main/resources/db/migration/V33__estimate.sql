-- V33: estimates (FEAT-48) — the number the customer agreed to, before the work.
--
-- Job cards carry only the final invoice today; the agreed figure lives in
-- conversation, and disputes erupt at payment time. An estimate is APPROVED
-- (by customer, recorded by cashier; large ones FM-approved) before work
-- starts; billing then shows line variance vs the approved estimate.
-- Estimates never touch settlement math — they inform the bill, like budgets
-- inform spend. SUPERSEDED keeps history when re-quoted (never deleted).

CREATE TABLE estimate (
    id            BIGSERIAL     PRIMARY KEY,
    org_id        BIGINT        NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    job_card_id   BIGINT        NOT NULL REFERENCES job_card(id)     ON DELETE RESTRICT,
    status        VARCHAR(12)   NOT NULL DEFAULT 'DRAFT',
    total         NUMERIC(14,2) NOT NULL DEFAULT 0,
    approved_by   BIGINT        REFERENCES app_user(id) ON DELETE RESTRICT,
    decided_at    TIMESTAMPTZ,
    decision_note VARCHAR(500),
    created_by    BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT estimate_status_chk CHECK (status IN ('DRAFT','APPROVED','REJECTED','SUPERSEDED')),
    CONSTRAINT estimate_total_chk CHECK (total >= 0)
);

CREATE TABLE estimate_line (
    id            BIGSERIAL     PRIMARY KEY,
    org_id        BIGINT        NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    estimate_id   BIGINT        NOT NULL REFERENCES estimate(id)     ON DELETE CASCADE,
    line_no       INTEGER       NOT NULL,
    description   VARCHAR(300)  NOT NULL,
    amount        NUMERIC(14,2) NOT NULL,

    CONSTRAINT estimate_line_amount_chk CHECK (amount >= 0),
    CONSTRAINT estimate_line_no_uq UNIQUE (estimate_id, line_no)
);

CREATE INDEX estimate_job_idx ON estimate (org_id, job_card_id);

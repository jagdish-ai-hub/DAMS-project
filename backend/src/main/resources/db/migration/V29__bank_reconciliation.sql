-- V29: bank statement reconciliation (FEAT-40) — match UTRs, don't eyeball them.
--
-- The accountant uploads the bank statement CSV; each line is matched against
-- settlement lines on (UTR, amount, date±2 days). Matches are suggestions the
-- accountant confirms — matching never moves money or changes a document, it
-- only explains it. Unmatched both-sides rows are the actual work product:
-- money in bank with no receipt, receipts with no money.

CREATE TABLE recon_batch (
    id          BIGSERIAL     PRIMARY KEY,
    org_id      BIGINT        NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    filename    VARCHAR(255)  NOT NULL,
    line_count  INTEGER       NOT NULL DEFAULT 0,
    uploaded_by BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    uploaded_at TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE recon_line (
    id                        BIGSERIAL     PRIMARY KEY,
    org_id                    BIGINT        NOT NULL REFERENCES organization(id)      ON DELETE RESTRICT,
    batch_id                  BIGINT        NOT NULL REFERENCES recon_batch(id)      ON DELETE CASCADE,
    txn_date                  DATE          NOT NULL,
    utr                       VARCHAR(60),
    amount                    NUMERIC(14,2) NOT NULL,
    narration                 VARCHAR(500),
    matched_settlement_line_id BIGINT       REFERENCES settlement_line(id) ON DELETE RESTRICT,
    match_kind                VARCHAR(12),
    ignored                   BOOLEAN       NOT NULL DEFAULT FALSE,

    CONSTRAINT recon_line_match_chk
        CHECK (match_kind IS NULL OR match_kind IN ('EXACT','AMOUNT_DATE','MANUAL'))
);

CREATE INDEX recon_line_batch_idx ON recon_line (org_id, batch_id);
CREATE INDEX recon_line_utr_idx ON recon_line (org_id, utr) WHERE utr IS NOT NULL;

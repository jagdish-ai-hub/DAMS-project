-- V24: cash-close reopen requests (AGENT.md conflict resolved as a request flow).
--
-- A closed cash day is a lock — the cashier cannot silently reopen it. Instead the
-- cashier files a row here (reason required); a Finance Manager approves (which deletes
-- the cash_day_close row so the day can be re-closed) or rejects with a reason.
-- At most one PENDING request per (branch, date) — a decided request does not block a
-- fresh one (the cashier may re-apply with a better reason).

CREATE TABLE cash_close_reopen_request (
    id            BIGSERIAL     PRIMARY KEY,
    org_id        BIGINT        NOT NULL REFERENCES organization(id) ON DELETE RESTRICT,
    branch_id     BIGINT        NOT NULL REFERENCES branch(id)       ON DELETE RESTRICT,
    close_date    DATE          NOT NULL,
    reason        VARCHAR(500)  NOT NULL,
    status        VARCHAR(10)   NOT NULL DEFAULT 'PENDING',
    requested_by  BIGINT        NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    decided_by    BIGINT        REFERENCES app_user(id)          ON DELETE RESTRICT,
    decided_at    TIMESTAMPTZ,
    decision_note VARCHAR(500),
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),

    CONSTRAINT cash_reopen_status_chk CHECK (status IN ('PENDING','APPROVED','REJECTED'))
);

-- One pending request per (branch, date) — decided rows stay as history without blocking.
CREATE UNIQUE INDEX cash_reopen_one_pending_per_day
    ON cash_close_reopen_request (org_id, branch_id, close_date)
    WHERE status = 'PENDING';

CREATE INDEX cash_reopen_branch_idx ON cash_close_reopen_request (org_id, branch_id);

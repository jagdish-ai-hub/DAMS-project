-- rev 61: an expense marked "Transfer to Claim" is closed by the Finance Manager (not the
-- Accountant), who records the final amount actually recovered. Same idea as claim_close on
-- the receipt side: the final amount, whether it differs from the expense total, the reason,
-- and who closed it. All null until a claim expense is closed.

ALTER TABLE expense_document
    ADD COLUMN claim_final_amount    NUMERIC(14, 2),
    ADD COLUMN claim_computed_total  NUMERIC(14, 2),
    ADD COLUMN claim_overridden      BOOLEAN       NOT NULL DEFAULT FALSE,
    ADD COLUMN claim_override_reason VARCHAR(300),
    ADD COLUMN claim_closed_by       BIGINT REFERENCES app_user (id) ON DELETE RESTRICT,
    ADD COLUMN claim_closed_at       TIMESTAMPTZ;

ALTER TABLE expense_document
    ADD CONSTRAINT expense_document_claim_final_chk CHECK (claim_final_amount IS NULL OR claim_final_amount >= 0),
    ADD CONSTRAINT expense_document_claim_reason_chk CHECK (NOT claim_overridden OR claim_override_reason IS NOT NULL);

-- The Finance Manager's "recently closed claims" list reads newest first.
CREATE INDEX expense_document_claim_closed_idx ON expense_document (org_id, claim_closed_at DESC)
    WHERE claim_closed_at IS NOT NULL;

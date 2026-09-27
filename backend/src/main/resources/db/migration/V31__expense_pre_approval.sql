-- rev 53: Finance Manager pre-approval for over-limit expenses. When any line is above its
-- sub-category limit the Cashier can't Submit; they "Send for Review" instead. The document
-- stays DRAFT (no number yet) while the FM approves or queries the request, so the review
-- workflow (workflow_status) is untouched — pre-approval is its own small state machine.
--
--   pre_approval_status  NULL      = never requested
--                        PENDING   = waiting for the FM (draft locked for the Cashier)
--                        APPROVED  = FM said yes; Cashier may Submit up to pre_approved_amount
--                        QUERIED   = FM sent it back with a note; Cashier edits and resends
--   pre_approved_amount  the document total the FM approved — a higher total needs approval again

ALTER TABLE expense_document
    ADD COLUMN pre_approval_status  VARCHAR(12),
    ADD COLUMN pre_approved_amount  NUMERIC(14,2),
    ADD COLUMN pre_approved_by      BIGINT REFERENCES app_user(id),
    ADD COLUMN pre_approved_at      TIMESTAMPTZ,
    ADD COLUMN approval_requested_at TIMESTAMPTZ;

ALTER TABLE expense_document
    ADD CONSTRAINT expense_document_pre_approval_chk
    CHECK (pre_approval_status IS NULL OR pre_approval_status IN ('PENDING','APPROVED','QUERIED'));

-- The FM's "approval requests" list: pending requests, oldest first, org-wide.
CREATE INDEX idx_expense_document_pre_approval_pending
    ON expense_document (org_id, approval_requested_at)
    WHERE pre_approval_status = 'PENDING';

ALTER TABLE audit_event DROP CONSTRAINT audit_event_event_type_chk;
ALTER TABLE audit_event ADD CONSTRAINT audit_event_event_type_chk CHECK (event_type IN (
    'CREATED', 'SUBMITTED', 'VERIFIED', 'APPROVED', 'QUERIED', 'REJECTED',
    'CLOSED', 'OVERRIDE', 'LINE_ADDED', 'SETTLED', 'CATEGORY_CHANGED', 'CLAIM_TYPE_CHANGED',
    'TRANSFERRED_TO_CLAIM', 'APPROVAL_REQUESTED', 'PRE_APPROVED'
));

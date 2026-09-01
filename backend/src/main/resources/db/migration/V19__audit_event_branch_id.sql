-- V19: give audit_event a branch_id so the Override Audit screen (Stage 8) and the Owner
-- dashboard (Stage 9) can filter by branch on an index instead of resolving every row's
-- document. Nullable — a few event kinds (org-level, user-level) have no branch, and older
-- rows written before the AuditService overload existed carry NULL until backfilled below.

ALTER TABLE audit_event ADD COLUMN branch_id BIGINT REFERENCES branch(id) ON DELETE SET NULL;

-- Backfill from the document each event is about. entity_type is the discriminator.
UPDATE audit_event ae SET branch_id = d.branch_id
  FROM receive_document d WHERE ae.entity_type = 'ReceiveDocument' AND ae.entity_id = d.id;

UPDATE audit_event ae SET branch_id = d.branch_id
  FROM expense_document d WHERE ae.entity_type = 'ExpenseDocument' AND ae.entity_id = d.id;

UPDATE audit_event ae SET branch_id = d.branch_id
  FROM cash_document d WHERE ae.entity_type = 'CashDocument' AND ae.entity_id = d.id;

UPDATE audit_event ae SET branch_id = jc.branch_id
  FROM job_card jc WHERE ae.entity_type = 'JobCard' AND ae.entity_id = jc.id;

-- Override Audit reads OVERRIDE rows across a date range, optionally by branch / actor.
CREATE INDEX audit_event_type_time_idx ON audit_event (org_id, event_type, created_at DESC);

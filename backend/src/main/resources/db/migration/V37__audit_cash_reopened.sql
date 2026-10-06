-- rev 60: the Owner can reopen a branch's latest closed cash day (a close made by mistake).
-- Reopening deletes the cash_day_close row; the audit trail keeps the original close
-- (counted amount, variance, who closed it) and the Owner's reason as a CASH_REOPENED event.

ALTER TABLE audit_event DROP CONSTRAINT audit_event_event_type_chk;
ALTER TABLE audit_event ADD CONSTRAINT audit_event_event_type_chk CHECK (event_type IN (
    'CREATED', 'SUBMITTED', 'VERIFIED', 'APPROVED', 'QUERIED', 'REJECTED',
    'CLOSED', 'OVERRIDE', 'LINE_ADDED', 'SETTLED', 'CATEGORY_CHANGED', 'CLAIM_TYPE_CHANGED',
    'TRANSFERRED_TO_CLAIM', 'APPROVAL_REQUESTED', 'PRE_APPROVED', 'ROLE_SWITCHED',
    'JOB_CARD_CUSTOMER_ATTACHED', 'STATUS_CHANGED', 'CASH_REOPENED'
));

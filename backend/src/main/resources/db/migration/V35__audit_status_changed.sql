-- rev 58: a reviewer (Accountant / Finance Manager / Owner) can change an expense's business status
-- from the review screen; each change is audited as STATUS_CHANGED.

ALTER TABLE audit_event DROP CONSTRAINT audit_event_event_type_chk;
ALTER TABLE audit_event ADD CONSTRAINT audit_event_event_type_chk CHECK (event_type IN (
    'CREATED', 'SUBMITTED', 'VERIFIED', 'APPROVED', 'QUERIED', 'REJECTED',
    'CLOSED', 'OVERRIDE', 'LINE_ADDED', 'SETTLED', 'CATEGORY_CHANGED', 'CLAIM_TYPE_CHANGED',
    'TRANSFERRED_TO_CLAIM', 'APPROVAL_REQUESTED', 'PRE_APPROVED', 'ROLE_SWITCHED',
    'JOB_CARD_CUSTOMER_ATTACHED', 'STATUS_CHANGED'
));

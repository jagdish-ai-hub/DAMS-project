-- rev 77: the Owner can move a vehicle to another customer; each move is audited as VEHICLE_MOVED
-- (who, from, to). The audit table restricts event types, so the new one must be allowed here.
ALTER TABLE audit_event DROP CONSTRAINT audit_event_event_type_chk;
ALTER TABLE audit_event ADD CONSTRAINT audit_event_event_type_chk CHECK (event_type IN (
    'CREATED', 'SUBMITTED', 'VERIFIED', 'APPROVED', 'QUERIED', 'REJECTED',
    'CLOSED', 'OVERRIDE', 'LINE_ADDED', 'SETTLED', 'CATEGORY_CHANGED', 'CLAIM_TYPE_CHANGED',
    'TRANSFERRED_TO_CLAIM', 'APPROVAL_REQUESTED', 'PRE_APPROVED', 'ROLE_SWITCHED',
    'JOB_CARD_CUSTOMER_ATTACHED', 'STATUS_CHANGED', 'CASH_REOPENED', 'VEHICLE_MOVED'
));

-- rev 56: link Expense <-> Job Card <-> Customer <-> Vehicle.
--
--  * job_card.customer_id becomes nullable: a Cashier may start a job card from an Expense
--    before any customer is known; a later Receipt attaches the customer once (set-once).
--    The typed vehicle number is kept as text until then (vehicle_no_text) because every
--    Vehicle row must belong to exactly one customer.
--  * expense_document gets real customer_id / vehicle_id links (the V30 text columns stay as a
--    display snapshot).
--  * customer.created_branch_id lets a branch-scoped search find a customer that has no job
--    card yet.

ALTER TABLE job_card ALTER COLUMN customer_id DROP NOT NULL;
ALTER TABLE job_card ADD COLUMN vehicle_no_text VARCHAR(20);
CREATE INDEX job_card_vehicle_text_idx ON job_card (org_id, vehicle_no_text);

ALTER TABLE expense_document
    ADD COLUMN customer_id BIGINT REFERENCES customer(id) ON DELETE RESTRICT,
    ADD COLUMN vehicle_id  BIGINT REFERENCES vehicle(id)  ON DELETE RESTRICT;

UPDATE expense_document e
   SET customer_id = j.customer_id,
       vehicle_id  = j.vehicle_id
  FROM job_card j
 WHERE e.job_card_id = j.id;

CREATE INDEX expense_document_customer_idx ON expense_document (org_id, customer_id);

ALTER TABLE customer ADD COLUMN created_branch_id BIGINT REFERENCES branch(id) ON DELETE RESTRICT;

UPDATE customer c
   SET created_branch_id = (SELECT j.branch_id
                              FROM job_card j
                             WHERE j.customer_id = c.id
                             ORDER BY j.created_at ASC, j.id ASC
                             LIMIT 1);

CREATE INDEX customer_org_lower_name_idx ON customer (org_id, lower(name));

ALTER TABLE audit_event DROP CONSTRAINT audit_event_event_type_chk;
ALTER TABLE audit_event ADD CONSTRAINT audit_event_event_type_chk CHECK (event_type IN (
    'CREATED', 'SUBMITTED', 'VERIFIED', 'APPROVED', 'QUERIED', 'REJECTED',
    'CLOSED', 'OVERRIDE', 'LINE_ADDED', 'SETTLED', 'CATEGORY_CHANGED', 'CLAIM_TYPE_CHANGED',
    'TRANSFERRED_TO_CLAIM', 'APPROVAL_REQUESTED', 'PRE_APPROVED', 'ROLE_SWITCHED',
    'JOB_CARD_CUSTOMER_ATTACHED'
));

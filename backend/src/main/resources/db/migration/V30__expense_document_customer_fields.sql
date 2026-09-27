-- Cashier feedback: an expense may need to reference the customer/vehicle/invoice/job-card
-- it relates to, even when it has no linked job_card (branch-overhead expenses). These are
-- plain manual reference fields on the document itself, never used as keys -- mirrors
-- job_card.dbm_id / job_card.invoice_no (see V8).

ALTER TABLE expense_document
    ADD COLUMN customer_name VARCHAR(160),
    ADD COLUMN vehicle_no    VARCHAR(20),
    ADD COLUMN invoice_no    VARCHAR(60),
    ADD COLUMN dbm_id        VARCHAR(40);

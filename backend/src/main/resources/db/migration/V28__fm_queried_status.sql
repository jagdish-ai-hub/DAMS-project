-- rev 49: a second, distinct query state. The Accountant's query on a SUBMITTED document
-- still yields QUERIED (fix-and-resubmit goes to the Cashier, unchanged). The Finance
-- Manager's query on a VERIFIED document now yields FM_QUERIED instead — routed back to the
-- Accountant's own queue rather than skipping past them to the Cashier.

ALTER TABLE receive_document DROP CONSTRAINT receive_document_workflow_chk;
ALTER TABLE receive_document
    ADD CONSTRAINT receive_document_workflow_chk
    CHECK (workflow_status IN ('DRAFT','SUBMITTED','VERIFIED','APPROVED','QUERIED','FM_QUERIED','REJECTED'));

ALTER TABLE expense_document DROP CONSTRAINT expense_document_workflow_chk;
ALTER TABLE expense_document
    ADD CONSTRAINT expense_document_workflow_chk
    CHECK (workflow_status IN ('DRAFT','SUBMITTED','VERIFIED','APPROVED','QUERIED','FM_QUERIED','REJECTED','CLOSED'));

ALTER TABLE cash_document DROP CONSTRAINT cash_document_workflow_chk;
ALTER TABLE cash_document
    ADD CONSTRAINT cash_document_workflow_chk
    CHECK (workflow_status IN ('DRAFT','SUBMITTED','VERIFIED','APPROVED','QUERIED','FM_QUERIED','REJECTED'));

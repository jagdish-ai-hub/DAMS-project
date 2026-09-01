-- V18: test-phase seed — the Rayagada (OOR) branch's cash: a first opening, four cash
-- movements, and one historical day-close so the "opening = previous day's counted amount"
-- chain runs on seed data. Attached to the demo dealership (V5). Skipped by the prod path.
--
-- Notable rows:
--   OOR-JUL26-C-003  SUBMITTED  — sits in the Stage 7 review queue
--   OOR-JUL26-C-004  QUERIED    — the cash-side fix-and-resubmit demo (mirrors R-013)
--   cash_day_close 2026-07-14   — locks that date; opening 10,000 + C-001 IN 20,000 = 30,000

DO $$
DECLARE
    v_org        BIGINT;
    v_oor        BIGINT;
    v_cashier    BIGINT;
    v_accountant BIGINT;
    v_sbi        BIGINT;
BEGIN
    SELECT id INTO v_org FROM organization WHERE name = 'JJ Motors (Demo)';
    IF v_org IS NULL THEN
        RAISE EXCEPTION 'V18 seed: demo org "JJ Motors (Demo)" not found (V5 must run first)';
    END IF;

    SELECT id INTO v_oor        FROM branch   WHERE org_id = v_org AND code = 'OOR';
    SELECT id INTO v_cashier    FROM app_user WHERE email = 'cashier@jjmotors.demo';
    SELECT id INTO v_accountant FROM app_user WHERE email = 'accountant@jjmotors.demo';
    SELECT id INTO v_sbi        FROM bank     WHERE org_id = v_org AND name = 'State Bank of India';

    -- First-ever drawer opening for Rayagada — set by the Accountant.
    INSERT INTO branch_cash_opening (org_id, branch_id, opening_date, amount, set_by, created_at)
    VALUES (v_org, v_oor, DATE '2026-07-01', 10000, v_accountant, TIMESTAMPTZ '2026-07-01 09:00:00+05:30');

    -- ---- cash movements ----
    INSERT INTO cash_document (org_id, branch_id, document_no, direction, transaction_date, amount,
                              bank_id, transaction_ref, remark, workflow_status,
                              created_by, last_modified_by, created_at, submitted_at) VALUES
        (v_org, v_oor, 'OOR-JUL26-C-001', 'IN',  DATE '2026-07-14', 20000, v_sbi, 'NEFT/IN/71401',
         'Float drawn from bank for the week', 'APPROVED',
         v_cashier, v_cashier, TIMESTAMPTZ '2026-07-14 09:30:00+05:30', TIMESTAMPTZ '2026-07-14 09:30:00+05:30'),
        (v_org, v_oor, 'OOR-JUL26-C-002', 'OUT', DATE '2026-07-20', 15000, v_sbi, 'DEP/72001',
         'Surplus cash deposited to bank', 'APPROVED',
         v_cashier, v_cashier, TIMESTAMPTZ '2026-07-20 17:45:00+05:30', TIMESTAMPTZ '2026-07-20 17:45:00+05:30'),
        (v_org, v_oor, 'OOR-JUL26-C-003', 'IN',  DATE '2026-07-24', 5000, v_sbi, 'NEFT/IN/72401',
         NULL, 'SUBMITTED',
         v_cashier, v_cashier, TIMESTAMPTZ '2026-07-24 10:15:00+05:30', TIMESTAMPTZ '2026-07-24 10:15:00+05:30'),
        (v_org, v_oor, 'OOR-JUL26-C-004', 'OUT', DATE '2026-07-28', 8000, NULL, NULL,
         'Cash to bank', 'QUERIED',
         v_cashier, v_cashier, TIMESTAMPTZ '2026-07-28 18:00:00+05:30', TIMESTAMPTZ '2026-07-28 18:00:00+05:30');

    -- ---- historical day-close for 2026-07-14 ----
    -- opening 10,000 + cash IN (C-001) 20,000; no cash-mode receipts / expenses / OUT that day.
    INSERT INTO cash_day_close (org_id, branch_id, close_date, opening_amount, computed_closing,
                               counted_amount, variance, variance_remark, closed_by, closed_at)
    VALUES (v_org, v_oor, DATE '2026-07-14', 10000, 30000, 30000, 0, NULL,
            v_cashier, TIMESTAMPTZ '2026-07-14 19:00:00+05:30');

    -- ---- document_sequence: July 2026 C counter ----
    INSERT INTO document_sequence (org_id, branch_id, month_key, doc_type, last_seq)
    VALUES (v_org, v_oor, 'JUL26', 'C', 4);
END $$;

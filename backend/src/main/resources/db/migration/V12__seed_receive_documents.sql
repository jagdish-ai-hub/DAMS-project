-- V12: test-phase seed — one receive document (plus its settlement lines) per job card from
-- the cashier-home.html mockup, with the same document numbers, amounts, modes and dates.
-- Attached to the demo dealership (V5) / job cards (V9). Skipped by the future prod path.
--
-- Notable rows, chosen so Stage 4 flows are demoable on seed data alone:
--   OOR-JUL26-R-013  Suresh Mohapatra  — QUERIED  → the fix-and-resubmit loop
--   OOB-JUL26-R-022  Kalaivani         — APPROVED + claim_close ₹22,875 on a ₹23,101 invoice
--                                         → the shortfall-accepted claim: pending must read 0, not ₹226
--   OOJ-JUL26-R-018  Aravind Traders   — B2B job card (is_b2b = true, gst_no set), no lines yet

DO $$
DECLARE
    v_org      BIGINT;
    v_ooj      BIGINT;
    v_oob      BIGINT;
    v_oor      BIGINT;
    v_cashier  BIGINT;   -- created_by / last_modified_by for every seeded document

    m_cash   BIGINT;
    m_qr     BIGINT;
    m_bank   BIGINT;
    m_advqr  BIGINT;
    v_sbi    BIGINT;

    v_jc     BIGINT;
    v_doc    BIGINT;
BEGIN
    SELECT id INTO v_org FROM organization WHERE name = 'JJ Motors (Demo)';
    IF v_org IS NULL THEN
        RAISE EXCEPTION 'V12 seed: demo org "JJ Motors (Demo)" not found (V5 must run first)';
    END IF;

    SELECT id INTO v_ooj FROM branch WHERE org_id = v_org AND code = 'OOJ';
    SELECT id INTO v_oob FROM branch WHERE org_id = v_org AND code = 'OOB';
    SELECT id INTO v_oor FROM branch WHERE org_id = v_org AND code = 'OOR';

    SELECT id INTO v_cashier FROM app_user WHERE email = 'cashier@jjmotors.demo';

    SELECT id INTO m_cash  FROM settlement_mode WHERE org_id = v_org AND name = 'Cash';
    SELECT id INTO m_qr    FROM settlement_mode WHERE org_id = v_org AND name = 'QR / UPI';
    SELECT id INTO m_bank  FROM settlement_mode WHERE org_id = v_org AND name = 'Bank';
    SELECT id INTO m_advqr FROM settlement_mode WHERE org_id = v_org AND name = 'Adv-QR';
    SELECT id INTO v_sbi   FROM bank            WHERE org_id = v_org AND name = 'State Bank of India';

    -- Aravind Traders is the B2B example.
    UPDATE job_card SET is_b2b = TRUE, gst_no = '21ABCDE1234F1Z5'
    WHERE org_id = v_org AND dbm_id = '4009895845';

    -- ---- 1 — Suresh Mohapatra / OOR-JUL26-R-013 — Workshop, QUERIED, ₹24,000 of ₹24,500 ----
    SELECT id INTO v_jc FROM job_card WHERE org_id = v_org AND dbm_id = '4009690979';
    INSERT INTO receive_document (org_id, branch_id, job_card_id, document_no, workflow_status, settled,
                                 created_by, last_modified_by, created_at, submitted_at)
    VALUES (v_org, v_oor, v_jc, 'OOR-JUL26-R-013', 'QUERIED', FALSE,
            v_cashier, v_cashier, TIMESTAMPTZ '2026-07-16 12:00:00+05:30', TIMESTAMPTZ '2026-07-16 12:00:00+05:30')
    RETURNING id INTO v_doc;
    INSERT INTO settlement_line (org_id, receive_document_id, line_no, line_id, transaction_date,
                                 settlement_mode_id, amount, created_by, created_at)
    VALUES (v_org, v_doc, 1, 'OOR-JUL26-R-013-L1', DATE '2026-07-16', m_cash, 24000, v_cashier,
            TIMESTAMPTZ '2026-07-16 12:00:00+05:30');

    -- ---- 2 — P Simanchala Dora / OOR-JUL26-R-012 — Advance (no invoice), APPROVED, two advances ----
    SELECT id INTO v_jc FROM job_card WHERE org_id = v_org AND dbm_id = '4009550148';
    INSERT INTO receive_document (org_id, branch_id, job_card_id, document_no, workflow_status, settled,
                                 created_by, last_modified_by, created_at, submitted_at)
    VALUES (v_org, v_oor, v_jc, 'OOR-JUL26-R-012', 'APPROVED', FALSE,
            v_cashier, v_cashier, TIMESTAMPTZ '2026-07-15 12:00:00+05:30', TIMESTAMPTZ '2026-07-15 12:00:00+05:30')
    RETURNING id INTO v_doc;
    INSERT INTO settlement_line (org_id, receive_document_id, line_no, line_id, transaction_date,
                                 settlement_mode_id, amount, created_by, created_at) VALUES
        (v_org, v_doc, 1, 'OOR-JUL26-R-012-L1', DATE '2026-07-15', m_advqr, 9000,  v_cashier, TIMESTAMPTZ '2026-07-15 12:00:00+05:30'),
        (v_org, v_doc, 2, 'OOR-JUL26-R-012-L2', DATE '2026-07-17', m_advqr, 10000, v_cashier, TIMESTAMPTZ '2026-07-17 12:00:00+05:30');

    -- ---- 3 — Naveen Khandelwal / OOR-JUL26-R-016 — Workshop, SUBMITTED, fully paid → settled ----
    SELECT id INTO v_jc FROM job_card WHERE org_id = v_org AND dbm_id = '4009870505';
    INSERT INTO receive_document (org_id, branch_id, job_card_id, document_no, workflow_status, settled,
                                 created_by, last_modified_by, created_at, submitted_at)
    VALUES (v_org, v_oor, v_jc, 'OOR-JUL26-R-016', 'SUBMITTED', TRUE,
            v_cashier, v_cashier, TIMESTAMPTZ '2026-07-17 12:00:00+05:30', TIMESTAMPTZ '2026-07-17 12:00:00+05:30')
    RETURNING id INTO v_doc;
    INSERT INTO settlement_line (org_id, receive_document_id, line_no, line_id, transaction_date,
                                 settlement_mode_id, amount, created_by, created_at)
    VALUES (v_org, v_doc, 1, 'OOR-JUL26-R-016-L1', DATE '2026-07-17', m_qr, 8600, v_cashier,
            TIMESTAMPTZ '2026-07-17 12:00:00+05:30');

    -- ---- 4 — Satya Prakash Behera / OOR-JUL26-R-014 — Workshop, VERIFIED, fully paid → settled ----
    SELECT id INTO v_jc FROM job_card WHERE org_id = v_org AND dbm_id = '4009873848';
    INSERT INTO receive_document (org_id, branch_id, job_card_id, document_no, workflow_status, settled,
                                 created_by, last_modified_by, created_at, submitted_at)
    VALUES (v_org, v_oor, v_jc, 'OOR-JUL26-R-014', 'VERIFIED', TRUE,
            v_cashier, v_cashier, TIMESTAMPTZ '2026-07-16 12:00:00+05:30', TIMESTAMPTZ '2026-07-16 12:00:00+05:30')
    RETURNING id INTO v_doc;
    INSERT INTO settlement_line (org_id, receive_document_id, line_no, line_id, transaction_date,
                                 settlement_mode_id, amount, created_by, created_at)
    VALUES (v_org, v_doc, 1, 'OOR-JUL26-R-014-L1', DATE '2026-07-16', m_qr, 4000, v_cashier,
            TIMESTAMPTZ '2026-07-16 12:00:00+05:30');

    -- ---- 5 — Sumati Sahoo / OOR-JUL26-R-011 — Workshop, APPROVED, part paid (₹12,298 of ₹15,431) ----
    SELECT id INTO v_jc FROM job_card WHERE org_id = v_org AND dbm_id = '4009941587';
    INSERT INTO receive_document (org_id, branch_id, job_card_id, document_no, workflow_status, settled,
                                 created_by, last_modified_by, created_at, submitted_at)
    VALUES (v_org, v_oor, v_jc, 'OOR-JUL26-R-011', 'APPROVED', FALSE,
            v_cashier, v_cashier, TIMESTAMPTZ '2026-07-14 12:00:00+05:30', TIMESTAMPTZ '2026-07-14 12:00:00+05:30')
    RETURNING id INTO v_doc;
    INSERT INTO settlement_line (org_id, receive_document_id, line_no, line_id, transaction_date,
                                 settlement_mode_id, amount, created_by, created_at)
    VALUES (v_org, v_doc, 1, 'OOR-JUL26-R-011-L1', DATE '2026-07-14', m_advqr, 12298, v_cashier,
            TIMESTAMPTZ '2026-07-14 12:00:00+05:30');

    -- ---- 6 — Kalaivani Logistics / OOB-JUL26-R-022 — AMC claim, closed at ₹22,875 (shortfall accepted) ----
    SELECT id INTO v_jc FROM job_card WHERE org_id = v_org AND dbm_id = '4009877403';
    INSERT INTO receive_document (org_id, branch_id, job_card_id, document_no, workflow_status, settled,
                                 created_by, last_modified_by, created_at, submitted_at)
    VALUES (v_org, v_oob, v_jc, 'OOB-JUL26-R-022', 'APPROVED', TRUE,
            v_cashier, v_cashier, TIMESTAMPTZ '2026-07-24 12:00:00+05:30', TIMESTAMPTZ '2026-07-24 12:00:00+05:30')
    RETURNING id INTO v_doc;
    INSERT INTO settlement_line (org_id, receive_document_id, line_no, line_id, transaction_date,
                                 settlement_mode_id, amount, bank_id, created_by, created_at)
    VALUES (v_org, v_doc, 1, 'OOB-JUL26-R-022-L1', DATE '2026-07-24', m_bank, 22875, v_sbi, v_cashier,
            TIMESTAMPTZ '2026-07-24 12:00:00+05:30');
    INSERT INTO claim_close (org_id, job_card_id, final_amount, overridden, override_reason, closed_by, closed_at)
    VALUES (v_org, v_jc, 22875, TRUE, 'Eicher settled the AMC claim short by ₹226 — accepted as final',
            (SELECT id FROM app_user WHERE email = 'finance@jjmotors.demo'),
            TIMESTAMPTZ '2026-07-25 11:00:00+05:30');

    -- ---- 7 — Aravind Traders / OOJ-JUL26-R-018 — B2B Credit, VERIFIED, no payment lines yet ----
    SELECT id INTO v_jc FROM job_card WHERE org_id = v_org AND dbm_id = '4009895845';
    INSERT INTO receive_document (org_id, branch_id, job_card_id, document_no, workflow_status, settled,
                                 created_by, last_modified_by, created_at, submitted_at)
    VALUES (v_org, v_ooj, v_jc, 'OOJ-JUL26-R-018', 'VERIFIED', FALSE,
            v_cashier, v_cashier, TIMESTAMPTZ '2026-07-17 12:00:00+05:30', TIMESTAMPTZ '2026-07-17 12:00:00+05:30');

    -- ---- 8 — Maruti Bricks / OOJ-JUL26-R-017 — Spare / Counter, APPROVED, fully paid → settled ----
    SELECT id INTO v_jc FROM job_card WHERE org_id = v_org AND customer_id =
        (SELECT id FROM customer WHERE org_id = v_org AND name = 'Maruti Bricks');
    INSERT INTO receive_document (org_id, branch_id, job_card_id, document_no, workflow_status, settled,
                                 created_by, last_modified_by, created_at, submitted_at)
    VALUES (v_org, v_ooj, v_jc, 'OOJ-JUL26-R-017', 'APPROVED', TRUE,
            v_cashier, v_cashier, TIMESTAMPTZ '2026-07-16 12:00:00+05:30', TIMESTAMPTZ '2026-07-16 12:00:00+05:30')
    RETURNING id INTO v_doc;
    INSERT INTO settlement_line (org_id, receive_document_id, line_no, line_id, transaction_date,
                                 settlement_mode_id, amount, created_by, created_at)
    VALUES (v_org, v_doc, 1, 'OOJ-JUL26-R-017-L1', DATE '2026-07-16', m_qr, 5727, v_cashier,
            TIMESTAMPTZ '2026-07-16 12:00:00+05:30');

    -- ---- document_sequence: record July 2026 as already used so new docs continue cleanly ----
    -- (new documents created "today" land in a fresh month key, e.g. AUG26-R-001.)
    INSERT INTO document_sequence (org_id, branch_id, month_key, doc_type, last_seq) VALUES
        (v_org, v_oor, 'JUL26', 'R', 16),
        (v_org, v_oob, 'JUL26', 'R', 22),
        (v_org, v_ooj, 'JUL26', 'R', 18);
END $$;

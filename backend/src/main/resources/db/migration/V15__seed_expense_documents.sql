-- V15: test-phase seed — a handful of expense documents so the Expense Entry screen, My
-- Entries, and the Stage 7 review queue all have data on a fresh database. Attached to the
-- demo dealership (V5) / job cards (V9). Skipped by the future prod path.
--
-- Rows chosen to cover the states Stage 5 has to demo:
--   OOR-JUL26-E-002  Naveen / Sharma Auto Spares — Spare Transport ₹1,400 on a ₹1,000
--                    limit → over_limit = true (the per-line warning + FM-approval path)
--   (draft)          Courier overhead, no job card, no number yet → drafts stay unnumbered
--   OOR-JUL26-E-003  RTO Expenses — workflow CLOSED (terminal row for the queue)
--   OOB-JUL26-E-001  Kalaivani AMC job card — business status "Transfer to Claim"
--                    (only valid because that job card's category is a claim category)

DO $$
DECLARE
    v_org      BIGINT;
    v_oob      BIGINT;
    v_oor      BIGINT;
    v_cashier  BIGINT;   -- created_by / last_modified_by for every seeded document

    -- receivers
    r_sharma   BIGINT;
    r_fuel     BIGINT;
    r_courier  BIGINT;
    r_taxi     BIGINT;

    -- expense categories
    ec_service BIGINT;
    ec_sales   BIGINT;

    -- expense sub-categories
    sc_fuel    BIGINT;
    sc_taxi    BIGINT;
    sc_spare   BIGINT;
    sc_courier BIGINT;
    sc_local   BIGINT;
    sc_rto     BIGINT;

    -- expense modes
    em_cash    BIGINT;
    em_qr      BIGINT;
    em_bank    BIGINT;

    -- expense business statuses
    es_open    BIGINT;
    es_prog    BIGINT;
    es_await   BIGINT;
    es_closed  BIGINT;
    es_claim   BIGINT;

    v_sbi      BIGINT;

    jc_sumati  BIGINT;
    jc_naveen  BIGINT;
    jc_kalai   BIGINT;

    v_doc      BIGINT;
BEGIN
    SELECT id INTO v_org FROM organization WHERE name = 'JJ Motors (Demo)';
    IF v_org IS NULL THEN
        RAISE EXCEPTION 'V15 seed: demo org "JJ Motors (Demo)" not found (V5 must run first)';
    END IF;

    SELECT id INTO v_oob FROM branch WHERE org_id = v_org AND code = 'OOB';
    SELECT id INTO v_oor FROM branch WHERE org_id = v_org AND code = 'OOR';
    SELECT id INTO v_cashier FROM app_user WHERE email = 'cashier@jjmotors.demo';

    SELECT id INTO r_sharma  FROM receiver WHERE org_id = v_org AND name = 'Sharma Auto Spares';
    SELECT id INTO r_fuel    FROM receiver WHERE org_id = v_org AND name = 'City Fuel Station';
    SELECT id INTO r_courier FROM receiver WHERE org_id = v_org AND name = 'Quick Courier Services';
    SELECT id INTO r_taxi    FROM receiver WHERE org_id = v_org AND name = 'Rayagada Taxi Union';

    SELECT id INTO ec_service FROM expense_category WHERE org_id = v_org AND name = 'Service';
    SELECT id INTO ec_sales   FROM expense_category WHERE org_id = v_org AND name = 'Sales';

    SELECT id INTO sc_fuel    FROM expense_sub_category WHERE org_id = v_org AND name = 'Fuel (JC)';
    SELECT id INTO sc_taxi    FROM expense_sub_category WHERE org_id = v_org AND name = 'Taxi (JC)';
    SELECT id INTO sc_spare   FROM expense_sub_category WHERE org_id = v_org AND name = 'Spare Transport';
    SELECT id INTO sc_courier FROM expense_sub_category WHERE org_id = v_org AND name = 'Courier Charges';
    SELECT id INTO sc_local   FROM expense_sub_category WHERE org_id = v_org AND name = 'Local Purchase (JC)';
    SELECT id INTO sc_rto     FROM expense_sub_category WHERE org_id = v_org AND name = 'RTO Expenses';

    SELECT id INTO em_cash FROM expense_mode WHERE org_id = v_org AND name = 'Cash';
    SELECT id INTO em_qr   FROM expense_mode WHERE org_id = v_org AND name = 'QR / UPI';
    SELECT id INTO em_bank FROM expense_mode WHERE org_id = v_org AND name = 'Bank';

    SELECT id INTO es_open   FROM expense_business_status WHERE org_id = v_org AND name = 'Open';
    SELECT id INTO es_prog   FROM expense_business_status WHERE org_id = v_org AND name = 'In Progress';
    SELECT id INTO es_await  FROM expense_business_status WHERE org_id = v_org AND name = 'Awaiting Receipt';
    SELECT id INTO es_closed FROM expense_business_status WHERE org_id = v_org AND name = 'Closed';
    SELECT id INTO es_claim  FROM expense_business_status WHERE org_id = v_org AND name = 'Transfer to Claim';

    SELECT id INTO v_sbi FROM bank WHERE org_id = v_org AND name = 'State Bank of India';

    SELECT id INTO jc_sumati FROM job_card WHERE org_id = v_org AND dbm_id = '4009941587';
    SELECT id INTO jc_naveen FROM job_card WHERE org_id = v_org AND dbm_id = '4009870505';
    SELECT id INTO jc_kalai  FROM job_card WHERE org_id = v_org AND dbm_id = '4009877403';

    -- ---- 1 — Fuel + taxi for Sumati's repair — Service, SUBMITTED, within limits ----
    INSERT INTO expense_document (org_id, branch_id, job_card_id, receiver_id, expense_category_id,
                                 business_status_id, document_no, workflow_status, over_limit,
                                 created_by, last_modified_by, created_at, submitted_at)
    VALUES (v_org, v_oor, jc_sumati, r_fuel, ec_service, es_await, 'OOR-JUL26-E-001', 'SUBMITTED', FALSE,
            v_cashier, v_cashier, TIMESTAMPTZ '2026-07-18 12:00:00+05:30', TIMESTAMPTZ '2026-07-18 12:00:00+05:30')
    RETURNING id INTO v_doc;
    INSERT INTO expense_line (org_id, expense_document_id, line_no, line_id, transaction_date,
                             sub_category_id, expense_mode_id, amount, transaction_ref, created_by, created_at) VALUES
        (v_org, v_doc, 1, 'OOR-JUL26-E-001-L1', DATE '2026-07-18', sc_fuel, em_qr,   800,  'UPI/230718', v_cashier, TIMESTAMPTZ '2026-07-18 12:00:00+05:30'),
        (v_org, v_doc, 2, 'OOR-JUL26-E-001-L2', DATE '2026-07-18', sc_taxi, em_cash, 1500, NULL,         v_cashier, TIMESTAMPTZ '2026-07-18 12:00:00+05:30');

    -- ---- 2 — Spare transport for Naveen's repair — Service, SUBMITTED, OVER LIMIT (₹1,400 > ₹1,000) ----
    INSERT INTO expense_document (org_id, branch_id, job_card_id, receiver_id, expense_category_id,
                                 business_status_id, document_no, workflow_status, over_limit,
                                 created_by, last_modified_by, created_at, submitted_at)
    VALUES (v_org, v_oor, jc_naveen, r_sharma, ec_service, es_prog, 'OOR-JUL26-E-002', 'SUBMITTED', TRUE,
            v_cashier, v_cashier, TIMESTAMPTZ '2026-07-19 12:00:00+05:30', TIMESTAMPTZ '2026-07-19 12:00:00+05:30')
    RETURNING id INTO v_doc;
    INSERT INTO expense_line (org_id, expense_document_id, line_no, line_id, transaction_date,
                             sub_category_id, expense_mode_id, amount, created_by, created_at)
    VALUES (v_org, v_doc, 1, 'OOR-JUL26-E-002-L1', DATE '2026-07-19', sc_spare, em_cash, 1400, v_cashier,
            TIMESTAMPTZ '2026-07-19 12:00:00+05:30');

    -- ---- 3 — Courier charges, branch overhead (no job card) — Service, DRAFT, unnumbered ----
    INSERT INTO expense_document (org_id, branch_id, job_card_id, receiver_id, expense_category_id,
                                 business_status_id, document_no, workflow_status, over_limit,
                                 created_by, last_modified_by, created_at, submitted_at)
    VALUES (v_org, v_oor, NULL, r_courier, ec_service, es_open, NULL, 'DRAFT', FALSE,
            v_cashier, v_cashier, TIMESTAMPTZ '2026-07-20 12:00:00+05:30', NULL)
    RETURNING id INTO v_doc;
    INSERT INTO expense_line (org_id, expense_document_id, line_no, line_id, transaction_date,
                             sub_category_id, expense_mode_id, amount, created_by, created_at)
    VALUES (v_org, v_doc, 1, NULL, DATE '2026-07-20', sc_courier, em_cash, 300, v_cashier,
            TIMESTAMPTZ '2026-07-20 12:00:00+05:30');

    -- ---- 4 — RTO expenses, branch overhead (no job card) — Sales, workflow CLOSED ----
    INSERT INTO expense_document (org_id, branch_id, job_card_id, receiver_id, expense_category_id,
                                 business_status_id, document_no, workflow_status, over_limit,
                                 created_by, last_modified_by, created_at, submitted_at)
    VALUES (v_org, v_oor, NULL, r_taxi, ec_sales, es_closed, 'OOR-JUL26-E-003', 'CLOSED', FALSE,
            v_cashier, v_cashier, TIMESTAMPTZ '2026-07-21 12:00:00+05:30', TIMESTAMPTZ '2026-07-21 12:00:00+05:30')
    RETURNING id INTO v_doc;
    INSERT INTO expense_line (org_id, expense_document_id, line_no, line_id, transaction_date,
                             sub_category_id, expense_mode_id, amount, created_by, created_at)
    VALUES (v_org, v_doc, 1, 'OOR-JUL26-E-003-L1', DATE '2026-07-21', sc_rto, em_cash, 2800, v_cashier,
            TIMESTAMPTZ '2026-07-21 12:00:00+05:30');

    -- ---- 5 — Local purchase for the Kalaivani AMC claim — Berhampur, business status "Transfer to Claim" ----
    -- Valid only because job_card 4009877403's category (AMC) is a claim category.
    -- created_by is the demo cashier for seed convenience; a real transfer is a post-submit action.
    INSERT INTO expense_document (org_id, branch_id, job_card_id, receiver_id, expense_category_id,
                                 business_status_id, document_no, workflow_status, over_limit,
                                 created_by, last_modified_by, created_at, submitted_at)
    VALUES (v_org, v_oob, jc_kalai, r_sharma, ec_service, es_claim, 'OOB-JUL26-E-001', 'SUBMITTED', FALSE,
            v_cashier, v_cashier, TIMESTAMPTZ '2026-07-24 12:00:00+05:30', TIMESTAMPTZ '2026-07-24 12:00:00+05:30')
    RETURNING id INTO v_doc;
    INSERT INTO expense_line (org_id, expense_document_id, line_no, line_id, transaction_date,
                             sub_category_id, expense_mode_id, amount, bank_id, transaction_ref, created_by, created_at)
    VALUES (v_org, v_doc, 1, 'OOB-JUL26-E-001-L1', DATE '2026-07-24', sc_local, em_bank, 2000, v_sbi, 'NEFT/99812',
            v_cashier, TIMESTAMPTZ '2026-07-24 12:00:00+05:30');

    -- ---- document_sequence: July 2026 E counters, so new documents continue cleanly ----
    INSERT INTO document_sequence (org_id, branch_id, month_key, doc_type, last_seq) VALUES
        (v_org, v_oor, 'JUL26', 'E', 3),
        (v_org, v_oob, 'JUL26', 'E', 1);
END $$;

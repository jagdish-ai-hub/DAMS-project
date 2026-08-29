-- V9: test-phase seed — sample customers, vehicles and one job card each, matching the
-- names / vehicle numbers / invoice values in the cashier-home.html mockup. Attached to
-- the demo dealership from V5. Skipped by the future prod seed path (see plan.md).

DO $$
DECLARE
    v_org BIGINT;
    v_ooj BIGINT;
    v_oob BIGINT;
    v_oor BIGINT;

    -- receive_category ids
    c_workshop BIGINT;
    c_advance  BIGINT;
    c_amc      BIGINT;
    c_b2b      BIGINT;
    c_spare    BIGINT;

    -- receive_business_status ids
    s_wip    BIGINT;
    s_amc    BIGINT;
    s_credit BIGINT;
    s_close  BIGINT;

    v_cust BIGINT;
    v_veh  BIGINT;
BEGIN
    SELECT id INTO v_org FROM organization WHERE name = 'JJ Motors (Demo)';
    IF v_org IS NULL THEN
        RAISE EXCEPTION 'V9 seed: demo org "JJ Motors (Demo)" not found (V5 must run first)';
    END IF;

    SELECT id INTO v_ooj FROM branch WHERE org_id = v_org AND code = 'OOJ';
    SELECT id INTO v_oob FROM branch WHERE org_id = v_org AND code = 'OOB';
    SELECT id INTO v_oor FROM branch WHERE org_id = v_org AND code = 'OOR';

    SELECT id INTO c_workshop FROM receive_category WHERE org_id = v_org AND name = 'Workshop';
    SELECT id INTO c_advance  FROM receive_category WHERE org_id = v_org AND name = 'Advance';
    SELECT id INTO c_amc      FROM receive_category WHERE org_id = v_org AND name = 'AMC';
    SELECT id INTO c_b2b      FROM receive_category WHERE org_id = v_org AND name = 'B2B Credit';
    SELECT id INTO c_spare    FROM receive_category WHERE org_id = v_org AND name = 'Spare / Counter';

    SELECT id INTO s_wip    FROM receive_business_status WHERE org_id = v_org AND name = 'WIP';
    SELECT id INTO s_amc    FROM receive_business_status WHERE org_id = v_org AND name = 'AMC';
    SELECT id INTO s_credit FROM receive_business_status WHERE org_id = v_org AND name = 'Credit';
    SELECT id INTO s_close  FROM receive_business_status WHERE org_id = v_org AND name = 'Close';

    -- 1 — Suresh Mohapatra / OD18J4563 — Workshop, Rayagada
    INSERT INTO customer (org_id, name) VALUES (v_org, 'Suresh Mohapatra') RETURNING id INTO v_cust;
    INSERT INTO vehicle (org_id, customer_id, vehicle_no) VALUES (v_org, v_cust, 'OD18J4563') RETURNING id INTO v_veh;
    INSERT INTO job_card (org_id, branch_id, customer_id, vehicle_id, dbm_id, invoice_no, invoice_amount, category_id, business_status_id)
    VALUES (v_org, v_oor, v_cust, v_veh, '4009690979', '7731122600401', 24500, c_workshop, s_wip);

    -- 2 — P Simanchala Dora / OD07AH6251 — Advance (no invoice yet), Rayagada
    INSERT INTO customer (org_id, name) VALUES (v_org, 'P Simanchala Dora') RETURNING id INTO v_cust;
    INSERT INTO vehicle (org_id, customer_id, vehicle_no) VALUES (v_org, v_cust, 'OD07AH6251') RETURNING id INTO v_veh;
    INSERT INTO job_card (org_id, branch_id, customer_id, vehicle_id, dbm_id, invoice_no, invoice_amount, category_id, business_status_id)
    VALUES (v_org, v_oor, v_cust, v_veh, '4009550148', NULL, NULL, c_advance, s_wip);

    -- 3 — Naveen Khandelwal / OD02CE1071 — Workshop, Rayagada
    INSERT INTO customer (org_id, name) VALUES (v_org, 'Naveen Khandelwal') RETURNING id INTO v_cust;
    INSERT INTO vehicle (org_id, customer_id, vehicle_no) VALUES (v_org, v_cust, 'OD02CE1071') RETURNING id INTO v_veh;
    INSERT INTO job_card (org_id, branch_id, customer_id, vehicle_id, dbm_id, invoice_no, invoice_amount, category_id, business_status_id)
    VALUES (v_org, v_oor, v_cust, v_veh, '4009870505', '7731122600412', 8600, c_workshop, s_wip);

    -- 4 — Satya Prakash Behera / OD05CG0371 — Workshop, Rayagada
    INSERT INTO customer (org_id, name) VALUES (v_org, 'Satya Prakash Behera') RETURNING id INTO v_cust;
    INSERT INTO vehicle (org_id, customer_id, vehicle_no) VALUES (v_org, v_cust, 'OD05CG0371') RETURNING id INTO v_veh;
    INSERT INTO job_card (org_id, branch_id, customer_id, vehicle_id, dbm_id, invoice_no, invoice_amount, category_id, business_status_id)
    VALUES (v_org, v_oor, v_cust, v_veh, '4009873848', '7731122600409', 4000, c_workshop, s_wip);

    -- 5 — Sumati Sahoo / OD05CA4177 — Workshop, Rayagada (the cashier-home headline example)
    INSERT INTO customer (org_id, name) VALUES (v_org, 'Sumati Sahoo') RETURNING id INTO v_cust;
    INSERT INTO vehicle (org_id, customer_id, vehicle_no) VALUES (v_org, v_cust, 'OD05CA4177') RETURNING id INTO v_veh;
    INSERT INTO job_card (org_id, branch_id, customer_id, vehicle_id, dbm_id, invoice_no, invoice_amount, category_id, business_status_id)
    VALUES (v_org, v_oor, v_cust, v_veh, '4009941587', '7731122600388', 15431, c_workshop, s_wip);

    -- 6 — Kalaivani Logistics Pvt Ltd / TN04BC1778 — AMC claim, Berhampur
    INSERT INTO customer (org_id, name) VALUES (v_org, 'Kalaivani Logistics Pvt Ltd') RETURNING id INTO v_cust;
    INSERT INTO vehicle (org_id, customer_id, vehicle_no) VALUES (v_org, v_cust, 'TN04BC1778') RETURNING id INTO v_veh;
    INSERT INTO job_card (org_id, branch_id, customer_id, vehicle_id, dbm_id, invoice_no, invoice_amount, category_id, business_status_id)
    VALUES (v_org, v_oob, v_cust, v_veh, '4009877403', 'AMC-2026-118', 23101, c_amc, s_amc);

    -- 7 — Aravind Traders / AP39UM4519 — B2B Credit, Jeypore
    INSERT INTO customer (org_id, name) VALUES (v_org, 'Aravind Traders') RETURNING id INTO v_cust;
    INSERT INTO vehicle (org_id, customer_id, vehicle_no) VALUES (v_org, v_cust, 'AP39UM4519') RETURNING id INTO v_veh;
    INSERT INTO job_card (org_id, branch_id, customer_id, vehicle_id, dbm_id, invoice_no, invoice_amount, category_id, business_status_id)
    VALUES (v_org, v_ooj, v_cust, v_veh, '4009895845', '7731162600071', 44530, c_b2b, s_credit);

    -- 8 — Maruti Bricks — counter sale, no vehicle, no dbm id, Jeypore
    INSERT INTO customer (org_id, name) VALUES (v_org, 'Maruti Bricks') RETURNING id INTO v_cust;
    INSERT INTO job_card (org_id, branch_id, customer_id, vehicle_id, dbm_id, invoice_no, invoice_amount, category_id, business_status_id)
    VALUES (v_org, v_ooj, v_cust, NULL, NULL, '7731162600070', 5727, c_spare, s_close);
END $$;

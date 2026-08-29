-- V5: test-phase seed — one dummy dealership, its branches, the default master rows
-- (matching the HTML mockups), sample receivers, and one login per role with a known
-- password. Runs in every environment during the test phase. A later prod path will
-- skip the *seed* migrations and seed masters only (see plan.md).

DO $$
DECLARE
    v_org_id     BIGINT;
    v_ooj        BIGINT;
    v_oob        BIGINT;
    v_oor        BIGINT;
    v_svc        BIGINT;
    v_sal        BIGINT;
    v_sho        BIGINT;
    v_fin        BIGINT;
    v_accountant BIGINT;
BEGIN
    INSERT INTO organization (name, multi_branch_cashier_access, active)
    VALUES ('JJ Motors (Demo)', FALSE, TRUE)
    RETURNING id INTO v_org_id;

    INSERT INTO branch (org_id, name, code) VALUES (v_org_id, 'Jeypore',   'OOJ') RETURNING id INTO v_ooj;
    INSERT INTO branch (org_id, name, code) VALUES (v_org_id, 'Berhampur', 'OOB') RETURNING id INTO v_oob;
    INSERT INTO branch (org_id, name, code) VALUES (v_org_id, 'Rayagada',  'OOR') RETURNING id INTO v_oor;

    -- Receive categories — is_claim TRUE for AMC / Warranty / Goodwill (FM closes these with a ClaimClose)
    INSERT INTO receive_category (org_id, name, is_claim, sort_order) VALUES
        (v_org_id, 'Workshop',                   FALSE, 1),
        (v_org_id, 'Breakdown',                  FALSE, 2),
        (v_org_id, 'Advance',                    FALSE, 3),
        (v_org_id, 'Spare / Counter',            FALSE, 4),
        (v_org_id, 'AdBlue Bucket',              FALSE, 5),
        (v_org_id, 'AdBlue Barrel',              FALSE, 6),
        (v_org_id, 'AMC',                        TRUE,  7),
        (v_org_id, 'Warranty',                   TRUE,  8),
        (v_org_id, 'Goodwill',                   TRUE,  9),
        (v_org_id, 'B2B Credit',                 FALSE, 10),
        (v_org_id, 'Scrap / Used Lubes / Other', FALSE, 11);

    INSERT INTO receive_business_status (org_id, name, sort_order) VALUES
        (v_org_id, 'Hold', 1), (v_org_id, 'AMC', 2), (v_org_id, 'CG', 3), (v_org_id, 'WIP', 4),
        (v_org_id, 'Warranty', 5), (v_org_id, 'Credit', 6), (v_org_id, 'Close', 7);

    INSERT INTO settlement_mode (org_id, name, requires_bank, requires_ref, sort_order) VALUES
        (v_org_id, 'Cash',         FALSE, FALSE, 1),
        (v_org_id, 'QR / UPI',     FALSE, TRUE,  2),
        (v_org_id, 'Bank',         TRUE,  TRUE,  3),
        (v_org_id, 'Card',         FALSE, FALSE, 4),
        (v_org_id, 'Adv-QR',       FALSE, TRUE,  5),
        (v_org_id, 'Adv-Cash',     FALSE, FALSE, 6),
        (v_org_id, 'Credit (Due)', FALSE, FALSE, 7);

    INSERT INTO expense_category (org_id, name, sort_order) VALUES (v_org_id, 'Service',  1) RETURNING id INTO v_svc;
    INSERT INTO expense_category (org_id, name, sort_order) VALUES (v_org_id, 'Sales',    2) RETURNING id INTO v_sal;
    INSERT INTO expense_category (org_id, name, sort_order) VALUES (v_org_id, 'Showroom', 3) RETURNING id INTO v_sho;
    INSERT INTO expense_category (org_id, name, sort_order) VALUES (v_org_id, 'Finance',  4) RETURNING id INTO v_fin;

    -- Old Finance sub-categories 'Cash Deposit' / 'Cash Out' are intentionally omitted —
    -- replaced by the Cash page (AGENT.md locked decision #1). Finance starts with none.
    INSERT INTO expense_sub_category (org_id, expense_category_id, name, limit_amount, sort_order) VALUES
        (v_org_id, v_svc, 'Food (BD)',           500,  1),
        (v_org_id, v_svc, 'Spare Transport',     1000, 2),
        (v_org_id, v_svc, 'Taxi (JC)',           2000, 3),
        (v_org_id, v_svc, 'Fuel (JC)',           1000, 4),
        (v_org_id, v_svc, 'Local Purchase (JC)', 2000, 5),
        (v_org_id, v_svc, 'Courier Charges',     500,  6),
        (v_org_id, v_sho, 'Stationary',          1500, 1),
        (v_org_id, v_sho, 'Misc. Office',        2000, 2),
        (v_org_id, v_sho, 'Site Repair',         5000, 3),
        (v_org_id, v_sho, 'Daily Wages',         5000, 4),
        (v_org_id, v_sal, 'Sales Promotion',     5000, 1),
        (v_org_id, v_sal, 'RTO Expenses',        3000, 2),
        (v_org_id, v_sal, 'Misc. Sales',         2000, 3);

    INSERT INTO expense_mode (org_id, name, sort_order) VALUES
        (v_org_id, 'Cash', 1), (v_org_id, 'QR / UPI', 2), (v_org_id, 'Bank', 3);

    INSERT INTO expense_business_status (org_id, name, sort_order) VALUES
        (v_org_id, 'Open', 1), (v_org_id, 'In Progress', 2), (v_org_id, 'Awaiting Receipt', 3),
        (v_org_id, 'Received Receipt', 4), (v_org_id, 'Closed', 5), (v_org_id, 'Transfer to Claim', 6);

    INSERT INTO bank (org_id, name, sort_order) VALUES
        (v_org_id, 'State Bank of India', 1), (v_org_id, 'HDFC Bank', 2), (v_org_id, 'ICICI Bank', 3),
        (v_org_id, 'Axis Bank', 4), (v_org_id, 'Bank of Baroda', 5), (v_org_id, 'Punjab National Bank', 6);

    INSERT INTO receiver (org_id, name, phone) VALUES
        (v_org_id, 'Sharma Auto Spares',      '9437011111'),
        (v_org_id, 'City Fuel Station',       NULL),
        (v_org_id, 'Quick Courier Services',  '9437022222'),
        (v_org_id, 'Rayagada Taxi Union',     NULL);

    -- One login per role. Passwords (bcrypt cost 10): owner123 / finance123 / accountant123 / cashier123.
    -- These bypass the invite flow — test convenience only. Documented in the README.
    INSERT INTO app_user (org_id, name, email, password_hash, role, active) VALUES
        (v_org_id, 'Priya Nair',   'owner@jjmotors.demo',
         '$2b$10$sqd/HT6n1gJFa8w68XcK0O4VRHA/R9YePXU7ZVMZLs74olSTCxHqK', 'OWNER', TRUE),
        (v_org_id, 'Rakesh Menon', 'finance@jjmotors.demo',
         '$2b$10$3pFVwmrtDfkM.8Ss80AaIu0BD7VYIBmmUdwHIQZ5cFnLecf/.jt7C', 'FINANCE_MANAGER', TRUE);

    INSERT INTO app_user (org_id, name, email, password_hash, role, active) VALUES
        (v_org_id, 'Anita Rao', 'accountant@jjmotors.demo',
         '$2b$10$8KzSZEBhwi22WYwyWEGHdOxFC0iDobREY4wZi2e.eo1ISw9xHSSMm', 'ACCOUNTANT', TRUE)
    RETURNING id INTO v_accountant;

    INSERT INTO user_branch_access (user_id, branch_id) VALUES (v_accountant, v_oob), (v_accountant, v_oor);

    -- Cashier posts under Rayagada — matches the cashier-home mockup ("DAMS — Rayagada", Bikram Nayak).
    INSERT INTO app_user (org_id, home_branch_id, name, email, password_hash, role, active) VALUES
        (v_org_id, v_oor, 'Bikram Nayak', 'cashier@jjmotors.demo',
         '$2b$10$gPsvZpB0b0ooM3QlL6NLoulJAEEVYVWvoEfH1TaR7YmcQHerz0z92', 'CASHIER', TRUE);
END $$;

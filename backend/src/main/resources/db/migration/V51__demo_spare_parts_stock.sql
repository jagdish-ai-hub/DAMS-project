-- rev 96: a short demo stock list for the Spare Parts module, in the demo organization only (the one that
-- owns owner@jjmotors.demo) -- six parts, held at one to three branches each, so every branch has something
-- to search, bill and request. Each quantity is written the way the app writes it: the stock row plus one
-- MANUAL_ADD ledger row (qty_after = the quantity), so History and Overview agree with Stock.
-- Idempotent and non-destructive: nothing is added if the demo organization already has any part.
DO $$
DECLARE
    v_org  BIGINT;
    v_user BIGINT;
    v_part BIGINT;
    r      RECORD;
BEGIN
    SELECT org_id INTO v_org FROM app_user WHERE email = 'owner@jjmotors.demo';
    IF v_org IS NULL OR EXISTS (SELECT 1 FROM inv_part WHERE org_id = v_org) THEN
        RETURN;
    END IF;
    SELECT id INTO v_user FROM app_user WHERE email = 'inventory@jjmotors.demo';

    FOR r IN
        SELECT * FROM (VALUES
            -- code,        description,                 price, bin,   OOJ, OOB, OOR   (0 = this branch holds none)
            ('EI-BRK-001', 'Brake shoe set - Pro 2049',  1450, 'A-12', 24,  6,  10),
            ('EI-CLT-014', 'Clutch plate assembly',      3850, 'B-03',  5,  0,   2),
            ('EI-FLT-220', 'Engine oil filter',           420, 'C-21', 60, 35,  48),
            ('EI-AIR-118', 'Air filter element',          680, 'C-22', 18, 22,   4),
            ('EI-HLB-050', 'Headlamp bulb 12V 55W',       150, 'D-05', 40, 40,  12),
            ('EI-OIL-5W',  'Gear oil 80W-90 (1 L)',       390, 'F-01', 70, 28,  33)
        ) AS t(code, descr, price, bin, ooj, oob, oor)
    LOOP
        INSERT INTO inv_part (org_id, part_code, description, unit_price)
        VALUES (v_org, r.code, r.descr, r.price) RETURNING id INTO v_part;

        INSERT INTO inv_stock (org_id, branch_id, part_id, bin, quantity)
        SELECT v_org, b.id, v_part, r.bin, q.qty
        FROM (VALUES ('OOJ', r.ooj), ('OOB', r.oob), ('OOR', r.oor)) AS q(code, qty)
        JOIN branch b ON b.org_id = v_org AND b.code = q.code
        WHERE q.qty > 0;

        INSERT INTO inv_movement (org_id, branch_id, part_id, movement_type, qty_change, qty_after, note, created_by)
        SELECT v_org, s.branch_id, v_part, 'MANUAL_ADD', s.quantity, s.quantity, 'Demo stock', v_user
        FROM inv_stock s WHERE s.part_id = v_part;
    END LOOP;
END $$;

-- rev 95: demo logins for the Spare Parts module, and the module switched ON for the demo organization.
-- Passwords: inventory123 / storekeeper123 (bcrypt cost 10) -- test convenience only, like the other demo
-- logins in V5; they bypass the invite flow. The demo organization is the one that owns owner@jjmotors.demo.
-- Idempotent: an existing account with the same email is left alone.
--   inventory@jjmotors.demo   INVENTORY_MANAGER  all three branches (Jeypore, Berhampur, Rayagada)
--   storekeeper@jjmotors.demo STORE_KEEPER       Rayagada only
DO $$
DECLARE
    v_org BIGINT;
    v_mgr BIGINT;
    v_keeper BIGINT;
BEGIN
    SELECT org_id INTO v_org FROM app_user WHERE email = 'owner@jjmotors.demo';
    IF v_org IS NULL THEN
        RETURN;   -- no demo organization here (e.g. a fresh production-only database)
    END IF;

    UPDATE organization SET spare_parts_enabled = TRUE WHERE id = v_org;

    IF NOT EXISTS (SELECT 1 FROM app_user WHERE email = 'inventory@jjmotors.demo') THEN
        INSERT INTO app_user (org_id, name, email, password_hash, role, active)
        VALUES (v_org, 'Meera Patnaik', 'inventory@jjmotors.demo',
                '$2a$10$XY8VJTtS2thFUmz7ig0LTuJ/GnvWsiQiORkjYWTSWeo3/cEO/A6Mu', 'INVENTORY_MANAGER', TRUE)
        RETURNING id INTO v_mgr;
        INSERT INTO user_branch_access (user_id, branch_id)
        SELECT v_mgr, b.id FROM branch b WHERE b.org_id = v_org AND b.code IN ('OOJ', 'OOB', 'OOR');
    END IF;

    IF NOT EXISTS (SELECT 1 FROM app_user WHERE email = 'storekeeper@jjmotors.demo') THEN
        INSERT INTO app_user (org_id, name, email, password_hash, role, active)
        VALUES (v_org, 'Sanjay Behera', 'storekeeper@jjmotors.demo',
                '$2a$10$HYJH4T48SsfXAqJ6Uv5VluThPhz8KgSlg.2PXj9SMXOdY5CpJ.6ky', 'STORE_KEEPER', TRUE)
        RETURNING id INTO v_keeper;
        INSERT INTO user_branch_access (user_id, branch_id)
        SELECT v_keeper, b.id FROM branch b WHERE b.org_id = v_org AND b.code = 'OOR';
    END IF;
END $$;

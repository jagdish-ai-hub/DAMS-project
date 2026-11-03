-- rev 88: the demo dealership's master lists and Owner settings are the standard for every organization.
--
-- 1. organization.master_template marks the one organization whose lists new organizations copy. It is the
--    oldest organization (the V5 demo dealership, renamed since) — chosen by age, never by name or id.
-- 2. One-time copy, decided by the owner: every other organization that has recorded nothing yet (no job
--    cards, receipts, expenses, cash movements or vendors) gets its lists replaced by copies of the template's
--    ACTIVE items — flags, limits, sort order and the roles allowed to pick each receipt status included — and
--    takes the template's two Owner switches. An organization that has recorded anything is left alone; the
--    RESTRICT foreign keys from the transaction tables would refuse the delete anyway.
-- Not copied: UPI IDs and vendors (each dealership's own), users, transactions, the organization name. After
-- this, each Owner may adjust their own lists; nothing keeps them in sync.

ALTER TABLE organization ADD COLUMN master_template BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE organization SET master_template = TRUE WHERE id = (SELECT MIN(id) FROM organization);

CREATE UNIQUE INDEX organization_master_template_uq ON organization (master_template) WHERE master_template;

DO $$
DECLARE
    v_tpl BIGINT;
    v_org BIGINT;
BEGIN
    SELECT id INTO v_tpl FROM organization WHERE master_template;
    IF v_tpl IS NULL THEN
        RETURN;
    END IF;

    FOR v_org IN
        SELECT o.id FROM organization o
        WHERE o.id <> v_tpl
          AND NOT EXISTS (SELECT 1 FROM job_card         WHERE org_id = o.id)
          AND NOT EXISTS (SELECT 1 FROM receive_document WHERE org_id = o.id)
          AND NOT EXISTS (SELECT 1 FROM expense_document WHERE org_id = o.id)
          AND NOT EXISTS (SELECT 1 FROM cash_document    WHERE org_id = o.id)
          AND NOT EXISTS (SELECT 1 FROM receiver         WHERE org_id = o.id)
    LOOP
        -- Clear the organization's own lists (children first).
        DELETE FROM receive_business_status_role WHERE org_id = v_org;
        DELETE FROM receive_business_status      WHERE org_id = v_org;
        DELETE FROM receive_category             WHERE org_id = v_org;
        DELETE FROM settlement_mode              WHERE org_id = v_org;
        DELETE FROM claim_type                   WHERE org_id = v_org;
        DELETE FROM expense_sub_category         WHERE org_id = v_org;
        DELETE FROM expense_category             WHERE org_id = v_org;
        DELETE FROM expense_mode                 WHERE org_id = v_org;
        DELETE FROM expense_business_status      WHERE org_id = v_org;
        DELETE FROM bank                         WHERE org_id = v_org;

        -- Copy the template's active items.
        INSERT INTO receive_category (org_id, name, is_claim, active, sort_order)
            SELECT v_org, name, is_claim, TRUE, sort_order FROM receive_category WHERE org_id = v_tpl AND active;

        -- Retired (deprecated) statuses exist only so the template's old job cards still read; not carried over.
        INSERT INTO receive_business_status (org_id, name, active, sort_order, deprecated)
            SELECT v_org, name, TRUE, sort_order, FALSE
            FROM receive_business_status WHERE org_id = v_tpl AND active AND NOT deprecated;

        INSERT INTO receive_business_status_role (org_id, status_id, role)
            SELECT v_org, n.id, r.role
            FROM receive_business_status_role r
            JOIN receive_business_status t ON t.id = r.status_id AND t.org_id = v_tpl AND t.active AND NOT t.deprecated
            JOIN receive_business_status n ON n.org_id = v_org AND n.name = t.name
            WHERE r.org_id = v_tpl;

        INSERT INTO settlement_mode (org_id, name, requires_bank, requires_ref, is_cash, active, sort_order)
            SELECT v_org, name, requires_bank, requires_ref, is_cash, TRUE, sort_order
            FROM settlement_mode WHERE org_id = v_tpl AND active;

        INSERT INTO claim_type (org_id, name, active, sort_order)
            SELECT v_org, name, TRUE, sort_order FROM claim_type WHERE org_id = v_tpl AND active;

        INSERT INTO expense_category (org_id, name, active, sort_order)
            SELECT v_org, name, TRUE, sort_order FROM expense_category WHERE org_id = v_tpl AND active;

        INSERT INTO expense_sub_category (org_id, expense_category_id, name, limit_amount, active, sort_order)
            SELECT v_org, nc.id, s.name, s.limit_amount, TRUE, s.sort_order
            FROM expense_sub_category s
            JOIN expense_category tc ON tc.id = s.expense_category_id AND tc.active
            JOIN expense_category nc ON nc.org_id = v_org AND nc.name = tc.name
            WHERE s.org_id = v_tpl AND s.active;

        INSERT INTO expense_mode (org_id, name, requires_bank, requires_ref, is_cash, active, sort_order)
            SELECT v_org, name, requires_bank, requires_ref, is_cash, TRUE, sort_order
            FROM expense_mode WHERE org_id = v_tpl AND active;

        INSERT INTO expense_business_status (org_id, name, triggers_claim, requires_fm_approval, active, sort_order)
            SELECT v_org, name, triggers_claim, requires_fm_approval, TRUE, sort_order
            FROM expense_business_status WHERE org_id = v_tpl AND active;

        INSERT INTO bank (org_id, name, active, sort_order)
            SELECT v_org, name, TRUE, sort_order FROM bank WHERE org_id = v_tpl AND active;

        -- The Owner's Settings switches.
        UPDATE organization o
        SET accountant_direct_approve_cash = t.accountant_direct_approve_cash,
            multi_branch_cashier_access    = t.multi_branch_cashier_access
        FROM organization t
        WHERE t.id = v_tpl AND o.id = v_org;
    END LOOP;
END $$;

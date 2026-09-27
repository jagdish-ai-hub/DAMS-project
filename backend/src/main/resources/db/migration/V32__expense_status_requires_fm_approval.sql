-- rev 54: an expense business status can require Finance Manager approval before the
-- expense is submitted — the same pre-approval flow an over-limit expense goes through
-- (rev 53, V31). Checked by flag, never by label (AGENT.md locked decision #3), so an Owner
-- can rename it or mark another status the same way through masters management.
--
--   expense_business_status.requires_fm_approval
--       When set on an expense's status, the Cashier gets "Send for Review" instead of
--       Submit; the FM approves or queries it; after an approved submit the Accountant
--       closes it without a second FM approval.
--
-- Every existing organization gets a "Requires Finance Approval" status (sorted last);
-- new organizations get it from MasterProvisioningService.

ALTER TABLE expense_business_status
    ADD COLUMN requires_fm_approval BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO expense_business_status (org_id, name, active, sort_order, triggers_claim, requires_fm_approval)
SELECT o.id,
       'Requires Finance Approval',
       TRUE,
       COALESCE((SELECT MAX(s.sort_order) FROM expense_business_status s WHERE s.org_id = o.id), 0) + 1,
       FALSE,
       TRUE
FROM organization o
ON CONFLICT (org_id, name) DO UPDATE SET requires_fm_approval = TRUE;

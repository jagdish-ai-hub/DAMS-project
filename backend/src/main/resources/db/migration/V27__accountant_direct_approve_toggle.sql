-- Org-level opt-in: when on, an Accountant can approve a SUBMITTED receipt directly
-- (skipping the Finance Manager) as long as it's not a claim, its business status isn't
-- "Credit", and every settlement line is cash-mode. Off by default — this is a carve-out
-- from the FM's "final approval on every entry" rule (AGENT.md), so an org must opt in.
--
-- Same shape as multi_branch_cashier_access (V3): one boolean on organization, Owner-editable
-- via PATCH /organization.
ALTER TABLE organization
    ADD COLUMN accountant_direct_approve_cash BOOLEAN NOT NULL DEFAULT FALSE;

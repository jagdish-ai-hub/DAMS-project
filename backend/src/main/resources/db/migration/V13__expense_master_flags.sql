-- V13: flags the expense-side masters need before the Expense Entry screen can be built
-- without hard-coding master names in application code (AGENT.md locked decision #3).
--
--   expense_mode.requires_bank / requires_ref
--       Same two flags settlement_mode already carries (V3). The expense form decides
--       whether to show the Bank field and the Transaction-ID field by asking the mode,
--       never by matching its name — so an Owner can rename or add a mode through masters
--       management and the form still behaves.
--
--   expense_business_status.triggers_claim
--       Marks the one status ("Transfer to Claim") that moves an expense onto a warranty /
--       AMC / goodwill claim instead of being paid as cash. The service checks this flag
--       (not the label) and, when set, requires the expense to sit on a job card whose
--       category is a claim category (receive_category.is_claim = true).
--
-- Columns are added with safe defaults, then the demo rows (V5) are back-filled by name.
-- A fresh database runs V5 before this migration, so the back-fill covers it too.

ALTER TABLE expense_mode
    ADD COLUMN requires_bank BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN requires_ref  BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE expense_business_status
    ADD COLUMN triggers_claim BOOLEAN NOT NULL DEFAULT FALSE;

-- Back-fill demo masters — mirrors settlement_mode: Cash needs neither, QR / UPI needs a
-- reference, Bank needs both.
UPDATE expense_mode SET requires_ref  = TRUE WHERE name = 'QR / UPI';
UPDATE expense_mode SET requires_bank = TRUE, requires_ref = TRUE WHERE name = 'Bank';

UPDATE expense_business_status SET triggers_claim = TRUE WHERE name = 'Transfer to Claim';

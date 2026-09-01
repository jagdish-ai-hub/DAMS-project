-- V16: mark which payment modes are physical cash, so the Cash-page drawer math can ask
-- the mode instead of matching its name (AGENT.md locked decision #3, "nothing hard-coded").
--
-- The drawer position is:
--   opening + cash-mode receipts + Cash In − cash-mode expenses − Cash Out
-- "cash-mode receipts / expenses" = settlement / expense lines whose mode has is_cash = true.
-- Everything else (QR / UPI, Bank, Card, Credit, advance-by-QR) never touches the drawer.
--
-- Columns are added with a safe default, then the demo rows (V5) are back-filled by name.
-- A fresh database runs V5 before this migration, so the back-fill covers it too.

ALTER TABLE settlement_mode ADD COLUMN is_cash BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE expense_mode    ADD COLUMN is_cash BOOLEAN NOT NULL DEFAULT FALSE;

-- Physical cash across the counter: a plain cash receipt and a cash advance both land in
-- the drawer. Adv-Cash counts; Adv-QR does not.
UPDATE settlement_mode SET is_cash = TRUE WHERE name IN ('Cash', 'Adv-Cash');

-- On the expense side only the plain Cash mode is a drawer withdrawal.
UPDATE expense_mode SET is_cash = TRUE WHERE name = 'Cash';

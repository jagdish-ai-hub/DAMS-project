-- rev 91: cash and bank receipts never go to the Finance Manager. Which settlement modes qualify is the
-- Owner's choice (Masters -> Settlement modes -> "No Finance step needed"), not a fixed list.
--
-- Existing rows start as the owner decided: Cash, QR / UPI and Bank are ticked (matched by name, any case),
-- and so is any mode flagged as cash; claim modes (AMC/WRNTY, CGW) and the retired ones stay unticked.
-- New organisations copy the flag from the template organisation (MasterProvisioningService).
ALTER TABLE settlement_mode ADD COLUMN skips_finance BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE settlement_mode
   SET skips_finance = TRUE
 WHERE is_cash OR LOWER(name) IN ('cash', 'qr / upi', 'bank');

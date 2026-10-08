-- rev 68: two optional details a cashier can record on a receipt — the customer's contact number
-- as given at this receipt, and the vehicle's chassis number. Both live on the job card (per
-- receipt) rather than on the customer / vehicle masters, so typing one never overwrites what is
-- saved there. Nullable, no backfill: existing receipts simply show "—".

ALTER TABLE job_card
    ADD COLUMN contact_phone VARCHAR(32),
    ADD COLUMN chassis_no    VARCHAR(40);

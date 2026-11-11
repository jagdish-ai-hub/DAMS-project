-- rev 93: a QR payment is tagged to the bank it was paid into.
-- QR / UPI and Adv-QR (settlement) and QR / UPI (expense) now require a bank in every organisation,
-- including the template org new orgs are copied from. Lines saved earlier without a bank are untouched.
UPDATE settlement_mode SET requires_bank = TRUE WHERE LOWER(name) IN ('qr / upi', 'adv-qr');
UPDATE expense_mode    SET requires_bank = TRUE WHERE LOWER(name) = 'qr / upi';

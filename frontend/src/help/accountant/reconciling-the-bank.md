# Reconciling the bank

Every UPI and bank receipt you record carries a UTR. Reconciliation matches the bank statement against those UTRs, so mis-postings and missing credits surface in days — not at month-end.

## Steps

1. Download the statement CSV from the bank portal.
2. Open **Reconcile** and press **Upload statement CSV** (needs `date,utr,amount` columns).
3. Each line shows a suggestion: **EXACT** (same UTR + amount) or **AMOUNT_DATE** (same amount within 2 days — check with eyes).
4. Press **Confirm** on correct matches, **Ignore** on bank charges and transfers.
5. Work the **unmatched** rows: money in bank with no receipt, or receipts with no money.

## Good to know

- Matching only explains money. It never moves it and never edits a document.
- One receipt matches one statement line — the rest stay unmatched for you to look at.

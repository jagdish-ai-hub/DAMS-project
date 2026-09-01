# Masters

Every dropdown your staff pick from — settlement modes, receipt and expense categories, banks, expense sub-categories — is filled from the lists on the **Masters** screen. Nothing is hard-coded, so you set these up to match how your dealership actually works.

A new organization starts with a sensible default set. Adjust from there.

## Edit a list

1. Open **Masters**.
2. Choose a list from the left (e.g. *Settlement modes*).
3. Click **＋ Add** for a new row, or **Edit** on a row to change it.
4. Set the **name** and, optionally, a **sort order** — the number that controls where it sits in the dropdown.

## Options that matter

- **Receipt categories** — tick **claim** for the categories the finance manager closes with a final settlement (Warranty, AMC, CG). This is what makes a job card behave as a claim.
- **Settlement modes** — tick **requires a bank name** and/or **requires a transaction reference** to make those fields mandatory on a settlement line for that mode.
- **Expense sub-categories** — pick the parent **category** at the top, then add rows under it. Set a **per-line limit** if you want DAMS to *flag* (not block) any line above it — flagged lines need finance approval before they can be closed.

## Removing a row

You can't delete a master row — set it to **Inactive** instead. It stops appearing in new dropdowns but stays valid on every record that already uses it, so history never breaks.

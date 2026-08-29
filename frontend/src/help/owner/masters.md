# Editing the master lists

Every dropdown in DAMS — settlement modes, receipt and expense categories,
banks, and so on — is filled from the lists on the **Masters** screen. Nothing
is hard-coded, so you can tailor them to how your dealership works.

## Edit a list

1. Open **Masters**.
2. Pick a list from the left (e.g. *Settlement modes*).
3. Click **+ Add** to add a row, or **Edit** on a row to change it.
4. Set the **name** and, optionally, a **sort order** (controls the order the
   dropdown shows).

## List-specific options

- **Receipt categories** — tick **claim** for categories the Finance Manager
  closes with a final settlement (Warranty, AMC, CG).
- **Settlement modes** — tick **requires a bank name** and/or **requires a
  transaction reference** to make those fields mandatory on a settlement line.
- **Expense sub-categories** — choose the parent **department** at the top,
  then add rows. Set a **per-line limit** if you want DAMS to flag (not block)
  entries above it.

## Removing a row

You cannot delete a row — set it to **Inactive** instead. It disappears from
new dropdowns but stays valid on the records that already use it.

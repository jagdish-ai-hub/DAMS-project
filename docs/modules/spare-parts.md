# Spare Parts module (`inv_*`)

Local spare parts that are **not** in the OEM (Eicher) stock: parts left over from part
splitting, accidental services and stock differences. Source of the requirement:
`Ooriba_Inventory_Reconciliation_BRD_v1.3` plus the owner's Excel `Spare parts.xlsx`.
Platform rules for modules are in AGENT.md "Modules and module roles".

**Scope of this first drop** (what the client described, smaller than the BRD):
upload their Excel or add a part by hand → everyone searches all branches → bill a part
to a job card → request a part from another branch → the other branch marks Sent (stock
goes down there) → the requester marks Received (stock goes up). An Owner dashboard shows
what is happening across all branches. **Not in this drop:** physical audit and
discrepancy approval, wrong-part-ID investigation, high-value purchase approval (the
client mentioned it; it is not in the BRD — decide before building), vendor ordering.

## Switch, roles, access

| Thing | Rule |
|---|---|
| Switch | `organization.spare_parts_enabled`, default false. Super Admin sets it in Organizations. Off = every `/spare-parts/**` call returns 403 "not enabled". |
| Roles | `INVENTORY_MANAGER`, `STORE_KEEPER` (primary roles, branches via `user_branch_access`) — or an Owner-granted extra role. |
| See | Owner, Inventory Manager, Store Keeper: stock of **all** branches. Nobody else. |
| Act | Only at the caller's own branch(es). While switched, only the one branch picked. The Owner not switched = view only. |
| Permissions | Owner-editable grid per role (`inv_role_permission`); defaults below. Server-enforced. |

Permission grid (defaults — a missing row means the default):

| Permission | Store Keeper | Inventory Manager |
|---|---|---|
| `BILL_PART` bill a part to a job card | yes | yes |
| `RAISE_REQUEST` raise a branch request | yes | yes |
| `SEND_RECEIVE` mark Sent / Received / Decline | yes | yes |
| `UPLOAD_ADD` upload Excel / add a part | no | yes |
| `CORRECT_QTY` correct a quantity | no | yes |
| `EXPORT` export to Excel | no | yes |

Fixed (not in the grid): searching all branches (both roles), assigning the roles
(Owner), the Owner dashboard (Owner).

## Tables

| Table | Holds |
|---|---|
| `inv_part` | Part master per org: `part_code` (upper-case, no spaces, unique per org), description, unit price (org-wide; the last upload that carries a price sets it). |
| `inv_stock` | One row per branch + part: bin, `quantity` (running total, never negative). |
| `inv_movement` | **The ledger.** Append-only, signed `qty_change`, `qty_after`, type, reference (job card / request), user, acting role. Never updated or deleted. |
| `inv_import` | One row per confirmed upload: file name, mode (ADD / SET), row counts, user. |
| `inv_transfer` | A branch request: requesting branch, supplying branch, part, quantities, status, who/when for each step. |
| `inv_role_permission` | The Owner-edited permission grid, per org + role. |

Movement types: `UPLOAD_ADD`, `UPLOAD_SET`, `MANUAL_ADD`, `CORRECTION`, `BILLED`,
`TRANSFER_OUT`, `TRANSFER_IN`.

## Rules (and why)

- **Stock changes only through the ledger.** `inv_stock.quantity` and the sum of
  `inv_movement.qty_change` for that branch+part must always agree — a test proves it.
- **Whole numbers only.** Quantities are integers; a fractional quantity in a file is an
  error row (the owner's sheet is all whole numbers).
- **Upload asks every time:** *Add to stock* adds the file's quantity; *Set stock to the
  file* sets the quantity of the parts **in the file** and writes one ledger row with the
  difference. Parts not in the file are never touched. Nothing is posted until the user
  confirms the preview. Which quantity is read: `CLOSING QTY` if the sheet has it,
  otherwise `OPENING QTY`. `BILLED QTY` / `JOB CARD` columns are kept in the ledger note
  only (the closing quantity already reflects them).
- **Branch codes come from DAMS's own branch list** for that org. A code in a file that
  is not a branch of the org is an error row, never guessed.
- **Billing a part:** quantity ≤ branch stock; the job card number is **typed text**
  (Eicher's 10-digit number; stored as text so Excel's `4.01E9` never creeps in). If the
  typed number equals the `dbm_id` of an existing DAMS job card **that is a claim**, the
  bill is refused (BRD LSP-08: local spares only on non-claim cases). No match = allowed.
- **Branch request:** `REQUESTED → SENT → RECEIVED`, or `DECLINED` (supplier, with a
  reason) / `CANCELLED` (requester, only while REQUESTED). A request never changes stock.
  **Sent** takes the quantity out of the supplying branch (it may send fewer than asked,
  never more than it holds); the part is then **in transit**. **Received** adds it to
  the requesting branch (creating the stock row if the branch had none).
- **Role grid is checked on the server** for every action, from the database each time.
- A branch with no stock row for a part simply has quantity 0 there.

## API (all under `/api/v1/spare-parts`, 403 when the module is off)

`GET /access` (any signed-in user — says whether the module is on and what the caller may
do; the shell uses it to show the menu) · `GET /stock?q=&branchId=` ·
`GET /branches` · `POST /imports/preview` (multipart: file) · `POST /imports` (confirm:
mode + rows) · `POST /parts` (add one part + opening quantity) ·
`POST /stock/correct` · `POST /bills` · `GET /movements?...` · `GET /transfers` ·
`POST /transfers` · `POST /transfers/{id}/send|receive|decline|cancel` ·
`GET /dashboard` (Owner, Inventory Manager) · `GET|PUT /permissions` (Owner) ·
`GET /export/stock` (EXPORT permission). Super Admin: `PATCH /admin/organizations/{id}`
now also accepts `sparePartsEnabled`.

## Still open (ask the client)

1. Full parts list — the sample file has 23 parts in 3 branches; the BRD says 5 branches.
2. High-value purchase approval: buying from a local vendor (existing DAMS expense
   approval already covers over-limit) or ordering from Eicher (out of BRD scope)?
3. Does Sent need a proof reference, and should stock stay "in transit" visible to the
   requester? (Built: yes, shown as in transit.)

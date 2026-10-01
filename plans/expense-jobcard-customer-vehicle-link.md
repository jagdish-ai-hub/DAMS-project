# Link Expense ↔ Job Card ↔ Customer ↔ Vehicle (searchable, branch-scoped)

## Context
Today an Expense document can point at a job card (`expense_document.job_card_id`), but the UI hides it:
- The "Ooriba ID" dropdown on [NewExpensePage.tsx](frontend/src/cashier/NewExpensePage.tsx) (L734-747) is empty unless you arrive from a customer page (`?customerId=`).
- Customer Name / Vehicle # / DBM on the same form are plain text (V30 columns), not linked to anything, so an expense's customer can silently differ from its job card's customer.
- There is no job-card list/search API at all (JobCardController: create/get/patch/close-claim only). `GET /customers?q=` and vehicle lookup are org-wide, not branch-scoped. The Receipt form's Customer/Vehicle boxes are plain text too.

Goal (confirmed with user): one shared, branch-scoped search on **Expense, Receipt and Job-Card create** so that job card ⇄ customer ⇄ vehicle are picked, not retyped; a customer has many vehicles; typing something new creates it (deduped) on save.

Decisions confirmed: **every one of the three screens can either LINK to an existing customer / vehicle / job card or CREATE a new one** (Expense included: it can create a new job card too, which is an AGENT.md change: job cards no longer start only from a Receive); Expense form = **both** customer/vehicle typeahead **and** a job-card search box (each narrows/fills the others); **create both** customer and vehicle from Expense; scope = **existing BranchScope rule** (Cashier home branch, or all if org multi-branch toggle ON; Accountant assigned branches; Owner/FM all); apply on **all three screens**.

## Step 0 — Save plan + spec first (CLAUDE.md working process)
1. Copy this plan to `plans/expense-jobcard-customer-vehicle-link.md` (repo root `plans/`, next to `multi-role-switch.md`).
2. **AGENT.md**: revise the "job card starts from a Receive" wording (Expense may also create one); clause under Entities/ID scheme: an expense/receipt may carry `customer_id` + `vehicle_id` (+ optional `job_card_id`); vehicle belongs to exactly one customer; pickers are branch-scoped by `BranchScope`; update API table (L505-507) with `GET /job-cards`, `GET /customers?scoped`, `GET /customers/{id}/vehicles`.
3. **plan.md**: rev 56 entry, V34 in the migration table, endpoints list (near L1668).

## Customerless job cards (user decision)
An Expense can create a job card **without a customer**; a later **Receipt attaches the customer** (link existing or create new).
- `job_card.customer_id` becomes **nullable** (today `NOT NULL`, V8). Vehicle master rule unchanged (each vehicle has exactly one customer), so a customerless job card keeps the typed vehicle no. as **text** (`job_card.vehicle_no_text`, searchable) with `vehicle_id` null.
- **Attach rule: set once, then locked.** New `POST /job-cards/{id}/attach-customer {customerId | newCustomerName+phone}` (or the same fields on the Receipt create when it targets that job card). Only a CASHIER of the job card's branch, only while `customer_id` is null; in one transaction it resolves/creates the customer, then creates or matches the Vehicle from `vehicle_no_text` under that customer (409 if that number belongs to a different customer) and sets `vehicle_id`. Changing it afterwards = Owner/FM only, audited (`JOB_CARD_CUSTOMER_ATTACHED` / `_CHANGED` in `EventType`).
- The Expense form's job-card create needs no customer; if a customer is also picked/typed it is set immediately.
- **Impact sweep (fix the rule, not the screen):** these places read `JobCard.getCustomerId()` and must tolerate null: `JobCardService`, `ReceiveDocumentService`, `ExpenseDocumentService`, `SearchService`, `ReviewService`, `MyEntriesService`, `DashboardService`, `ExportService`, `DrawerService`, `AiOpsService`, plus `CustomerService.history/expenses`. Display fallback = "— no customer yet —"; customer history/totals simply exclude customerless cards; review/queue screens show the badge "Customer not linked" so the accountant can chase it. Receipts against a customerless job card are blocked until a customer is attached (receipt requires a customer today).

## Data model — `V34__expense_customer_vehicle_link.sql` (CI/CD applies Flyway; not run locally)
- `job_card`: `customer_id` DROP NOT NULL; add `vehicle_no_text VARCHAR(20)` (+ index `(org_id, vehicle_no_text)`).
- `expense_document`: add `customer_id BIGINT REFERENCES customer(id)`, `vehicle_id BIGINT REFERENCES vehicle(id)` (both nullable; keep V30 text columns as display snapshot/back-compat). Backfill from `job_card` where `job_card_id` is set.
- `customer`: add `created_branch_id BIGINT REFERENCES branch(id)`. Backfill = branch of the customer's earliest job card. **Why:** customers are org-wide and only reachable via job cards; a customer created from Expense with no job card would otherwise be invisible to branch-scoped search.
- Indexes: `customer(org_id, lower(name))`, `job_card(org_id, branch_id)` if missing, `expense_document(org_id, customer_id)`.

## Backend (Spring Boot; reuse existing pieces)
- **Reuse**: `BranchScope.allowedBranchIds()` ([BranchScope.java](backend/src/main/java/com/dams/common/security/BranchScope.java)), `Vehicle.normalise()`, `VehicleService.createOrGet` ([VehicleService.java](backend/src/main/java/com/dams/vehicle/service/VehicleService.java)), `JobCardService.resolveCustomer/resolveVehicle` (L252-288), `JobCardResponse`, `CustomerService.vehiclesFor`.
- **New `GET /api/v1/job-cards?q=&customerId=&vehicleId=&limit=`** (JobCardController + `JobCardService.search`): new `JobCardRepository` query joining customer + vehicle, matching customer name/phone, vehicle no (normalised), dbm_id, invoice_no, internal id / `{branchCode}-JC-{id}`; filtered by `allowedBranchIds` inside the query; newest first, limit ~20. Returns JobCardResponse (already has customerName, vehicleNo, reference, branch).
- **`CustomerService.search(q)`**: add branch scoping (customer visible if it has a job card in an allowed branch OR `created_branch_id` in allowed set; Owner/FM unrestricted). Keeps `PICKER_LIMIT`. Universal search keeps its current behaviour.
- **New `GET /api/v1/customers/{id}/vehicles?q=`**: that customer's vehicles, contains-match on normalised number (drives the vehicle dropdown "existing shows as you type").
- **Expense creates a job card**: `CreateExpenseRequest` gains optional `newJobCard { categoryId (receive category), businessStatusId (job-card status), claimTypeId? }`. `ExpenseDocumentService.create` calls the existing `JobCardService` create logic (customer + vehicle resolved as below, branch = cashier's home branch via `ExpensePostingGuard`, DBM/invoice from the expense form) in the same transaction, then sets `job_card_id`. Cashier-only, as expense posting already is; same job-card audit event as today. Customer is optional (see Customerless job cards).
- **Create path** (Expense/Receive create + PATCH DTOs get `customerId`, `vehicleId`, and inline `newCustomerName`, `newVehicleNo`): in one transaction resolve customer (existing id, else create with `created_branch_id` = posting branch) then vehicle (existing, else `createOrGet` under that customer). If typed vehicle no. already belongs to a **different** customer → 409 "Vehicle X is already registered to <customer>" (name shown only if in caller's scope) instead of today's silent reuse. If `jobCardId` also sent, its customer/vehicle must match (else 400) — one rule, enforced in `ExpenseDocumentService.create/patch`, receive service, and JobCardService.
- Fill the V30 text columns from the resolved records so exports/history keep working; `ExpenseDocumentResponse` returns `customerId`/`vehicleId` from the doc first, job card second.
- Audit: existing `CREATED` event detail gains `customerId`, `vehicleId`.
- Security: same `@PreAuthorize` sets as CustomerController; org id from `TenantContext` only.

## Frontend (match inline-style patterns of existing forms; stack unchanged)
- **New shared components** in `frontend/src/shared/`: `CustomerCombobox` (debounced `/customers?q=`), `VehicleCombobox` (needs a customer; lists that customer's vehicles, filters as you type, shows "＋ Add new vehicle 'OD05…'" row when nothing matches), `JobCardSearch` (calls `/job-cards?q=`, shows `reference · customer · vehicle · DBM`). Model on the dropdown styling in [GlobalSearch.tsx](frontend/src/shared/GlobalSearch.tsx). Vehicle number normalised client-side the same way as the server.
- Linking behaviour: picking a job card fills customer + vehicle + DBM; picking a customer clears an inconsistent vehicle/job card and narrows the job-card search to that customer; picking a vehicle fixes the customer.
- **Expense "＋ New job card"**: choosing it under the job-card search reveals *Job-card category* (`receive-categories`) and *Job-card status* (`BusinessStatusSelect`), since the Expense form's own Status is the expense status, not the job card's. Works with no customer; a small hint says "customer can be linked later from a Receipt".
- **[NewExpensePage.tsx](frontend/src/cashier/NewExpensePage.tsx)**: replace Customer Name / Vehicle # / "Ooriba ID" select (L722-747) with the three components; "— none (branch overhead) —" stays as clearing the job card. Keep `?customerId=`/`?jobCardId=` prefill and `?editDoc=` load (send/receive `customerId`, `vehicleId`).
- **[NewReceiptPage.tsx](frontend/src/cashier/NewReceiptPage.tsx)** (L560-574) and job-card create: same components; existing customer pick sets `customerId`, new typed name stays inline-create as today.
- `frontend/src/api/{expenses,receipts,jobCards,customers,vehicles}.ts`: new calls + request fields.

## Tests
- Backend: customerless job card create from Expense, attach-customer (set once; second attempt rejected; wrong branch rejected; vehicle conflict 409), null-customer safety in each service listed above; `JobCardService.search` branch scoping per role (cashier home, cashier toggle ON, accountant assigned, owner) and customer/vehicle match; `CustomerService.search` scoping incl. `created_branch_id`; expense create with new customer+vehicle, with vehicle owned by another customer (409), with mismatched jobCardId (400). Follow existing `*ControllerSecurityTest` style.
- Frontend (vitest): combobox filtering, "add new vehicle" row, job card pick fills the fields.

## Verification (per CLAUDE.md — check the layer the user sees)
1. Backend tests via bundled Maven (see memory: build-and-run-commands).
2. `npm run build` in `frontend/`, then grep `dist/assets/*.js` for the new placeholder strings/endpoints (`/job-cards?`, "Add new vehicle") and confirm no leftover `Ooriba ID` select.
3. Neon MCP `run_sql` after CI applies V34: confirm columns exist and backfill counts; after a test expense, query the actual `expense_document` row for `customer_id`/`vehicle_id`/`job_card_id`.
4. Trace the full user path in the browser: cashier at OOR types a customer → sees only OOR-reachable customers → picks vehicle → job cards narrow → saves; then types a brand-new vehicle no. and confirms it appears in that customer's vehicle list next time.

## Open risk to note
Branch-scoped customer search means a customer known only at another branch is not found by a restricted cashier; the 409 on a duplicate vehicle number is the safety net against duplicate customers.

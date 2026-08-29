# DAMS — Build Plan

> Source of truth alongside AGENT.md. All decisions here are locked unless
> AGENT.md is updated first.

---

## Revision log

- **rev 9 (2026-08-30)** — Stage 4 (Receive document) built + verified on Neon:
  - **B2B / GST restored.** The Receive Entry mockup's B2C/B2B toggle + mandatory GST# was
    dropped in the Stage 4 plan as "mockup embellishment" — that was wrong, it's a real
    client requirement. `job_card` gains `is_b2b` / `gst_no` (V10, a new migration — V8 is
    untouched); `gst_no` is required when `is_b2b` (enforced in `JobCardService`, both create
    and PATCH, so the message names the field).
  - **`POST /api/v1/receipts` is one combined endpoint** — `jobCardId` OR inline job-card
    fields, + `lines[]`, + optional `submit`. It has **no branch field**; the document always
    posts under the job card's branch (a cashier's is forced to their home branch).
  - **Cross-branch write block.** Every receive-document write requires `role == CASHIER` and
    `jobCard.branchId == cashier.homeBranchId` — otherwise 409 naming both branches. Holds
    even with `multi_branch_cashier_access` ON (that only widens search/visibility — the
    routing question stays TBD in AGENT.md). `ReceivePaymentGuard` owns this + the
    `canRecordPayment` UI hint on `JobCardResponse` / history / (search shows real
    `totalOutstanding`).
  - **Doc numbering** — `{branchCode}-{MONKEY}-R-{seq:03d}` (e.g. `OOR-AUG26-R-001`), assigned
    **on submit only** (drafts stay unnumbered — the mockup's on-save numbering was a
    prototype shortcut). Gap-free via `INSERT … ON CONFLICT … DO UPDATE … RETURNING` inside
    the submit txn. Line ids `{docNo}-L{n}` stamped at submit / on Add Payment to a numbered
    doc; never reused (deleted lines are not renumbered).
  - **"Date of Entry" header field dropped** — superseded by per-line `transaction_date`;
    `created_at` covers "when entered".
  - Receive category seed already matched the mockup's 11 values (V5) — no migration needed.
  - **Pending / settle.** `PendingAmountCalculator` is the one implementation: `invoice − Σ
    lines across the job card's non-REJECTED receive docs`, 0 when no invoice, 0 when a
    `ClaimClose` exists (incl. shortfall). At pending 0 (invoice present, no claim) the open
    doc auto-settles with a SYSTEM `SETTLED` audit event and its attachments freeze. The
    claim-close-forces-settled path stays Stage 8; `JobCardService.hasClaimClose` is now real.
  - **Attachments.** `StorageService` interface + `LocalFilesystemStorageService` (default;
    real disk store, HMAC-signed 10-min URL served from `/api/v1/attachments/raw`, permit-all
    — the signature is the auth). **R2 impl not built** — `dams.storage.provider=r2` is wired
    but `R2StorageService` + the S3 SDK dependency are deferred (the offline build here can't
    fetch the jar); the interface seam is in place so it's one new class.
  - **My Entries** — `GET /api/v1/my-entries`, the caller's own receive docs newest-first with
    `today` / `queried` flags. Seed includes a QUERIED doc (Suresh, R-013) so fix-and-resubmit
    is demoable without Stage 7.
  - Frontend: New Receipt page (`/new-receipt`), Add Payment modal, View Receipts modal
    (button, not thumbnails), My Entries page (`/my-entries`); Cashier home wired
    (quick-actions row + real Add Payment / View Receipts / history figures).
  - `pom.xml` now pins `project.build.sourceEncoding` / `maven.compiler.encoding` to UTF-8 —
    the bundled Maven here otherwise mangled non-ASCII in message strings.
- **rev 8 (2026-08-29)** — small decisions made while building Stage 3:
  - Job-card screen reference is `{branchCode}-JC-{id}` (e.g. `OOR-JC-5`), derived at read
    time — no stored job-card number (user-confirmed). `dbm_id` stays a separate nullable
    manual field.
  - `audit_event.detail` is `TEXT` holding a JSON string, not `jsonb` — keeps Flyway +
    Hibernate `ddl-auto: validate` friction-free on Neon. Nothing queries into it yet;
    a later migration can switch to `jsonb`.
  - Universal search: name / phone / vehicle matches are org-wide (customers and vehicles
    aren't branch-scoped); only the job-card / invoice / dbm dimension and each customer's
    job-card roll-up are limited by `BranchScope`. The mockup's "outstanding" figure shows
    `totalInvoiced` until Stage 4 adds settlement lines.
  - `hasClaimClose(jobCardId)` in `JobCardService` is a stub returning `false` (the
    `claim_close` table lands in Stage 8) — the PATCH 409 guard for `category_id` /
    `business_status_id` is wired but currently unreachable.
- **rev 7 (2026-08-29)** — Super Admin scope + self-service password (owner request,
  during Stage 2):
  - Super Admin seed is now `dams@jjsoftware.com` / `Welcome12345@` (V1), meant to be
    changed on first login.
  - `POST /api/v1/auth/change-password` — any signed-in user changes their own password
    (current + new). Frontend: a Settings screen.
  - `DELETE /api/v1/admin/organizations/{id}` — Super Admin permanently deletes a
    dealership and all its data (branches, users, masters, receivers), FK-safe order,
    in `com.dams.admin` (`OrganizationPurgeService`). Stage 4+ will refuse it once the
    org has transactional documents. General rule stays deactivate-never-delete; this is
    the one exception, for mis-onboarded orgs.
- **rev 6 (2026-08-29)** — dependency refresh (Stage 1 built, then bumped to
  latest **within the locked majors** — React 18, Vite 5, Tailwind 3, TS 5,
  Spring Boot 3.x all held; no AGENT.md/plan.md stack change):
  - Backend: Spring Boot `3.5.16`, jjwt `0.13.0`, lombok `1.18.46`,
    springdoc-openapi `2.9.0`. Java 21. `mvn test` green.
  - Frontend: react-router-dom `7`, recharts `3`, react-markdown `10`,
    remark-gfm `4`, tailwind-merge `3`, cva `0.7.1`, vitest `3`, jsdom `26`,
    `@testing-library/*` latest (jest-dom pinned `6.9.1`), axios `1.20`,
    lucide-react `1`. tsc + eslint + vitest + build green.
  - `@MockBean` in backend tests is deprecated on Boot 3.5 (removed in 4.0) —
    migrate to `@MockitoBean` when convenient.
  - **Flyway renumber:** seed data was numbered as one final migration (V11),
    but it references tables built in later stages. Fixed to schema-then-seed,
    sequential, per stage (V2–V5 = Stage 2, …, V15 = Stage 6). See the Flyway
    Migration Plan table.
- **rev 5 (2026-08-29)** — ClaimClose now drives Pending Amount and
  `settled`:
  1. **`pending_amount` reports 0 once a `ClaimClose` exists** — the claim
     is settled at `final_amount`; the raw `invoice − Σlines` shortfall
     must never surface as a phantom balance (prototype bug — do not
     reintroduce). API adds `settled_via_claim_close` + `claim_final_amount`.
  2. **close-claim is one transaction that also flips `settled = true`** on
     every still-open ReceiveDocument of that job card — a shortfall-
     accepted claim never reaches `pending = 0` exactly, so the doc would
     otherwise stay "open" forever.
- **rev 4 (2026-08-29)** — locked the rev-2 open items and added two
  claim-close guards:
  1. rev-2 open items #1–5 are **locked**: cash model rework (yes),
     attachments as a `0..n` table (yes), `last_modified_by` (yes),
     job-card `category_id`/`business_status_id` stay mutable via PATCH
     (yes), Vitest + RTL (yes). "Open items" section removed.
  2. **Claim-close freezes the case.** Once a `ClaimClose` row exists for
     a job card, `PATCH /job-cards/{id}` rejects any change to
     `category_id` / `business_status_id` (409). While still mutable
     (no `ClaimClose` yet), every `category_id` change writes an
     `AuditEvent` with before/after values (`is_claim` derives from
     category).
  3. **No new document on a closed claim.** `POST /api/v1/receipts`
     checks the target job card for a `ClaimClose` first; if one exists
     it refuses (409). A closed claim cannot get a new ReceiveDocument
     opened against it later.
- **rev 3 (2026-08-29)** — test-phase decisions + help system:
  1. **DB: Neon only.** No containerised Postgres during the test phase.
     One shared Neon instance for dev + integration + client review.
     AGENT.md "Local development & testing" updated to match.
  2. **Sessions: access token only, no refresh.** Short-lived access JWT
     (~8h) in `sessionStorage`, no `/auth/refresh`, no "remember me", no
     persistence across browser restart. Persistent login is its own
     hardening stage **after** client sign-off. AGENT.md auth section
     updated.
  3. **Seeded test data.** Flyway seeds Super Admin + one full dummy
     dealership (org, branches, one login per role) so testers can switch
     roles freely. The invite flow still exists and is the only path for
     real orgs; a production seed profile omits the dummy data.
  4. **In-app Help Center.** Role-scoped, step-by-step help articles
     bundled with the frontend (Markdown, no backend). New Stage 11; per-
     role stubs written during each role's stage. AGENT.md UI-reference
     section updated.
- **rev 2 (2026-08-29)** — applied owner corrections before Stage 1:
  1. BIGINT confirmed (added the "no raw ID is ever shown to a user" reason).
  2. Email: `EmailService` interface now, log/echo-the-link impl for Stage 1.
  3. **Structural:** `category_id`, `business_status_id`, `invoice_no`,
     `invoice_amount` moved from `ReceiveDocument` → `JobCard`. Pending
     Amount is now job-card-wide. `ClaimClose` keyed by `job_card_id`.
  4. **Structural:** `User.home_branch_id` added (Cashier posts here, always).
     `UserBranchAccess` join table is ACCOUNTANT-only. The
     `multi_branch_cashier_access` toggle changes search/visibility scope
     only, never the posting branch.
  Plus plan-review findings folded in (cash model, attachments,
  `last_modified_by`, …) — all locked in rev 4, see **Resolved** near the end.

---

## Locked Decisions

| Decision | Value |
|---|---|
| Surrogate key type | `BIGINT` auto-increment everywhere, no UUIDs. Safe because **no screen ever exposes a raw internal ID** — every document is shown by its `document_no` (`OOR-JUL26-R-021`), never its `id`. Sequential-ID guessing exposes nothing; nothing sensitive is on either representation. |
| Email / invite flow | `EmailService` interface defined in Stage 1. Stage 1 impl (`LoggingEmailService`) writes the invite link to the log and returns it in the API response so Super Admin can copy-send it manually. `invite_token` / `invite_expires_at` and the full accept-invite flow are built now. A real provider = one new impl class, no other change. |
| Case-level fields live on JobCard | `category_id`, `business_status_id`, `invoice_no`, `invoice_amount` are on **JobCard**, not `ReceiveDocument`. One invoice per job; a job card can own several `ReceiveDocument`s over its life (a claim closes, months later new money arrives → new document, same job card). `invoice_no` sits next to `dbm_id` — same kind of external reference. |
| Pending Amount | `JobCard.invoice_amount − Σ(settlement-line amount) across ALL ReceiveDocuments under that job card`. Returns **0** when `invoice_amount` is null (advance taken, no invoice raised yet). **Returns 0 when a `ClaimClose` row exists** — the claim is settled at `ClaimClose.final_amount`, regardless of any invoice/settlement gap; the raw `invoice − Σlines` shortfall must never surface as a phantom balance (this bug was hit and fixed once in the HTML prototype). API also exposes `settled_via_claim_close` + `claim_final_amount` so the UI can show "Settled via claim close, final ₹X". Never computed per-document. |
| Claim closing | `ClaimClose` is keyed by `job_card_id` — you close the **case**, not one document in its history. Exactly one `ClaimClose` per job card, immutable once written. |
| Claim-close freezes the case | Once a `ClaimClose` row exists for a job card: (a) `PATCH /job-cards/{id}` rejects any change to `category_id` / `business_status_id` with 409; (b) `POST /api/v1/receipts` against that job card is refused with 409 — no new ReceiveDocument can be opened on a closed claim; (c) `pending_amount` reports 0 (see Pending Amount); (d) the close-claim transaction sets `settled = true` on every ReceiveDocument of that job card that is still `settled = false`, because a shortfall-accepted claim never reaches `pending = 0` exactly and the doc would otherwise stay "open" forever. |
| Job-card category changes are audited | While a job card is still mutable (no `ClaimClose` yet), every `category_id` change writes an `AuditEvent` (`event_type = CATEGORY_CHANGED`, before/after in `detail`) — because `is_claim` is derived from `category_id` and this is a money-classification change. |
| Receipt open vs settled | `ReceiveDocument.settled` — a computed boolean, flips `true` in two ways: (1) automatically when the owning job card's Pending Amount reaches 0; (2) forced `true` by the close-claim transaction on every still-open ReceiveDocument of that job card (a shortfall-accepted claim never hits `pending = 0` exactly). It is **independent of** `workflow_status` and of `JobCard.business_status`; the "two status columns are never derived from each other" rule is untouched — this is a third, explicitly-computed flag (named `settled`, not `closed`, to avoid clashing with the "Close" business-status value). |
| One open receive document per job card | Partial unique index `(org_id, job_card_id) WHERE settled = false`. "Add Payment" appends a settlement line to that one open document; it can never create a second document for the same job card. |
| Cashier posting branch | `User.home_branch_id` — exactly one, mandatory for every CASHIER, fixed. **Every** document a cashier creates posts under this branch, with no exception and regardless of any toggle. |
| `UserBranchAccess` join table | ACCOUNTANT only. FM/Owner are always org-wide. Cashier uses `home_branch_id`, not this table. |
| `multi_branch_cashier_access` toggle | Org-level, default OFF. Changes **only** what a cashier can search / see (own branch vs org-wide customers & job cards). Never changes which branch their own documents post under. |
| Maker-checker identity | Every document carries `created_by` **and** `last_modified_by`. A user may not verify or approve a document on which they are either. |
| Attachments | Separate `attachment` table, polymorphic `(parent_type, parent_id)`, **0..n** per settlement line / expense line / parent document. `frozen = true` once the parent is Approved or Closed — no replace, no delete. Served only via short-lived signed URLs. |
| Business status vs workflow status | Two separate columns, never derived from each other. (`business_status` now lives on `JobCard` for the receive side, on `ExpenseDocument` for the expense side.) |
| Transfer to Claim | Status change on the expense document only, no new document. |
| Invoice numbers | External reference, nullable, manual entry, never DAMS-generated. Lives on `JobCard`. |
| Receiver entity | Full master entity (`id`, `org_id`, `name`, `phone`) — not a text field. |
| Job card creation | One continuous inline flow from the receipt form. |
| Tally export | Not in v1. |
| Post-approval reversal | Deferred beyond v1. |
| Universal search | All roles, scoped by branch access (and the cashier toggle above). |
| Cashier My Entries | Required v1 — queried-item highlight + fix-and-resubmit. |
| "Today" boundary | All date-sensitive logic (drawer position, day-close, My Entries "today") uses a fixed org timezone: **Asia/Kolkata**. |
| Database | **Neon (hosted Postgres) only** during the test phase — one shared instance for local dev, integration testing, and client review. No containerised Postgres. `docker-compose.yml` runs backend + frontend only. Revisit after client sign-off. |
| Sessions (v1) | **Access token only, no refresh.** Short-lived access JWT (~8h) in `sessionStorage`; on expiry, log in again. No `POST /auth/refresh`, no refresh-token rotation, no "remember me", no persistence across browser restart. All deferred to the post-review hardening stage. Shell shows an account menu (name, role, branch, Logout). |
| Seeded test data | Flyway seeds Super Admin **plus one full dummy dealership** — org, 3 branches, one user per role (Owner, FM, Accountant, Cashier) with known passwords — so anyone can log in and exercise every role. The invite flow is still built and is the only path for real orgs; a `prod` seed profile creates masters only, no orgs/users. |
| Help Center | Role-scoped, step-by-step help articles (Cashier, Accountant, FM, Owner, Super Admin) authored as **bundled Markdown in the frontend**, versioned with releases — no `help_article` table, no API. Help button in every shell; contextual "?" on individual screens deep-links to the relevant article. |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend language | Java 21 |
| Backend framework | Spring Boot 3.x, Maven |
| Backend modules | Spring Web, Spring Data JPA, Spring Security (JWT stateless), Bean Validation, Flyway |
| Database | PostgreSQL 17 — Neon serverless (Singapore). Single shared instance for every environment during the test phase; no local Postgres container |
| Frontend | React 18 + Vite + TypeScript |
| CSS | Tailwind CSS |
| UI components | shadcn/ui (+ `class-variance-authority`, `clsx`, `tailwind-merge`, Radix primitives) |
| Icons | lucide-react |
| Charts | recharts |
| Routing | react-router |
| HTTP client | axios |
| Frontend tests | Vitest + React Testing Library |
| In-app help | Bundled Markdown rendered with `react-markdown` + `remark-gfm` (upgrade to MDX only if help needs interactive elements) |
| File storage | Cloudflare R2 via S3-compatible SDK, behind `StorageService` interface (local: no-op impl) |
| API docs | springdoc-openapi (auto-generated) |
| API base path | `/api/v1` |
| Containerisation | Docker (multi-stage builds) + docker-compose (backend + frontend only — DB is Neon) |
| CI/CD | GitHub Actions → GitHub Container Registry |

---

## Neon Database

| Field | Value |
|---|---|
| Project name | dams |
| Project ID | `red-smoke-06867853` |
| Region | `aws-ap-southeast-1` (Singapore) |
| Database | `dams` |
| Branch | `main` (`br-gentle-bar-azwnpopw`) |
| PG version | 17 |
| Connection string | Set as `SPRING_DATASOURCE_URL` env var — never commit to source control. Get from Neon console. Used by **every** profile during the test phase. Optionally use a separate Neon branch for `local` vs `prod` to isolate throwaway test data. |

---

## Entity List

### Platform-level (no `org_id`)

**Organization**
`id` BIGINT PK, `name`, `multi_branch_cashier_access` BOOL DEFAULT false, `active` BOOL, `created_at`

**User** (`app_user` in DB — `user` is reserved in Postgres)
`id` BIGINT PK, `org_id` BIGINT nullable (null = SUPER_ADMIN), `home_branch_id` BIGINT nullable (**NOT NULL in practice for CASHIER**, null for every other role — the branch every document this cashier creates posts under), `name`, `email` UNIQUE, `password_hash` nullable (null until invite accepted), `role` ENUM(SUPER_ADMIN / OWNER / FINANCE_MANAGER / ACCOUNTANT / CASHIER), `active` BOOL, `invite_token` nullable, `invite_expires_at` nullable, `created_at`

**UserBranchAccess** *(join table — ACCOUNTANT only)*
`user_id` BIGINT, `branch_id` BIGINT — PK `(user_id, branch_id)`

---

### Org-level master tables (all carry `org_id`)

**Branch**
`id`, `org_id`, `name`, `code` (2–5 chars, unique per org), `active`, `created_at`

**Customer**
`id`, `org_id`, `name`, `phone` nullable, `created_at`

**Vehicle**
`id`, `org_id`, `customer_id`, `vehicle_no` (normalized: uppercase, no spaces), `created_at`
Unique: `(org_id, vehicle_no)`

**JobCard** *(the case — anchors a vehicle/customer to all their documents over time)*
`id`, `org_id`, `branch_id`, `customer_id`, `vehicle_id` nullable, `dbm_id` nullable *(Eicher external ref)*, `invoice_no` nullable *(external ref)*, `invoice_amount` NUMERIC(14,2) nullable, `category_id` → `receive_category`, `business_status_id` → `receive_business_status`, `created_at`
- `is_claim` is **not stored** — read via join to `receive_category.is_claim`, to avoid a denormalized flag drifting.
- `category_id` / `business_status_id` are editable after creation (e.g. Warranty → Credit) via `PATCH /job-cards/{id}` **until a `ClaimClose` row exists for this job card**; after that PATCH rejects those two fields with 409.
- Every `category_id` change (while still mutable) writes an `AuditEvent` with `event_type = CATEGORY_CHANGED` and before/after ids in `detail`.

**Receiver** *(vendor/payee master)*
`id`, `org_id`, `name`, `phone` nullable, `active`, `created_at`

---

### Configurable masters (Owner-editable, nothing hard-coded)

**ReceiveCategory** — `id`, `org_id`, `name`, `is_claim` BOOL, `active`, `sort_order`

**ReceiveBusinessStatus** — `id`, `org_id`, `name`, `active`, `sort_order`
*(seed values from the mockups: Hold / AMC / CG / WIP / Warranty / Credit / Close — "Close" here is a user-set business status, distinct from the computed `ReceiveDocument.settled` flag)*

**SettlementMode** — `id`, `org_id`, `name`, `requires_bank` BOOL, `requires_ref` BOOL, `active`, `sort_order`

**ExpenseCategory** — `id`, `org_id`, `name`, `active`, `sort_order`

**ExpenseSubCategory** — `id`, `org_id`, `expense_category_id`, `name`, `limit_amount` nullable, `active`, `sort_order`

**ExpenseMode** — `id`, `org_id`, `name`, `active`, `sort_order`

**ExpenseBusinessStatus** — `id`, `org_id`, `name`, `active`, `sort_order`
*(Open / In Progress / Awaiting Receipt / Received Receipt / Closed / Transfer to Claim)*

**Bank** — `id`, `org_id`, `name`, `active`, `sort_order`

---

### Transactional documents

**ReceiveDocument** *(the review / money-flow wrapper — not the case)*
`id`, `org_id`, `branch_id`, `job_card_id`, `document_no` (e.g. `OOR-JUL26-R-021`), `workflow_status` ENUM(DRAFT/SUBMITTED/VERIFIED/APPROVED/QUERIED/REJECTED), `settled` BOOL DEFAULT false *(computed — flips true when job-card Pending Amount hits 0)*, `created_by`, `last_modified_by`, `created_at`, `submitted_at`
Unique: `(org_id, document_no)`
Partial unique: `(org_id, job_card_id) WHERE settled = false` — at most one open receive document per job card.

**SettlementLine**
`id`, `org_id`, `receive_document_id`, `line_no`, `line_id` (stored, e.g. `OOR-JUL26-R-021-L1`), `transaction_date`, `settlement_mode_id`, `amount` NUMERIC(14,2), `original_amount` nullable, `bank_id` nullable, `transaction_ref` nullable, `remark` nullable, `overridden_by` nullable, `override_reason` nullable, `overridden_at` nullable, `created_by`, `created_at`

**ExpenseDocument**
`id`, `org_id`, `branch_id`, `job_card_id` nullable, `document_no`, `receiver_id`, `expense_category_id`, `date_of_inception`, `business_status_id`, `workflow_status` ENUM(DRAFT/SUBMITTED/VERIFIED/APPROVED/QUERIED/REJECTED/CLOSED), `over_limit` BOOL, `created_by`, `last_modified_by`, `created_at`, `submitted_at`
Unique: `(org_id, document_no)`

**ExpenseLine**
`id`, `org_id`, `expense_document_id`, `line_no`, `line_id`, `transaction_date`, `sub_category_id`, `amount` NUMERIC(14,2), `original_amount` nullable, `expense_mode_id`, `bank_id` nullable, `transaction_ref` nullable, `remark` nullable, `overridden_by` nullable, `override_reason` nullable, `overridden_at` nullable, `created_by`, `created_at`

**ClaimClose** *(immutable once written — FM's final override record, one per job card)*
`id`, `org_id`, `job_card_id`, `final_amount`, `overridden` BOOL, `override_reason` nullable, `closed_by`, `closed_at`
Unique: `(org_id, job_card_id)`
Its existence is a gate: `PATCH /job-cards/{id}` rejects `category_id` / `business_status_id` changes (409); `POST /api/v1/receipts` rejects opening a new document on this job card (409); `pending_amount` for the job card reports 0 (settled at `final_amount`). Writing the row is a single transaction that also flips `settled = true` on every still-open ReceiveDocument of the job card.

**Attachment**
`id`, `org_id`, `parent_type` ENUM(RECEIVE_DOCUMENT / SETTLEMENT_LINE / EXPENSE_DOCUMENT / EXPENSE_LINE), `parent_id` BIGINT, `r2_object_key`, `filename`, `content_type`, `size_bytes`, `frozen` BOOL DEFAULT false, `uploaded_by`, `uploaded_at`
Index: `(org_id, parent_type, parent_id)`

---

### Cash documents

> **Locked (rev 4).** rev-1's `CashDocument ← CashLine` (many lines per doc)
> contradicted AGENT.md decision #1 ("single-amount documents, no sub-lines",
> each In/Out its own C-numbered document). The model below matches AGENT.md.

**CashDocument** *(one single In/Out movement)*
`id`, `org_id`, `branch_id`, `document_no` (e.g. `OOR-JUL26-C-005`), `direction` ENUM(IN/OUT), `transaction_date`, `amount` NUMERIC(14,2), `bank_id` nullable, `transaction_ref` nullable, `remark` nullable, `workflow_status` ENUM(DRAFT/SUBMITTED/VERIFIED/APPROVED), `created_by`, `last_modified_by`, `created_at`, `submitted_at`
Unique: `(org_id, document_no)`

**CashDayClose** *(end-of-day close — locks a (branch, date) against new cash entries)*
`id`, `org_id`, `branch_id`, `close_date`, `opening_amount`, `computed_closing` *(opening + cash-mode receipts + cash IN − cash-mode expenses − cash OUT)*, `counted_amount`, `variance` *(counted − computed)*, `variance_remark` nullable *(required when variance ≠ 0)*, `closed_by`, `closed_at`
Unique: `(org_id, branch_id, close_date)`

**BranchCashOpening** *(first-ever opening for a branch — set by the Accountant)*
`id`, `org_id`, `branch_id`, `opening_date`, `amount`, `set_by`, `created_at`
Unique: `(org_id, branch_id)`
Every subsequent day's opening = previous day's `CashDayClose.counted_amount`.

Cash In/Out documents are excluded from Collections and Expenses KPIs everywhere — they only affect drawer math.

---

### Infrastructure tables

**DocumentSequence** *(gap-free counter)*
`id`, `org_id`, `branch_id`, `month_key` (e.g. `JUL26`), `doc_type` ENUM(R/E/C), `last_seq` INT
Unique: `(org_id, branch_id, month_key, doc_type)`
Gap-free under concurrent submits via `SELECT … FOR UPDATE` on the counter row inside the submit transaction (or `INSERT … ON CONFLICT … DO UPDATE … RETURNING`). This is the one place where locking is deliberate — documented in code.

**AuditEvent** *(append-only)*
`id`, `org_id`, `entity_type`, `entity_id`, `event_type` ENUM(CREATED/SUBMITTED/VERIFIED/APPROVED/QUERIED/REJECTED/CLOSED/OVERRIDE/LINE_ADDED/SETTLED/CATEGORY_CHANGED), `actor_type` ENUM(USER/SYSTEM), `actor_id` nullable *(null when `actor_type = SYSTEM`, e.g. auto-settle at pending 0)*, `detail` JSONB, `created_at`

---

## Flyway Migration Plan

**Numbering rule (rev 6):** schema migrations then a seed migration, sequential, per
stage — a stage's seed is always numbered after that stage's tables, never before.
No single "final seed". The `prod` profile skips every `*seed*` migration via a
Flyway callback / profile guard and instead runs a masters-only seed.

| Migration | Creates | Stage |
|---|---|---|
| V1 | `organization`, `app_user` (incl. `home_branch_id`, FK deferred to V2), `user_branch_access`; seeds Super Admin | 1 ✅ |
| V2 | `branch`, `document_sequence`; add FK `app_user.home_branch_id → branch`; add FK `user_branch_access.branch_id → branch` | 2 |
| V3 | configurable masters: `receive_category`, `receive_business_status`, `settlement_mode`, `expense_category`, `expense_sub_category`, `expense_mode`, `expense_business_status`, `bank` | 2 |
| V4 | `receiver` | 2 |
| V5 | **seed** — dummy dealership org, 3 branches (OOJ/OOB/OOR), default master rows matching the mockup, one login per role (Owner / FM / Accountant w/ branch rows / Cashier w/ `home_branch_id`), known bcrypt passwords | 2 |
| V6 | `customer`, `vehicle` (unique `org_id + vehicle_no`) | 3 ✅ |
| V7 | `audit_event` (before job-card PATCH can log `CATEGORY_CHANGED`) — `event_type`/`actor_type` CHECK-constrained; `detail` is `TEXT` holding JSON (not `jsonb`) to stay safe under Hibernate `ddl-auto: validate` — upgradeable later | 3 ✅ |
| V8 | `job_card` — incl. `category_id`, `business_status_id`, `invoice_no`, `invoice_amount` (FKs to V3 masters) | 3 ✅ |
| V9 | **seed** — sample customers / vehicles / job cards matching the mockups | 3 ✅ |
| V10 | `job_card` add `is_b2b` / `gst_no` (Receive Entry's B2C/B2B toggle + mandatory GST — a spec gap in plan.md, folded in before Stage 4) | 4 ✅ |
| V11 | `receive_document` (incl. `settled`, `last_modified_by`, partial unique index), `settlement_line`, `claim_close` (keyed by `job_card_id`), `attachment` | 4 ✅ |
| V12 | **seed** — one receive document + lines per mockup job card (R-011…R-022), one `claim_close` (Kalaivani, shortfall-accepted), JUL26 `document_sequence` rows | 4 ✅ |
| V13 | `expense_document` (incl. `last_modified_by`), `expense_line` | 5 |
| V14 | **seed** — sample expense documents | 5 |
| V15 | `cash_document` (single-movement), `cash_day_close`, `branch_cash_opening` | 6 |
| V16 | **seed** — sample cash documents / opening balances | 6 |

---

## API Endpoint List

### Auth
```
POST /api/v1/auth/login            # unauthenticated — returns one access JWT (~8h), no refresh token
POST /api/v1/auth/accept-invite    # unauthenticated
POST /api/v1/auth/change-password  # authenticated — { currentPassword, newPassword }; any role
```
No `/auth/refresh` in v1 — on token expiry the client returns to the login
screen. Revoke = deactivate the user. Refresh tokens / rotation / "remember
me" arrive in the post-review hardening stage.

### Super Admin (cross-org — own controller/service package)
```
GET    /api/v1/admin/organizations
POST   /api/v1/admin/organizations          # creates org + first Owner + invite; response includes the invite link
GET    /api/v1/admin/organizations/{id}
PATCH  /api/v1/admin/organizations/{id}      # active toggle etc.
DELETE /api/v1/admin/organizations/{id}      # permanently removes the org + all its data (branches/users/masters)
```

### Org Masters
```
GET/POST/PATCH  /api/v1/branches/{id?}
GET/POST/PATCH  /api/v1/users/{id?}          # POST a CASHIER requires home_branch_id; POST an ACCOUNTANT takes branch_ids[]
GET/POST/PATCH  /api/v1/receivers/{id?}
GET/POST/PATCH  /api/v1/masters/receive-categories/{id?}
GET/POST/PATCH  /api/v1/masters/receive-statuses/{id?}
GET/POST/PATCH  /api/v1/masters/settlement-modes/{id?}
GET/POST/PATCH  /api/v1/masters/expense-categories/{id?}
GET/POST/PATCH  /api/v1/masters/expense-sub-categories/{id?}
GET/POST/PATCH  /api/v1/masters/expense-modes/{id?}
GET/POST/PATCH  /api/v1/masters/expense-statuses/{id?}
GET/POST/PATCH  /api/v1/masters/banks/{id?}
```
PATCH never deletes — `active = false` only.

### Customer / Vehicle / Job Card
```
GET    /api/v1/customers                     # ?q= search
POST   /api/v1/customers
GET    /api/v1/customers/{id}
GET    /api/v1/customers/{id}/history
GET    /api/v1/vehicles?vehicle_no=
POST   /api/v1/vehicles
POST   /api/v1/job-cards                     # inline customer/vehicle create; sets category, business_status, invoice_no/amount, dbm_id
GET    /api/v1/job-cards/{id}                # derived: pending_amount (0 if ClaimClose exists), is_claim,
                                            #   settled_via_claim_close, claim_final_amount
PATCH  /api/v1/job-cards/{id}                # update invoice_no / invoice_amount / dbm_id anytime;
                                            #   category_id / business_status_id only while no ClaimClose exists (else 409);
                                            #   a category_id change writes an AuditEvent (CATEGORY_CHANGED, before/after)
POST   /api/v1/job-cards/{id}/close-claim    # FINANCE_MANAGER — one transaction: write the immutable ClaimClose (optional
                                            #   final override) + flip settled=true on every still-open ReceiveDocument
```

### Receive Documents
```
POST   /api/v1/receipts                      # against a job card; 409 if that job card has a ClaimClose (no new doc on a closed claim);
                                            #   otherwise reuses the open (settled=false) doc if one exists
GET    /api/v1/receipts/{id}                 # joins job-card fields: category, business_status, invoice_no, invoice_amount,
                                            #   pending_amount (0 if ClaimClose), settled_via_claim_close, claim_final_amount
POST   /api/v1/receipts/{id}/submit
POST   /api/v1/receipts/{id}/verify
POST   /api/v1/receipts/{id}/approve
POST   /api/v1/receipts/{id}/query
POST   /api/v1/receipts/{id}/reject
POST   /api/v1/receipts/{id}/resubmit
POST   /api/v1/receipts/{id}/lines           # "Add Payment" also lands here
PATCH  /api/v1/receipts/{id}/lines/{lineNo}/override
POST   /api/v1/receipts/{id}/attachments
POST   /api/v1/receipts/{id}/lines/{lineNo}/attachments
GET    /api/v1/attachments/{id}              # returns a short-lived signed URL
DELETE /api/v1/attachments/{id}              # 409 if frozen
```
`close-claim` moved to `/job-cards/{id}/close-claim` — you close the case, not one document. The FM reaches it from a document detail view in the review queue.

### Expense Documents
```
POST   /api/v1/expenses
GET    /api/v1/expenses/{id}
POST   /api/v1/expenses/{id}/submit
POST   /api/v1/expenses/{id}/verify
POST   /api/v1/expenses/{id}/approve
POST   /api/v1/expenses/{id}/query
POST   /api/v1/expenses/{id}/reject
POST   /api/v1/expenses/{id}/resubmit
POST   /api/v1/expenses/{id}/close           # ACCOUNTANT
POST   /api/v1/expenses/{id}/transfer-to-claim
POST   /api/v1/expenses/{id}/lines
PATCH  /api/v1/expenses/{id}/lines/{lineNo}/override
POST   /api/v1/expenses/{id}/attachments
POST   /api/v1/expenses/{id}/lines/{lineNo}/attachments
```

### Cash Documents
```
POST   /api/v1/cash-documents                # one IN or OUT movement
GET    /api/v1/cash-documents/{id}
POST   /api/v1/cash-documents/{id}/submit
POST   /api/v1/cash-documents/{id}/verify
POST   /api/v1/cash-documents/{id}/approve
GET    /api/v1/cash-documents?branch_id=&date=
GET    /api/v1/cash/drawer?branch_id=&date=  # live computed drawer position
POST   /api/v1/cash/opening                  # ACCOUNTANT — first-ever opening for a branch
POST   /api/v1/cash/close-day                # CASHIER — counted amount + variance remark; locks the date
GET    /api/v1/cash/close-day?branch_id=&date=
```

### Cross-cutting
```
GET    /api/v1/search?q=                     # matches customer name/phone, vehicle_no, job-card id, job_card.invoice_no, document_no
GET    /api/v1/my-entries                    # today + recent, workflow badges, queried highlighted
GET    /api/v1/audit/overrides               # Owner + FM, org-wide, filter by user/branch/date
GET    /api/v1/dashboard/kpis
GET    /api/v1/dashboard/trend
GET    /api/v1/dashboard/collections-by-mode
GET    /api/v1/dashboard/expenses-by-dept
GET    /api/v1/dashboard/outstanding
GET    /api/v1/dashboard/activity-feed
GET    /api/v1/dashboard/branch-comparison
```

Each stage adds its own indexes for the query paths it introduces (search, my-entries, review queue, dashboard aggregates) — not deferred to a later "perf" pass.

---

## Stage-by-Stage Build Order

### Stage 1 — Repo skeleton + auth + org onboarding
**Demo:** Super Admin logs in → creates org → gets the invite link on screen → opens it → Owner sets password → sees empty dashboard shell.

- Repo structure: `backend/`, `frontend/`, `docker-compose.yml`, `README.md`
- V1 migration (Organization, User incl. `home_branch_id`, UserBranchAccess) — seeds Super Admin
- JWT auth: login (one ~8h access token, no refresh), accept-invite
- `EmailService` interface + `LoggingEmailService` impl (logs link, API returns it) — no SMTP
- Super Admin endpoints: create org (+ first Owner + invite), list, get, patch
- Neon wired via `SPRING_DATASOURCE_URL` for every profile; optional separate Neon branch for `local`
- Hibernate `@Filter` tenant scoping wired from the JWT principal (set once per request); cross-org test stub in place even though nothing tenant-scoped exists yet
- React app: login screen, set-password screen, role-based shell layout, account menu (name / role / branch / Logout), token in `sessionStorage`, auto-redirect to login on 401/expiry
- Frontend: add `recharts`, shadcn/ui base (button/input/dialog/table/select), `class-variance-authority`/`clsx`/`tailwind-merge`, `react-markdown` + `remark-gfm`; Vitest + RTL with one smoke test
- GitHub Actions: build + backend tests + frontend tests on PR (merge blocked on failure); image push to GHCR on merge to main; provider-agnostic deploy step
- `docker-compose.yml` (backend + frontend only, both → Neon), multi-stage `backend/Dockerfile` + `frontend/Dockerfile` (nginx), `README.md` (run, test, re-seed Neon dev branch)

### Stage 2 — Branch, user, and masters setup ✅ (verified on Neon)
**Demo:** Owner adds branches, users (Cashier → pick home branch; Accountant → pick branches), assigns access, edits all lookup masters. Matches `owner-dashboard.html` Team & Branches tab.

- V2 (Branch, DocumentSequence, deferred FKs) · V3 (8 master tables) · V4 (Receiver) · V5 seed (demo org, OOJ/OOB/OOR, default masters matching the mockups, one login per role — README)
- Branch CRUD, Receiver CRUD, Masters CRUD (one `/api/v1/masters/{type}` controller for all 8; reads open to any org user, writes OWNER-only, deactivate-never-delete)
- User CRUD (`/api/v1/users`, OWNER-only) — role-driven branch binding (OWNER/FM org-wide, ACCOUNTANT `branchIds[]` → `user_branch_access`, CASHIER one `home_branch_id`), creating a user issues an invite, last-active-Owner guard, self-lockout guard
- Org settings (`GET/PATCH /api/v1/organization`) — name + `multi_branch_cashier_access` toggle
- `@Filter(orgFilter)` on every tenant entity — `TenantFilterActivator` now enables it before every repository call; `CrossOrgIsolationTest` (Testcontainers Postgres, skips without Docker, runs in CI)
- Frontend: Owner **Team & Branches** (branches + users tables + add/edit modals), **Masters** (left rail + per-type table/modal), **Settings** (change password; Owner also gets org settings); Super Admin **Delete organization** (type-name-to-confirm)
- Help stubs: `owner/team-and-branches.md`, `owner/masters.md`

### Stage 3 — Customer, Vehicle, Job Card, universal search ✅ (verified on Neon)
**Demo:** Cashier searches by name / vehicle / job card / invoice. Clicking a result shows the customer history card. Matches `cashier-home.html` home + history.

- V6 (Customer, Vehicle — `vehicle_no` normalised + unique per org) · V7 (AuditEvent — `event_type` / `actor_type` CHECK-constrained, `detail` stored as TEXT JSON for now) · V8 (JobCard — invoice/dbm refs + `category_id`/`business_status_id` FKs) · V9 seed (8 customers / vehicles / job cards from `cashier-home.html`, on the demo org across OOJ/OOB/OOR)
- Customer CRUD + `/customers/{id}/history` (card totals + job-card list + timeline — `received`/`outstanding`/`timeline`/`workflowStatus` are Stage-4 placeholders); Vehicle lookup + create with dedup (existing normalised number wins, no duplicate row)
- Job card: `POST /job-cards` (inline customer/vehicle create; CASHIER's branch is forced to their home branch, others must pass a `branchId` in their scope), `GET /job-cards/{id}` (derived `reference` `{branchCode}-JC-{id}`, `is_claim` via category join, `pendingAmount` = invoice amount for now), `PATCH /job-cards/{id}` (invoice/dbm free; `categoryId`/`businessStatusId` guarded by `hasClaimClose()` — a stub returning false until Stage 8, so the 409 path is wired but unreachable; a `categoryId` change writes a `CATEGORY_CHANGED` AuditEvent with `{before, after}`)
- `AuditService` — the single append-only writer (`recordUserEvent` / `recordSystemEvent`); first users are JobCard `CREATED` and `CATEGORY_CHANGED`
- `BranchScope` helper — resolves the caller's visible branches from role + `user_branch_access` + the org toggle, read fresh from the DB; reused by later stages
- `GET /search?q=` — ≥2 chars; matches customer name/phone (org-wide), vehicle number (org-wide), and job-card id / `invoice_no` / `dbm_id` (branch-scoped via `BranchScope`); one hit per customer with the matched-field label. The mockup's "outstanding" column shows `totalInvoiced` until Stage 4 brings settlement lines.
- `OrganizationPurgeService` extended — job cards / audit events / vehicles / customers added to the Super Admin org-purge, FK-safe order
- Frontend: Cashier home (`src/cashier/CashierHomePage.tsx`) — debounced search box, results list, customer history card, "recently looked up" strip in `sessionStorage`; New Receipt / New Expense / Add Payment render disabled ("Stage 4"). Wired as the CASHIER index route.
- Help stub: `help/cashier/finding-a-customer.md`
- Tests: `JobCardServiceTest` (cashier home-branch forcing, CREATED + CATEGORY_CHANGED audit, no-op category patch), `VehicleServiceTest` (normalisation, dedup) — backend 34 pass / 1 skipped (Testcontainers)

### Stage 4 — Receive document ✅ (verified on Neon)
**Demo:** Cashier creates a receipt (with inline customer/vehicle/job-card create), adds lines, attaches a PDF, submits. Add Payment appends to the existing open doc. My Entries highlights queried items.

- V10 (`job_card` + `is_b2b` / `gst_no`) · V11 (ReceiveDocument, SettlementLine, ClaimClose, Attachment) · V12 seed (one receive doc + lines per mockup job card, one `claim_close`, JUL26 sequence rows). See rev 9 for the decisions.
- **B2B / GST** on the job card — B2C/B2B toggle + GST# required when B2B (`JobCardService`, create + PATCH). Frontend toggle + conditional GST# field restored per `cashier-home.html`.
- Document numbers — gap-free, sequential, never reused; assigned **on submit** (`INSERT … ON CONFLICT … RETURNING` in the submit txn) — `DocumentNumberService`. Line ids `{doc}-L{n}`, stamped at submit / on Add Payment, never reused (deleted lines aren't renumbered). Tests: `DocumentNumberServiceTest` (Testcontainers, CI).
- Pending Amount — `PendingAmountCalculator`: `invoice − Σ lines across the job card's non-REJECTED receive docs`; 0 when no invoice; 0 when a `ClaimClose` exists (incl. shortfall: invoiced ₹23,101, claim final ₹22,875 → 0, not ₹226). `PendingAmountCalculatorTest`.
- `settled` auto-flip at pending 0 (invoice present, no claim) with a `SYSTEM` `SETTLED` AuditEvent + attachment freeze. Claim-close-forces-settled stays Stage 8. `JobCardService.hasClaimClose` is now real.
- One-open-document-per-job-card invariant (partial unique index → 409) + `POST /receipts` reuses the open doc; Add Payment (`POST /receipts/{id}/lines`) appends to it, never a second doc. `ReceiveDocumentServiceTest`.
- `POST /receipts` refuses (409) when the target job card has a `ClaimClose`.
- **Cross-branch write block** — a cashier can only create/modify receive documents for job cards in their own home branch (409, names both branches); unaffected by `multi_branch_cashier_access`. `ReceivePaymentGuard` (+ `canRecordPayment` UI hint). `ReceivePaymentGuardTest`.
- Branch is always the job card's branch — `CreateReceiptRequest` has no branch field.
- `StorageService` interface + `LocalFilesystemStorageService` (default; HMAC-signed URL via `/api/v1/attachments/raw`). **R2 impl deferred** (offline build can't fetch the S3 SDK) — `dams.storage.provider=r2` wired, one new class away. `attachment` rows, signed-URL retrieval, freeze-on-settle. `AttachmentServiceTest`.
- `created_by` + `last_modified_by` on every mutation (feeds maker-checker in Stage 7).
- `GET /api/v1/my-entries` — caller's own docs newest-first, `today` / `queried` flags. Seed has a QUERIED doc (Suresh, R-013) for the fix-and-resubmit demo.
- `OrganizationPurgeService` extended (attachments / settlement lines / claim closes / receive docs, FK-safe). `GlobalExceptionHandler`: `DataIntegrityViolation` → 409, upload-too-large → 400.
- Frontend: `NewReceiptPage` (`/new-receipt`, incl. `?editDoc=` resubmit mode), `AddPaymentModal`, `ViewReceiptsModal` (button, not thumbnails), `MyEntriesPage` (`/my-entries`); Cashier home wired (quick-action row + real Add Payment / View Receipts / history figures / search outstanding).
- Help stubs: `cashier/creating-a-receipt.md`, `cashier/adding-a-payment.md`, `cashier/fixing-a-queried-entry.md`
- Tests: backend 56 pass / 2 skipped (Testcontainers — run in CI). Frontend tsc · lint · vitest · build all green.

### Stage 5 — Expense document
**Demo:** Cashier creates an expense, picks category/sub-category, sees the limit warning, submits. Transfer-to-Claim available on qualifying job cards.

- V13 migration (ExpenseDocument incl. `last_modified_by`, ExpenseLine); V14 seed migration (sample expense docs)
- Expense CRUD + submit + add-line endpoints; lines can be added until the Accountant closes the document
- Over-limit detection against `expense_sub_category.limit_amount`
- Transfer-to-claim endpoint (status change only, no new document)
- Frontend: New Expense form (category → sub-category cascade, limit warning)
- Help stub: `cashier/recording-an-expense.md`

### Stage 6 — Cash document + day close
**Demo:** Cashier opens the Cash page, sees the live drawer position, adds Cash In / Out movements (each its own C-numbered doc), closes the day with a variance remark. Closing locks the date.

- V15 migration (CashDocument single-movement, CashDayClose, BranchCashOpening); V16 seed migration (sample cash docs / openings)
- Cash movement endpoints (single amount, maker-checker) + `/cash/opening` (Accountant) + `/cash/close-day` (Cashier)
- Drawer-position computation (opening + cash-mode receipts + cash IN − cash-mode expenses − cash OUT), Asia/Kolkata day boundary
- Day-close: counted amount, auto variance, remark mandatory when variance ≠ 0, date lock
- Frontend: Cash page — drawer summary, movements table, Close Day button
- Help stubs: `cashier/cash-in-and-out.md`, `cashier/closing-the-day.md`

### Stage 7 — Accountant review queue
**Demo:** Accountant sees the queue, verifies / overrides / queries / rejects. Cashier sees queried items in My Entries and fixes + resubmits. Matches `review-close.html` Accountant view.

- Accountant workflow endpoints: verify, override-line, query, reject
- Resubmit endpoint (CASHIER)
- Maker-checker guard: actor ≠ `created_by` and ≠ `last_modified_by`
- Override recorded with actor + reason + timestamp; AuditEvent at every transition
- Frontend: two-pane layout (queue + detail), action bar, fix-and-resubmit flow
- Help stubs: `accountant/reviewing-the-queue.md`, `accountant/overriding-an-amount.md`, `accountant/closing-an-expense.md`

### Stage 8 — FM approval, claim closing, override audit
**Demo:** FM approves / queries / rejects. FM closes Warranty/AMC/CG claims via `POST /job-cards/{id}/close-claim` with an optional final override ("Overridden · Final" badge everywhere after). Owner/FM see the org-wide Override Audit screen. Matches `review-close.html` FM view.

- FM workflow endpoints: approve, close-claim — one transaction writes the immutable ClaimClose per job card **and** flips `settled = true` on every still-open ReceiveDocument of that job card (tested: shortfall-accepted claim → no doc left "open", `pending_amount` 0)
- Once ClaimClose exists: `PATCH /job-cards/{id}` rejects `category_id` / `business_status_id` (409), `POST /receipts` on that job card is refused (409), and `pending_amount` reports 0 — all covered by tests
- Override audit endpoint (filter by user / branch / date)
- Frontend: FM queue, claim-close modal, closed-claim summary, Override Audit screen
- Help stubs: `finance-manager/approving-entries.md`, `finance-manager/closing-a-claim.md`, `finance-manager/override-audit.md`

### Stage 9 — Owner dashboard
**Demo:** Owner sees KPI cards, trend chart, donut, branch comparison, expenses, outstanding, activity feed — filterable by branch and period. Matches `owner-dashboard.html` dashboard tab.

- Dashboard aggregate endpoints (read-only); Cash In/Out excluded from Collections & Expenses figures
- Frontend: KPI row with count-up, SVG trend chart, donut (recharts), branch-comparison table, horizontal bars, outstanding list, activity feed
- Help stubs: `owner/reading-the-dashboard.md`, `owner/comparing-branches.md`

### Stage 10 — Super Admin panel
**Demo:** Super Admin sees all orgs, onboards a new one, toggles active. Same visual language.

- Super Admin frontend panel (no new backend)
- Org list table, onboard modal (shows the invite link to copy), active toggle
- Help stubs: `super-admin/onboarding-an-organization.md`

### Stage 11 — Help Center
**Demo:** Every role opens Help from the shell and sees a clean, role-scoped table of contents; each article is short, numbered steps with a screenshot per step; screens have a contextual "?" that jumps to the right article.

- Help framework: `frontend/src/help/<role>/*.md` + a manifest per role (slug, title, order, which screen(s) link to it)
- `HelpDrawer` / `/help` route: role-scoped TOC, article renderer (`react-markdown` + `remark-gfm`), in-help search over titles + body
- Contextual `?` control wired into each screen header → deep-links to its article
- Quality pass on every stub written in Stages 2–10: rewrite for dealership staff (minimal prose, numbered steps), capture screenshots against the seeded dummy data, consistent voice
- Articles: Cashier (finding a customer, creating a receipt, adding a payment, recording an expense, cash in/out, closing the day, fixing a queried entry), Accountant (reviewing the queue, overriding an amount, closing an expense), Finance Manager (approving entries, closing a claim, override audit), Owner (team & branches, masters, reading the dashboard, comparing branches), Super Admin (onboarding an organization)
- No backend, no migration

### Post-review hardening (after client sign-off — not v1)
- Persistent login: `POST /auth/refresh`, refresh-token rotation, "remember me", session survives browser restart
- `prod` seed profile (masters only, no dummy org/users); remove/guard V11 test seed
- Revisit DB topology (dedicated prod Neon project or self-hosted Postgres), reinstate a local Postgres option if wanted
- Real `EmailService` implementation (SMTP/provider)

---

## Resolved (locked in rev 4)

1. **Cash model** — `CashDocument` = one single In/Out movement (matches
   AGENT.md decision #1), `CashDayClose` + `BranchCashOpening` alongside.
2. **Attachments** — separate `attachment` table, `0..n` per line / document.
3. **`last_modified_by`** — on receive / expense / cash documents; maker-
   checker guard is actor ≠ `created_by` and ≠ `last_modified_by`.
4. **Job-card `category_id` / `business_status_id`** — mutable via
   `PATCH /job-cards/{id}` **until** a `ClaimClose` exists (then 409);
   `category_id` changes are audited (`CATEGORY_CHANGED`).
5. **Frontend tests** — Vitest + React Testing Library.
6. **Closed-claim guard** — `POST /receipts` refuses (409) when the target
   job card has a `ClaimClose`.
7. **ClaimClose → Pending Amount** — `pending_amount` is 0 once a
   `ClaimClose` exists (settled at `final_amount`), never the raw shortfall.
8. **ClaimClose → settled** — close-claim, in the same transaction, flips
   `settled = true` on every still-open ReceiveDocument of the job card.

## Other things I flagged in the plan (already folded in above)

- `DocumentSequence` needs an explicit row-lock / upsert-returning strategy
  for gap-free numbering under concurrent submits — now stated in the entity.
- `AuditEvent` needed an `actor_type` (USER/SYSTEM) + nullable `actor_id`
  for system-triggered events like auto-settle — added.
- Attachment retrieval is via `GET /attachments/{id}` returning a short-lived
  signed URL, not per-parent GET endpoints — endpoint list simplified.
- "Today" is date-sensitive (drawer, day-close, My Entries) — pinned to
  Asia/Kolkata.
- Refresh tokens: stateless JWT pair, no server store in v1 — stated.

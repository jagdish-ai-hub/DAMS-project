# DAMS — Dealer Activity Management System

Replaces Excel-based service-station bookkeeping for truck dealerships with a small,
correct, auditable multi-tenant system. See **AGENT.md** (spec) and **plan.md** (build plan)
for everything; this file is just how to run it.

## What's built

**Stage 1**
- JWT auth: `POST /api/v1/auth/login`, `/auth/accept-invite`, `/auth/change-password`
  (one ~8h access token, no refresh — re-login on expiry).
- Super Admin: create organization + invite first Owner; list / get / activate-toggle /
  **delete** (removes the org and all its data).
- React shell: login, set-password, role-aware frame with an account menu.

**Stage 2**
- `branch`, 8 configurable master lists, `receiver`, `document_sequence` — migrations
  V2–V4; V5 seeds the demo dealership.
- CRUD: `/api/v1/branches`, `/api/v1/receivers`, `/api/v1/masters/{type}`, `/api/v1/users`
  (Owner-only), `/api/v1/organization` settings — reads open to any signed-in org user,
  writes OWNER-only, deactivate-never-delete.
- Tenant isolation is live: `branch`, masters and `receiver` carry `org_id` and a
  Hibernate `@Filter` enabled per request from the JWT (`TenantFilter` → `TenantFilterActivator`).
  `CrossOrgIsolationTest` proves it (Testcontainers — needs Docker locally, always runs in CI).
- Frontend: Owner **Team & Branches** and **Masters** screens, a **Settings** screen
  (change password; Owner also edits org settings), and a Super Admin **Delete** action.

**Stage 3**
- `customer`, `vehicle` (number normalised + unique per org), `job_card`, `audit_event` —
  migrations V6–V8; V9 seeds 8 customers / vehicles / job cards from `cashier-home.html`.
- CRUD: `/api/v1/customers` (+ `/{id}/history`), `/api/v1/vehicles` (lookup + deduped
  create), `/api/v1/job-cards` (`POST` with inline customer/vehicle create, `GET` with a
  derived `{branchCode}-JC-{id}` reference, `PATCH` — invoice/dbm free, category/status
  guarded; a category change writes a `CATEGORY_CHANGED` audit event).
- `GET /api/v1/search?q=` — universal search (name / phone / vehicle / job card / invoice),
  branch-scoped for the job-card side via `BranchScope` (a cashier sees only their home
  branch unless the org toggle is on).
- Frontend: Cashier home — search, results, customer history card, recently-looked-up strip.

**Stage 4**
- `receive_document`, `settlement_line`, `claim_close`, `attachment` — migrations V10 (job-card
  `is_b2b` / `gst_no`), V11 (tables), V12 (seed: one receive doc + lines per mockup job card).
- `POST /api/v1/receipts` — one flow: an existing `jobCardId` **or** inline job-card create,
  plus `lines[]` and optional `submit`. No branch field — the document posts under the job
  card's branch. `submit` / `resubmit` / `lines` (Add Payment) / line edit+delete endpoints.
- Gap-free `DAMS-Receive-ID` (`OOR-AUG26-R-001`) assigned on submit; line ids `{doc}-L{n}`,
  never reused. Pending Amount is job-card-wide (`invoice − Σ lines`, 0 with no invoice or a
  closed claim); a receipt **auto-settles** at pending 0. One open document per job card.
- A cashier can only touch job cards in **their own home branch** (even with multi-branch
  search on) — cross-branch Add Payment is refused, not guessed.
- `POST /api/v1/receipts/{id}/attachments` (+ per-line) — PDF/image receipts via
  `StorageService`: local filesystem in dev (signed URLs from `/api/v1/attachments/raw`),
  or Cloudflare R2 with `dams.storage.provider=r2` (`R2_ENDPOINT` / `R2_ACCESS_KEY_ID` /
  `R2_SECRET_ACCESS_KEY` / `R2_BUCKET`). Frozen once the receipt settles.
- `GET /api/v1/my-entries` — the cashier's own entries, today + recent, queried highlighted.
- Frontend: New Receipt page, Add Payment modal, View Receipts modal, My Entries page;
  cashier home wired end-to-end.

**Stage 5**
- `expense_document`, `expense_line` — migrations V13 (`expense_mode` / `expense_business_status`
  flag columns), V14 (tables + `TRANSFERRED_TO_CLAIM` audit type + doc-number search indexes),
  V15 (seed: 5 expense docs across states).
- `POST /api/v1/expenses` — one flow: `receiverId` **or** inline `receiverName` (deduped),
  optional `jobCardId`, `lines[]`, optional `submit`. No branch field — posts under the
  cashier's home branch. `PATCH` header (draft/queried), `submit` / `resubmit`, `lines`
  (Add Expense — allowed until the Accountant closes the doc), line edit+delete, plus
  document/line attachments.
- Gap-free `DAMS-Expenses-ID` (`OOR-AUG26-E-001`) on submit; `over_limit` flag recomputed on
  every line change (`amount > expense_sub_category.limit_amount`) — flags for Finance
  approval, never blocks.
- `POST /api/v1/expenses/{id}/transfer-to-claim` — only when the expense sits on a
  warranty / AMC / goodwill job card (`receive_category.is_claim`); a status change, no new
  document.
- A cashier can only record expenses under **their own home branch** (cross-branch job-card
  tag refused, 409).
- `GET /api/v1/my-entries` now merges receipts + expenses; universal search matches
  receive + expense `document_no`.
- Frontend: New Expense page (category→sub-category cascade, per-line limit warnings,
  Transfer to Claim); My Entries shows both kinds; cashier home "New Expense" wired.

**Stage 6**
- `cash_document`, `branch_cash_opening`, `cash_day_close` — migrations V16 (`is_cash` flag on
  settlement/expense modes), V17 (tables), V18 (seed: OOR opening, four cash docs, one close).
- `POST /api/v1/cash-documents` — one IN-from-bank / OUT-to-bank movement, single amount, no
  branch field (posts under the cashier's home branch). `submit` / `resubmit` / `PATCH`
  (draft or queried) / delete-a-draft. `C`-numbered on submit (`OOR-AUG26-C-001`).
- `GET /api/v1/cash/drawer?date=` — live drawer position + breakdown + the day's movements:
  `opening + cash-mode receipts + cash IN − cash-mode expenses − cash OUT`. Opening is the
  previous day's counted close, or the Accountant's one-time `POST /api/v1/cash/opening`.
- `POST /api/v1/cash/close-day` — counted amount, auto variance, remark required when the
  variance isn't zero. Closing **locks the date**: no new cash movements, and no cash-mode
  receipt/expense line can be backdated into it.
- `GET /api/v1/my-entries` now merges cash movements too.
- Frontend: Cash page (`/cash`) — drawer card, movements table, Cash In / Out and Close Day
  modals; `Cash` in the cashier nav.

**Stage 7**
- Accountant review — new `com.dams.review` package, no migration (override columns,
  workflow / event-type CHECKs and queue indexes all pre-existed).
- `GET /api/v1/review/receipts` · `/review/expenses` — the accountant's queue (SUBMITTED,
  in their assigned branches). `POST /api/v1/{receipts|expenses}/{id}/verify` · `/query`
  (note) · `/reject` (reason) · `/lines/{lineNo}/override` (amount + reason); plus
  `POST /api/v1/expenses/{id}/close`.
- `ReviewGuard`: ACCOUNTANT only, branch-scoped, and never a document you created or last
  modified (maker-checker). Review actions don't touch `last_modified_by`, so one
  accountant can override a line and still verify the same document.
- A line override stamps `original_amount` + `overridden_by/at` and writes an `OVERRIDE`
  audit row; it recomputes an expense's `over_limit` and re-runs a receipt's auto-settle.
  Closing an **over-limit** expense needs Finance Manager `APPROVED` first (Stage 8).
- `GET /api/v1/{receipts|expenses}/{id}` is now branch-scoped for every role. Both document
  responses gain a `history` array (humanised audit events) — drives the review pane and
  the cashier's "Query from the accountant" banner.
- Frontend: `/` for an Accountant is the **Review Queue** — two-pane queue + overview +
  record detail with inline verify / query / reject / override.

**Stage 8**
- Finance Manager approval + claim closing + Override Audit. Migration **V19** adds
  `audit_event.branch_id` (nullable, backfilled) so overrides can be filtered by branch.
- `GET /api/v1/review/fm/{receipts|expenses}` — the FM queue (`awaitingApproval` +, for
  receipts, `openClaims` and `recentlyClosed`). `POST /api/v1/{receipts|expenses}/{id}/approve`
  (VERIFIED → APPROVED). `/query` and `/reject` now serve both reviewers — the Accountant
  from SUBMITTED, the FM from VERIFIED, both landing on QUERIED / REJECTED.
- `POST /api/v1/job-cards/{id}/close-claim` `{ finalAmount, reason? }` — FM only. One
  transaction writes the immutable `claim_close` row **and** flips `settled = true` on every
  open receive document of the job card (freezing their receipts). A reason is required when
  the final amount differs from what was received; that difference then shows as
  **"Overridden · Final"** everywhere. Every non-rejected receive document on the claim must
  already be APPROVED. After close: `PATCH /job-cards` category/status → 409, `POST /receipts`
  → 409, `pending_amount` = 0.
- `GET /api/v1/override-audit?userId=&branchId=&from=&to=` (Owner + FM) — one feed of every
  amount override: the Accountant's line overrides **and** the FM's claim-close overrides.
- Frontend: `/` for an FM is **Approvals & Claims** (three-section queue, Approve / Query /
  Reject, claim-close modal); **Override Audit** screen at `/override-audit` for FM + Owner.

**Stage 8.5 — Cash review**
- Cash movements run the same maker-checker chain as receipts / expenses.
  `POST /api/v1/cash-documents/{id}/{verify|approve|query|reject}`;
  `GET /api/v1/review/cash` (Accountant, SUBMITTED in-branch) and
  `GET /api/v1/review/fm/cash` (FM, VERIFIED org-wide). A **Cash** tab joins Receipts /
  Expenses in both review screens with a compact cash detail panel + History.

**Stage 9 — Owner dashboard**
- `GET /api/v1/dashboard/{summary|outstanding|activity}` (Owner + FM). `summary` returns
  KPI cards (collections / expenses / net / cash-in-hand / pending-review — **money counts
  APPROVED docs only; cash In/Out never counts as collections or expenses**), a 14-day
  trend, collections-by-mode, expenses-by-category, and a per-branch comparison table.
- Frontend: `owner/DashboardPage.tsx` is the Owner home — branch + period (Today / MTD)
  filters, `recharts` area + donut, outstanding list, activity feed.
- The aggregates are batched (one drawer roll-up for all branches, grouped
  pending-review / settlement-sum queries) — see plan.md rev 15 for the numbers.

**Stage 10 — Super Admin panel**
- `superadmin/OrganizationsPage.tsx` (frontend only; Stage 1 admin endpoints): org table
  with created date / cashier-access / status, onboard form with a copy-able invite link,
  activate / deactivate, and a type-name-to-confirm delete modal.

**Stage 11 — Help Center**
- `? Help` in the shell header opens a right-side drawer: search + role-scoped table of
  contents + Markdown articles bundled from `frontend/src/help/<role>/*.md`
  (`help/manifest.ts` fixes order + titles). No backend, no migration.
- Text-only by design (no screenshots). Per-screen contextual `?` deep-links deferred.

## Prerequisites

- **JDK 21** (the build targets 21; newer JDKs may work but aren't the target).
- **Maven 3.9+** (or use Docker — see below).
- **Node 22+**.
- A **Neon** Postgres database. During the test phase there is **no local Postgres** — every
  environment points at one shared Neon instance. Project: `dams` (see plan.md → Neon Database).

## First-time setup

```bash
cp .env.example .env
# Fill in SPRING_DATASOURCE_URL / USERNAME / PASSWORD from the Neon console.

cd frontend && npm install   # generates package-lock.json — commit it
```

## Run — Windows one-click

Double-click **`dams.bat`** in the repo root (or `.\dams.bat`). It frees ports 8080/2314,
loads `.env`, and opens the backend and frontend each in its own window.

- Frontend → http://localhost:2314
- Backend  → http://localhost:8080
- Backend is ready ~35s after start (Flyway + Neon).

## Run — Docker (one command)

```bash
docker compose up --build
```

- Backend  → http://localhost:8080
- Frontend → http://localhost:5173

## Run — without Docker

```bash
# backend (reads .env-style vars from the environment)
cd backend
SPRING_PROFILES_ACTIVE=local \
SPRING_DATASOURCE_URL=... SPRING_DATASOURCE_USERNAME=... SPRING_DATASOURCE_PASSWORD=... \
mvn spring-boot:run

# frontend
cd frontend
npm run dev
```

## API docs (Swagger)

- **Swagger UI:** http://localhost:8080/swagger-ui.html — browse and try every endpoint
- **OpenAPI JSON:** http://localhost:8080/api-docs

Both are open (no token needed to view). To *try* a secured endpoint:

1. Run `POST /api/v1/auth/login` in Swagger with a seeded login (below), e.g.
   `{"email":"owner@jjmotors.demo","password":"owner123"}`
2. Copy `accessToken` from the response.
3. Click **Authorize** (top-right), paste the token, **Authorize**, **Close**.

Swagger remembers the token across reloads (`persist-authorization`), and the `local`
profile issues a 30-day token, so you authorize once per month, not per session.

One-liner to grab a token from the shell:

```bash
curl -s http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"owner@jjmotors.demo","password":"owner123"}'
```

## Seeded logins

**Platform Super Admin** (V1) — change the password after first login via
`POST /api/v1/auth/change-password` / the Settings screen:

| Email                 | Password        | Role        |
|-----------------------|-----------------|-------------|
| `dams@jjsoftware.com` | `Welcome12345@` | SUPER_ADMIN |

**Dummy dealership "JJ Motors (Demo)"** (V5, test phase only) — one login per role:

| Email                        | Password         | Role            | Branch access        |
|------------------------------|------------------|-----------------|----------------------|
| `owner@jjmotors.demo`        | `owner123`       | OWNER           | org-wide             |
| `finance@jjmotors.demo`      | `finance123`     | FINANCE_MANAGER | org-wide             |
| `accountant@jjmotors.demo`   | `accountant123`  | ACCOUNTANT      | Berhampur, Rayagada  |
| `cashier@jjmotors.demo`      | `cashier123`     | CASHIER         | home branch: Rayagada|

Branches: `OOJ` Jeypore · `OOB` Berhampur · `OOR` Rayagada.

## Tests

```bash
cd backend  && mvn test      # backend unit + slice tests
cd frontend && npm test      # Vitest + React Testing Library
```

## Reset the database

Flyway migrations live in `backend/src/main/resources/db/migration`. To start clean, drop
the objects on the Neon branch (or reset the branch in the Neon console) and restart the
backend — Flyway re-applies `V1…` from scratch. Consider a dedicated Neon branch for
throwaway local data (see plan.md → Neon Database).

## Layout

```
backend/    Spring Boot (Java 21, Maven, Flyway)
frontend/   React 18 + Vite + TypeScript
.github/    CI: build + test on PR; build & push images to GHCR on merge to main
```

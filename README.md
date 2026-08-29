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
  `StorageService` (local filesystem in dev, signed URLs from `/api/v1/attachments/raw`;
  R2 impl is stubbed behind `dams.storage.provider=r2`). Frozen once the receipt settles.
- `GET /api/v1/my-entries` — the cashier's own entries, today + recent, queried highlighted.
- Frontend: New Receipt page, Add Payment modal, View Receipts modal, My Entries page;
  cashier home wired end-to-end.

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

## Run — Docker (one command)

```bash
docker compose up --build
```

- Backend  → http://localhost:8080  (Swagger UI at `/swagger-ui.html`)
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

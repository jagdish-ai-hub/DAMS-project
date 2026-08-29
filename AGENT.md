# AGENT.md — DAMS (Dealer Activity Management System)

This file is the spec for any AI coding agent working on this project
(Claude Code, or others). Read it fully before writing code. If a rule here
conflicts with something convenient to build, this file wins — update this
file first if the rule itself needs to change, then build.

## What this is

DAMS replaces Excel-based service-station bookkeeping for truck dealerships
— receipts, expenses, cash tracking, warranty/AMC claims — with a small,
correct, auditable system. It was built for one dealership group first, but
is architected as a **multi-tenant SaaS product** from day one, so it can be
sold to other dealership groups without a redesign.

Tally remains the accounting system of record. DAMS is not accounting
software — it's the clean, verified, ledger-tagged pipe that feeds
accountants, plus the owner's live window into branch operations.

## Role hierarchy (five levels)

- **SUPER_ADMIN** — platform level, not tied to any organization. Onboards
  new dealership organizations (creates the org + its first Owner login).
  Can see all organizations for platform management; never sees another
  org's transactional data by default.
- **OWNER** — runs one organization. Sees all branches within their org.
  Adds branches, adds users, assigns roles and branch access. Read-only on
  transactions; never edits them.
- **FINANCE_MANAGER** — all branches within their org. Final approval on
  every entry. Closes Warranty/AMC/CG claims, with override authority that
  is final and locked once used.
- **ACCOUNTANT** — one or more assigned branches within their org. Verifies
  submitted entries, can override amounts (provisional — still needs FM
  approval downstream), queries or rejects with a reason. Closes Expense
  documents explicitly.
- **CASHIER** — exactly one branch. Creates Receive and Expense entries,
  adds payments against existing job cards, does daily cash closing.

A user never verifies or approves an entry they created or last modified.

## Multi-tenancy

**Approach: shared database, shared schema, `org_id` on every tenant-scoped
table.** Not database-per-tenant, not schema-per-tenant. Chosen deliberately
for simplicity: "every query filters by `org_id`, no exceptions" is one rule
that stays verifiable by inspection, which matters more at this stage than
the stronger isolation a split-database approach would buy. If a specific
customer later needs that stronger isolation, the org_id boundary already
exists everywhere, so migrating them out doesn't require a redesign.

**Enforcement, not just convention:** every repository query for a
tenant-scoped entity must filter by the authenticated user's `org_id`.
Prefer a Hibernate `@Filter` applied at the session level (set once per
request from the authenticated principal) over relying on every individual
query to remember it — a forgotten filter is the single most common
multi-tenant security bug, and this should be structurally hard to forget,
not just documented. Write a test that specifically asserts cross-org data
is unreachable, for at least the Receive/Expense document endpoints.

Super Admin's own endpoints (organization list, org onboarding) are the only
ones that intentionally query across all orgs — keep these in their own
controller/service package so it's visually obvious they're the exception.

**Document numbers are unique per-org, not globally.** `OOR-JUL26-R-021` is
only guaranteed unique within one organization — two different dealerships
may independently choose a branch code that collides. The database primary
key is a surrogate (UUID or auto-increment bigint); the human-readable
document number carries a unique constraint on `(org_id, receive_id)`
together, never on the document number alone.

## Core data model

### The clubbing principle
One "cause" = one document, containing many sub-transaction lines:
- **Receive Document** (`DAMS-Receive-ID`) ← many **Settlement Lines**
- **Expense Document** (`DAMS-Expenses-ID`) ← many **Expense Lines**

### ID scheme
- `DAMS-Receive-ID` / `DAMS-Expenses-ID`: format
  `{branchPrefix}-{MMMYY}-{R|E}-{seq}`, auto-generated server-side on
  submit, gap-free per branch per month per type. **Never manually
  editable**, by anyone, at any role.
- Line ID: `{document_id}-L{n}` — e.g. `OOR-JUL26-R-021-L1`, `-L2`. Auto
  numbered, never reused even if a line is later voided.
- **DBM ID / Job Card number**: Eicher's own external reference. Entered
  manually by the cashier. **Nullable** — advances and new customers won't
  have one yet. Never used as an internal key anywhere.
- **Job Card**: DAMS's own internal ID is the true anchor linking a
  vehicle/customer to all their Receive and Expense documents over time —
  never the DBM ID and never the vehicle number directly.
- **Vehicle number**: normalized (uppercase, no spaces), natural key for
  the Vehicle master table.

### Entities (in dependency order)
`Organization → Branch → User`, `Organization → Customer → Vehicle`,
`Organization + Branch + Customer + Vehicle → JobCard`,
`JobCard → ReceiveDocument → SettlementLine`,
`JobCard → ExpenseDocument → ExpenseLine`,
`ExpenseDocument → Receiver` (vendor/payee master, same reasoning as
Customer — a name alone isn't a safe key).

Every entity from Branch down carries `org_id`. Super Admin's own User row
has `org_id = null`.

### Closing rules — three distinct behaviors, do not conflate them
1. **Regular receipts close themselves.** No explicit close action exists.
   A receipt stays open, accepting new settlement lines, until Pending
   Amount reaches zero — status then flips to Close automatically.
   Accountant verification does **not** close a receipt.
2. **Expenses are closed explicitly by the Accountant.** Status flow: Open
   → In Progress → Awaiting Receipt → Received Receipt → Closed (or
   Transfer to Claim).
3. **Warranty / AMC / CG claims are closed explicitly by the Finance
   Manager.** FM may override the final settled amount at closing (e.g.
   accepting Eicher's partial payment as final). That override is
   permanent and must be visibly marked "Overridden · Final" everywhere
   the record is shown afterward.

### "Add Payment" behavior
Adding a payment against an existing job card **always appends a new
settlement line to the existing open Receive Document.** It must never
create a second document for the same job card. (This was a real bug we
found and fixed in the prototype — see the Cashier mockup.)

### Attachments
Every settlement line, every expense line, and the parent document itself
can have a PDF/image receipt attached. Stored in Cloudflare R2, referenced
from Postgres by object key + org_id, served via short-lived signed URLs —
never public links. Shown via a **"View Receipts" button that opens on
click — not inline thumbnails.** Frozen (no replace/delete) once the
parent document is Approved or Closed.

## Tech stack (fixed — do not substitute)
- Backend: Java 21, Spring Boot 3.x, Maven. Spring Web, Spring Data JPA,
  Spring Security (JWT, stateless), Validation, Flyway for all schema
  changes.
- DB: PostgreSQL.
- Frontend: React 18 + Vite + TypeScript, Tailwind CSS, shadcn/ui,
  lucide-react, recharts, react-router, axios.
- Documents: Cloudflare R2 via an S3-compatible SDK, behind a small
  `StorageService` interface — keep the provider swappable, don't let R2
  specifics leak into business logic.
- API docs: springdoc-openapi — auto-generated from annotations, always
  current, never hand-maintained separately.
- API base path `/api/v1`. REST naming: plural nouns, e.g.
  `POST /receipts/{id}/verify`, `/approve`, `/query`, `/reject`, `/close`.

## Debuggability & maintainability (non-negotiable)

AI-written code has a known failure mode: it works today and nobody — AI or
human — can safely change it in six months. These rules exist specifically
to prevent that, based on prior experience with exactly this problem.

- **Explicit over clever.** No dense functional one-liners where a plain
  loop reads clearly. If a reader has to pause to parse a line, rewrite it.
- **Small functions, single responsibility.** If a method's purpose needs
  "and" to describe, split it.
- **Structured logging at every state transition**, not just on errors — a
  line added, a document approved, a claim closed. Every log line carries
  `org_id`, `branch_id`, and the document/line ID involved, so one
  transaction can be traced end-to-end from logs alone.
- **Errors always name what failed** — entity, ID, org. Never a bare
  "an error occurred."
- **Every non-obvious business rule gets a comment explaining why**, not
  what — e.g. `// Receipts self-close at pending=0; accountant
  verification does NOT close them — see AGENT.md`.
- **Tests as documentation.** Test names describe the behavior being
  proven (`pendingAmount_returnsZero_whenNoInvoiceYet`), so a failing test
  is self-explanatory without reading the implementation.
- **No premature abstraction.** Don't build a generic framework for
  something used once. Add abstraction only when a second real use case
  appears.
- **One name per concept, everywhere.** Never "settlementLine" in one file
  and "paymentLine" in another for the same thing.
- **Commit messages state what changed and why**, one line, so `git log`
  reads as a real changelog.
- **Every API error response carries a request ID** that also appears in
  the matching server log line, so a report of "it broke" can be traced to
  the exact log entry without guessing.

Tests are required specifically for: pending-amount calculation, document
and line ID generation (gap-free, sequential, never reused), the override
audit trail, and claim-closing logic. Not full coverage everywhere — these,
because they're the money logic and the parts most likely to be silently
wrong.

## Local development & testing

**Test phase (current): the database is Neon (hosted Postgres), shared.**
One Neon instance is the single source of truth for local dev, integration
testing, and client review — so the client and the team all hit the same
seeded data and can switch between role accounts freely. There is no
containerised Postgres during this phase. (This overrides the earlier
"no cloud dependency for the core loop" rule; revisit once the client has
signed off and a production topology is chosen.) R2 can still be
mocked/skipped locally when credentials aren't set.

- `docker-compose.yml` at the repo root: backend + frontend only, both
  pointed at Neon via `SPRING_DATASOURCE_URL`. `docker compose up` brings
  the app up against the shared DB.
- Backend `application-local.yml` and `application-prod.yml` both point at
  Neon (separate branch/database if useful), Flyway-migrated and seeded
  with the same sample data as the HTML mockups (same customer names, same
  job cards) plus a full dummy dealership and one login per role.
- README at repo root: exact steps to run the app, run backend tests
  (`mvn test`), run frontend tests, and re-seed / reset the Neon dev branch.

## Docker & CI/CD

- Backend `Dockerfile`: multi-stage — Maven build stage, then a slim JRE
  runtime stage. Never ship the build toolchain in the runtime image.
- Frontend `Dockerfile`: Node build stage, then served via nginx.
- GitHub Actions:
  - On every PR: build + run backend tests + run frontend tests. Merge
    blocked if either fails.
  - On merge to main: build both Docker images, push to GitHub Container
    Registry (free, simplest starting point).
  - Deploy step: pull the freshly-built images and run them — kept
    provider-agnostic (no cloud-specific CLI baked into the workflow) so
    the actual host can be decided or changed later without rewriting
    the pipeline.

## Working process for this agent

The system is early-stage and will change significantly once real cashiers
use it. Before writing code for a new stage or feature: propose the plan in
plain language first — entities touched, migrations, endpoints, screens —
and wait for confirmation before implementing. Don't treat a prior stage's
implementation as untouchable if new requirements mean it should change;
flag the conflict and ask rather than silently working around it.

## Decisions locked after plan review (these override anything above if in conflict)

1. **Cash page (per branch, per day)** — one dedicated cashier screen,
   separate from Receipt/Expense entry:
   - Records internal cash movements: **In from Bank / Out to Bank**.
     Fields: direction, date, amount, bank name, reference no., remark.
     No customer/vehicle/job-card fields — this is internal money movement,
     not a customer document.
   - Own document series: `{branch}-{MMMYY}-C-{seq}` (e.g.
     `OOR-JUL26-C-005`). Single-amount documents, no sub-lines.
   - Same maker-checker workflow (Submitted → Verified → Approved) as
     every other document.
   - The page always shows today's live computed drawer position:
     `Opening + cash-mode receipts + Cash In − cash-mode expenses − Cash
     Out`. Opening = previous day's approved closing; the first-ever
     opening for a branch is set by the Accountant.
   - End-of-day **Close Cash** on the same page: cashier enters physically
     counted cash, variance auto-computed, remark mandatory if variance
     ≠ 0, closing locks that date against new cash entries.
   - The old "Cash Deposit" / "Cash Out" expense sub-categories are
     **removed** — fully replaced by this page.
   - Cash In/Out documents are **excluded from Collections and Expenses
     KPIs everywhere** (owner dashboard, branch comparison) — they only
     affect drawer math, never income/cost figures.
2. **Multi-branch cashier access** — an org-level setting in Owner
   masters, **default OFF**. OFF: a cashier searches, sees, and posts
   against only their own branch's customers and job cards. ON:
   org-wide. (FM/Owner are always org-wide; Accountant follows assigned
   branches — unchanged.)
3. **Full masters management in the Owner dashboard** — CRUD (deactivate,
   never delete) for: receipt categories, expense categories and
   sub-categories, settlement/payment modes, expense limits, business
   status lists, bank list, receivers/vendors. Every dropdown the app
   shows comes from these tables, never from hard-coded lists.
4. **Org-wide Override Audit view** (Owner + FM): one screen listing every
   amount override across the organization — who, when, original → new
   value, reason, document/line ID — filterable by user, branch, and date.
5. **No Tally export or ledger report in v1.** Tally references elsewhere
   in this file are historical context only.
6. **Post-approval reversal/correction: deferred beyond v1.** V1 relies on
   the maker-checker flow catching errors before approval.
7. **Universal search for every role**, results always scoped by that
   user's branch access (and the cashier toggle in #2).
8. **Queried-entry fix-and-resubmit loop (required v1)** — the cashier
   home includes a "My Entries" list (today + recent) with workflow
   badges. Queried items are visibly highlighted, open in edit mode, and
   can be corrected and **Resubmitted**. The same append-while-open
   principle applies to Expense documents: lines can be added until the
   Accountant closes them.

## UI reference

Three HTML mockups are the source of truth for layout, flow, field names,
and interaction — build the real frontend to match these closely, not as
loose inspiration:
- **`cashier-home.html`** — Cashier's full experience: universal search,
  customer history with Add Payment (appends a line, confirmed behavior),
  Receive Entry, Expense Entry, "View Receipts."
- **`review-close.html`** — Accountant and Finance Manager: review queue,
  verify/override/query/reject, claim closing with final override, the
  overview panel shown when nothing's selected.
- **`owner-dashboard.html`** — Owner's dashboard (branch comparison,
  drill-downs, outstanding claims) plus Team & Branches admin (add branch,
  add user, role-conditional branch assignment).

**Important — read this before building auth.** `review-close.html`
contains a Role toggle (Accountant / Finance Manager), and both
`cashier-home.html` and `owner-dashboard.html` load directly into a
hardcoded logged-in persona with no login screen at all. **These are demo
conveniences only** — built that way so one file could demo both sides of
a workflow without needing two separate logins. They are not a feature to
replicate. In the real system:
- There is no role-switch toggle anywhere. A logged-in Accountant sees
  only the Accountant view; a Finance Manager only theirs. One person
  wanting both views needs two separate accounts.
- Nobody lands on any screen without authenticating first. A standard
  login screen (email + password, no mockup needed for this — build it
  straightforwardly) is the only unauthenticated route. Every other screen
  requires a valid session and renders based on the JWT's role and
  org_id, never a client-side choice.

**Session persistence is deliberately minimal during the test phase.** A
single short-lived access JWT (~8h), held in `sessionStorage`, no refresh
token, no "remember me", no persistence across a browser restart — because
testers switch between the seeded role accounts constantly and a sticky
session gets in the way. `POST /auth/refresh`, refresh-token rotation, and
"stay logged in" are a dedicated hardening stage **after** client sign-off,
not v1. The shell shows a visible account menu (name, role, branch, Logout).

**Seeded test accounts.** For the test phase, Flyway seeds the Super Admin
plus one full dummy dealership — an organization, its branches, and one
user per role (Owner, Finance Manager, Accountant, Cashier) with known
passwords — so anyone can log in and exercise every role immediately. The
email/invite onboarding flow below is still built and is still the only
path for real organizations; the seed is a testing convenience that a
production seed profile will omit.

A Super Admin panel (organization list, onboard new org + first Owner) does
not have a mockup yet — build it in the same visual language as these
three once the core is working, or ask for a mockup first.

**In-app help.** Every role's shell has a Help button opening a role-scoped
help section: short, task-oriented, step-by-step articles written for
dealership staff, not developers — minimal prose, numbered steps, one
screenshot or small diagram per step where it helps. Each role sees only
its own articles (Cashier, Accountant, Finance Manager, Owner, Super Admin).
Individual screens carry a contextual "?" that deep-links to the relevant
article. Content is authored and versioned with the frontend (bundled
Markdown), never stored in the database.

## Deployment & onboarding (confirmed)

- **Deployment: Docker container.** Both Docker images (backend, frontend)
  from the CI/CD pipeline above are what actually ships — no target-specific
  build steps beyond that. Host is not pinned to a specific cloud provider;
  keep the GitHub Actions deploy step provider-agnostic (push image, then a
  generic "pull and run" step) so where it runs can change later without
  rewriting the pipeline.
- **Onboarding: email/invite flow.** When Super Admin creates a new
  organization, the first Owner is invited by email, not handed a temp
  password directly. They set their own password via the invite link on
  first login. This is the path for every real organization. The one
  exception is the seeded dummy dealership used for testing (see "Local
  development & testing") — a production seed profile will not create it.

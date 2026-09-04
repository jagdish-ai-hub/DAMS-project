# AGENT.md — DAMS (Dealer Activity Management System)

This file is the spec for any AI coding agent working on this project
(Claude Code, or others). Read it fully before writing code. If a rule here
conflicts with something convenient to build, this file wins — update this
file first if the rule itself needs to change, then build.

> **How to read this file (repo-grounded).** Every abstract rule below is
> followed by a short `Where this lives in this repo (verified)` block with
> real file paths, route prefixes, and table names taken from the current
> checkout. Those blocks are grounding aids only — the rule text above each
> one is authoritative. `plan.md` exists in the repo root but is explicitly
> **out of scope** per the owner's instruction (`ok go (dont use plan.md)`):
> do not read it, do not cite it, do not treat it as a source of truth. The
> sources of truth are, in order: (1) this file, (2) `intial ui prototypes/*.html`
> (note the literal folder spelling `intial`), (3) the code itself.
> Folder names below are literal (`backend/src/main/java/com/dams/...`,
> `frontend/src/...`, `backend/src/main/resources/db/migration/V*.sql`).

## Verification & change discipline (non-negotiable)

Treat this codebase as production at all times — even during the test
phase, because the shared Neon database holds the client's walkthrough
data and the team's review data. AI-written changes have a failure mode
beyond unreadable code: confidently wrong claims and "safe" edits that
silently break working flows. These rules exist to prevent that. They
apply in every stage, to every task, no matter how small it looks.

### Zero-prediction rule
- Never predict, assume, or guess what code does. If you haven't read it,
  you don't know. "It probably works like X" is forbidden.
- Every claim — a bug, an error, what a file does, whether a change is
  safe — must be backed by: file path + line number + the actual quoted
  code. No exceptions.
- Trace both sides before asserting behavior: a frontend claim requires
  reading the component AND its API call AND the backend route; a backend
  claim requires reading the controller AND service AND repository.
- Before claiming any UI element is wrong or missing, check the three
  HTML mockups — they are the source of truth for layout, flow, and field
  names, not loose inspiration.
- Before claiming any behavior is a bug, re-read this file first. Many
  things that look wrong are locked decisions (receipts self-close at
  pending = 0, Add Payment appends a line, the cashier multi-branch
  toggle, no Tally export in v1, no post-approval reversal in v1).
  **The spec is not the bug.**
- If something genuinely cannot be verified after tracing, label it
  `UNVERIFIED — needs manual check by a human` and stop there. Never
  present it as fact.

### Double-verification before ANY edit
Two full passes, both mandatory, for every edit — styling, one-line fixes,
migrations, everything.

**Pass 1 — before deciding to edit:**
1. Read the target file completely, top to bottom.
2. Search the whole codebase for every usage of what you're about to
   change — imports, callers, endpoint consumers, tests, Flyway
   migrations.
3. Confirm the problem is real and evidenced, not assumed.
4. Confirm the intended edit won't alter logic, data flow, API contracts,
   validation, or any invariant in this file.

**Pass 2 — immediately before writing:**
1. Re-read the exact lines to be changed plus the surrounding context.
2. List the blast radius: every file and behavior the edit touches.
3. Confirm nothing depends on the exact current behavior — string values,
   JSON shapes, status names, ID formats, log line formats.
4. State explicitly in your response: what you ARE changing and what you
   are NOT changing.

If either pass fails or is uncertain → stop. Read more code, or ask the
user. Do not edit.

### Double-verification before calling anything a bug or error
A bug report requires all four, verified twice:
1. Exact file + line, with the quoted code.
2. A traced explanation of why it fails — including the input or state
   that triggers it.
3. Confirmation it is not a locked decision in this file or intended
   mockup behavior.
4. Confirmation you've read the full surrounding flow, not just the one
   suspicious line.

Never report "this might be a bug" as a bug. Speculative findings are
questions to ask the user, not findings.

### Approval gates — ask before proceeding
When the user asks for a new plan, a new set of features, or a batch of
fixes, ALWAYS stop and present the following before touching any file:
1. What I found (with file/line evidence)
2. Exact list of files I intend to change
3. What each change will and will NOT touch
4. Risks and how I'll mitigate them

Then STOP and wait for explicit approval. Never begin implementing a plan,
feature set, or fix batch in the same turn it was requested. Any change
that touches routes, API contracts, database schema, authentication, or
shared components used in 2+ places requires this gate even if the user
seems to have already approved the general idea — confirm the specifics.

### Post-patch full reverification (mandatory before reporting "done")
After ALL patches are applied, and before saying the work is complete:

1. **Re-read every changed file**, fully, top to bottom.
2. **Re-trace every usage** of everything touched — imports, callers,
   endpoint contracts, tests, migrations.
3. **Walk the main flows by reading code, never by assuming:**
   - login → JWT role/org_id → route guard → correct role-scoped view
     (no role toggle, no unauthenticated screens)
   - Receive entry → settlement lines → pending amount → self-close at 0
     (accountant verification does NOT close receipts)
   - Add Payment → appends a settlement line to the existing open
     Receive document, never a second document
   - Expense flow → explicit close by Accountant; claim flow → close +
     override by FM, override marked "Overridden · Final"
   - Cash page → drawer math formula intact; Cash In/Out excluded from
     Collections/Expenses KPIs everywhere
   - Queried entries → fix-and-resubmit loop unchanged
4. **Multi-tenancy re-check:** every tenant-scoped query you touched
   still filters by `org_id`; the cross-org isolation test still passes.
5. **Maker-checker re-check:** no path allows a user to verify or approve
   an entry they created or last modified.
6. **ID re-check:** document/line ID generation remains gap-free,
   sequential, never reused; no applied Flyway migration was edited
   (new migrations only — editing an applied migration breaks checksums).
7. **Run the tests** (`mvn test`, frontend tests) and review the complete
   `git diff` — every hunk must belong to the task, nothing else.
8. **Report in this format:** files changed → what changed → why → what
   was explicitly preserved; any regressions found during reverification
   and how they were resolved; anything still labeled `UNVERIFIED`.

If reverification finds a regression: fix it, then start the entire
reverification pass again from step 1. "Done" is only reportable after a
clean full pass.

### Shared-database safety
The Neon instance is a single shared source of truth for the whole team
and the client. Never run destructive operations against it — DROP,
TRUNCATE, mass DELETE, manual schema edits, `flyway repair` — without
explicit user approval in the current session. Re-seeding wipes everyone's
data: ask first, every time.

### Communication format
- Always label: **fact** (code-verified) vs **question** (needs user
  input) vs `UNVERIFIED`.
- Every finding carries file path + line number + quoted snippet.
- On large tasks, report reading progress ("X of Y files read").
- If asked to do something that conflicts with these rules, say so and
  ask before proceeding.

Where this lives in this repo (verified):
- Backend tests run from `backend/` with `mvn -B verify` (CI) or `mvn test`
  locally; frontend tests run from `frontend/` with `npm test` (vitest) —
  see `.github/workflows/ci.yml`, `backend/pom.xml`, `frontend/package.json`.
- Never run destructive SQL against Neon: connection comes only from the
  `SPRING_DATASOURCE_URL` env var — see `backend/src/main/resources/application.yml`
  and `docker-compose.yml`. There is no local Postgres container in this phase.

---

## What this is

DAMS replaces Excel-based service-station bookkeeping for truck dealerships
— receipts, expenses, cash tracking, warranty/AMC claims — with a small,
correct, auditable system. It was built for one dealership group first, but
is architected as a **multi-tenant SaaS product** from day one, so it can be
sold to other dealership groups without a redesign.

Tally remains the accounting system of record. DAMS is not accounting
software — it's the clean, verified, ledger-tagged pipe that feeds
accountants, plus the owner's live window into branch operations.

Where this lives in this repo (verified):
- Backend entry: `backend/src/main/java/com/dams/DamsApplication.java`
  (package `com.dams`). Domain packages directly under it: `admin`,
  `attachment`, `audit`, `auth`, `branch`, `cash`, `common`, `config`,
  `customer`, `dashboard`, `email`, `expense`, `jobcard`, `masters`,
  `myentries`, `organization`, `receive`, `receiver`, `review`, `search`,
  `user`, `vehicle`.
- API base path for every controller below is `/api/v1` (see "Backend API
  map" near the end of this file). Swagger UI is served at
  `/swagger-ui.html`, OpenAPI JSON at `/api-docs` (see
  `backend/src/main/java/com/dams/config/OpenApiConfig.java`,
  `backend/src/main/resources/application.yml` `springdoc` block).

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

Where this lives in this repo (verified):
- Role enum: `backend/src/main/java/com/dams/user/entity/Role.java`
  (`SUPER_ADMIN / OWNER / FINANCE_MANAGER / ACCOUNTANT / CASHIER`).
- Enforcement is method/URL level: `ReviewController` javadoc + `ReviewGuard`
  (`backend/src/main/java/com/dams/review/service/ReviewGuard.java`) —
  caller must hold the right role, document must be in a branch the caller
  can see (`com.dams.common.security.BranchScope`), and maker-checker
  refuses self-review on created/last-modified. Note the deliberate nuance
  documented in `ReviewGuard`: an accountant's own amount override does NOT
  bump last-modified, so the same accountant may override a line and still
  verify that document (matches `review-close.html`).
- Frontend landing per JWT role: `frontend/src/shell/AppShell.tsx`
  `homeElement` (~lines 79–89) — SUPER_ADMIN → `/app/organizations`,
  CASHIER → `CashierHomePage`, ACCOUNTANT → `ReviewQueuePage`,
  FINANCE_MANAGER → `FmQueuePage`, OWNER → `DashboardPage`. Nav per role in
  `NAV_BY_ROLE` (~lines 28–55). There is no role toggle anywhere.
- Seeded logins for testing (see "Seeded test accounts" below):
  `dams@jjsoftware.com` (Super Admin), `owner@jjmotors.demo`,
  `finance@jjmotors.demo`, `accountant@jjmotors.demo`,
  `cashier@jjmotors.demo` (home branch Rayagada/OOR).

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

Where this lives in this repo (verified):
- Request wiring (order matters):
  `backend/src/main/java/com/dams/config/SecurityConfig.java` places
  `JwtConfig` before `UsernamePasswordAuthenticationFilter` and
  `TenantFilter` immediately after `JwtConfig`.
- `backend/src/main/java/com/dams/config/TenantFilter.java` — reads parsed
  JWT claims from `JwtConfig`, skips `SUPER_ADMIN`, otherwise stores
  `org_id` in `backend/src/main/java/com/dams/config/TenantContext.java`
  (request-scoped ThreadLocal, always cleared in `finally`).
- `backend/src/main/java/com/dams/config/TenantFilterActivator.java` —
  enables the Hibernate `orgFilter` on the session for every repository call.
- Branch scoping helper: `backend/src/main/java/com/dams/common/security/BranchScope.java`
  (accountant's assigned branches; FM/Owner org-wide).
- The cross-org exception lives alone in `com.dams.admin`
  (`backend/src/main/java/com/dams/admin/controller/AdminOrgController.java`,
  base `/api/v1/admin/organizations`).
- Tests that must keep passing:
  `backend/src/test/java/com/dams/tenant/CrossOrgIsolationTest.java`
  (Testcontainers Postgres — needs Docker locally, always runs in CI) and
  `backend/src/test/java/com/dams/config/TenantFilterTest.java`.
- Branch codes are unique per org, not globally:
  `V2__branch_and_document_sequence.sql` (`branch_org_code_uq UNIQUE (org_id, code)`,
  `code` 2–5 chars).

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

Where this lives in this repo (verified):
- Migrations (in order, `backend/src/main/resources/db/migration/`):
  `V1__create_org_user_tables.sql` (org, `app_user` incl. `org_id NULL` Super
  Admin seed + `user_branch_access`), `V2__branch_and_document_sequence.sql`
  (`branch`, gap-free `document_sequence` bumped under `SELECT ... FOR UPDATE`
  inside the submit transaction), `V3__configurable_masters.sql` (8 master
  tables), `V4__receiver.sql`, `V5__seed_demo_dealership.sql` (demo org +
  branches + masters + logins), `V6__customer_and_vehicle.sql`,
  `V7__audit_event.sql`, `V8__job_card.sql`,
  `V9__seed_customers_vehicles_job_cards.sql`, `V10__job_card_b2b_gst.sql`,
  `V11__receive_document_and_attachments.sql`, `V12__seed_receive_documents.sql`,
  `V13__expense_master_flags.sql`, `V14__expense_document_and_lines.sql`,
  `V15__seed_expense_documents.sql`, `V16__cash_mode_flags.sql`,
  `V17__cash_document_and_day_close.sql`, `V18__seed_cash_documents.sql`,
  `V19__audit_event_branch_id.sql`. Never edit an applied migration — new
  migrations only (Flyway checksums).
- Entities: `com.dams.receive.entity.ReceiveDocument` /
  `SettlementLine`, `com.dams.expense.entity.ExpenseDocument` /
  `ExpenseLine`, `com.dams.jobcard.entity.*`, `com.dams.customer.entity.*`,
  `com.dams.vehicle.entity.Vehicle`, `com.dams.receiver.entity.*`,
  `com.dams.branch.entity.*`, `com.dams.organization.entity.*`,
  `com.dams.user.entity.AppUser`, `com.dams.cash.entity.*`
  (`CashDocument`, `CashDayClose`, `BranchCashOpening`), `com.dams.audit.entity.AuditEvent`
  (+ `EventType`, `ActorType`).
- Numbering/sequence logic + tests:
  `backend/src/test/java/com/dams/branch/DocumentNumberServiceTest.java`,
  receive/expense service tests; line numbers `{doc}-L{n}` never reused.
- Money-logic tests that must keep passing:
  `.../jobcard/PendingAmountCalculatorTest.java`,
  `.../jobcard/ClaimCloseServiceTest.java`,
  `.../receive/ReceiveDocumentServiceTest.java`,
  `.../expense/ExpenseDocumentServiceTest.java`,
  `.../cash/DrawerServiceTest.java`, `.../cash/CashCloseServiceTest.java`,
  `.../cash/CashDocumentServiceTest.java`,
  `.../audit/OverrideAuditServiceTest.java`.

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

Where this lives in this repo (verified):
- Workflow enums (review lifecycle — distinct from business status and from
  the computed settled flag; never derive one from the other):
  `com.dams.receive.entity.WorkflowStatus`
  (`DRAFT, SUBMITTED, VERIFIED, APPROVED, QUERIED, REJECTED`) and
  `com.dams.expense.entity.ExpenseWorkflowStatus` (same + `CLOSED`).
- Receipt auto-settle at pending 0: `com.dams.receive.service.ReceiveDocumentService`
  (+ `ReceivePaymentGuard`); expense close: `POST /expenses/{id}/close`
  (Accountant) in `com.dams.expense.service.ExpenseDocumentService`
  (+ `ExpensePostingGuard` — a closed expense accepts no more lines);
  claim close: `POST /job-cards/{id}/close-claim` (FM only, final override).
- Review actions: `com.dams.review.controller.ReviewController`
  (`POST /receipts/{id}/verify|query|reject`, `/lines/{lineNo}/override`,
  `/approve`; same family for `/expenses/{id}/...` + `/close`; cash
  `/cash-documents/{id}/verify|approve|query|reject`). Queues:
  `GET /review/receipts|expenses|cash` (Accountant) and
  `GET /review/fm/receipts|expenses|cash` (FM). Logic in
  `com.dams.review.service.ReviewService` + `ReviewGuard`.
- Frontend: `frontend/src/accountant/ReviewQueuePage.tsx`,
  `frontend/src/finance/FmQueuePage.tsx`, shared bits in
  `frontend/src/review/reviewShared.tsx`.

### "Add Payment" behavior
Adding a payment against an existing job card **always appends a new
settlement line to the existing open Receive Document.** It must never
create a second document for the same job card. (This was a real bug we
found and fixed in the prototype — see the Cashier mockup.)

Where this lives in this repo (verified):
- Backend: `POST /api/v1/receipts/{id}/lines` (Add Payment) in
  `ReceiveDocumentController`; one-open-document-per-job-card enforced in
  service + `ReceivePaymentGuard` (+ `ReceivePaymentGuardTest`).
  A cashier can only touch job cards in their own home branch — cross-branch
  Add Payment is refused even when org-wide search is on.
- Frontend: `frontend/src/cashier/AddPaymentModal.tsx` (appends a line),
  customer history card on `CashierHomePage.tsx`.

### Attachments
Every settlement line, every expense line, and the parent document itself
can have a PDF/image receipt attached. Stored in Cloudflare R2, referenced
from Postgres by object key + org_id, served via short-lived signed URLs —
never public links. Shown via a **"View Receipts" button that opens on
click — not inline thumbnails.** Frozen (no replace/delete) once the
parent document is Approved or Closed.

Where this lives in this repo (verified):
- Backend: `com.dams.attachment.storage.StorageService` (provider-swappable
  interface), `LocalFilesystemStorageService` (dev default — serves via signed
  URLs from `GET /api/v1/attachments/raw`), `R2StorageService` (active only
  when `dams.storage.provider=r2`), `SignedUrlTokens`. Upload/list routes on
  the receipt/expense controllers (`POST|GET .../{id}/attachments`,
  `.../lines/{lineNo}/attachments`); `AttachmentController`
  (`/api/v1/attachments/{id}`, `/raw`, `DELETE`). `attachment.frozen=true`
  once the parent settles/approves/closes — frozen rows reject
  replace/delete (see `AttachmentServiceTest`).
- Upload limits: `application.yml` servlet multipart `max-file-size: 10MB`,
  `max-request-size: 12MB`.
- Frontend: `frontend/src/cashier/AttachmentsPanel.tsx`,
  `frontend/src/cashier/ViewReceiptsModal.tsx` (button-opens-modal, never
  inline thumbnails).

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

Where this lives in this repo (verified — pinned versions):
- `backend/pom.xml`: `spring-boot-starter-parent 3.5.16`, `java.version 21`,
  `jjwt 0.13.0`, `springdoc-openapi-starter-webmvc-ui 2.9.0`,
  `aws s3 + url-connection-client 2.29.0` (Netty/Apache clients excluded),
  `flyway-core + flyway-database-postgresql`, `postgresql` (runtime),
  `spring-boot-starter-aop` (tenant filter), `spring-boot-starter-actuator`
  (`/actuator/health`), `H2 + testcontainers-postgresql + spring-boot-testcontainers`
  (tests). Compiler: Lombok `1.18.46` on the annotation-processor path,
  UTF-8 forced.
- `frontend/package.json`: `react 18.3.1`, `react-dom 18.3.1`,
  `react-router-dom 7.x`, `axios 1.x`, `tailwindcss 3.4.x`,
  `lucide-react 1.37.0`, `recharts 3.x`, `jwt-decode 4`, `react-markdown 10`
  + `remark-gfm 4` (help articles), `vite 5`, `typescript 5`, `vitest 3`,
  `eslint 9`. Scripts: `dev` (vite), `build` (`tsc && vite build`),
  `lint` (`eslint src`), `test` / `test:watch` (vitest).
- No library/version substitution without asking (see also `CLAUDE.md`
  "Stack is fixed").

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

Where this lives in this repo (verified):
- Logging pattern: `application.yml`
  `logging.pattern.console: "%d{HH:mm:ss} [%X{requestId}] [org:%X{orgId}] %-5level %logger{36} - %msg%n"`.
  Request IDs: `com.dams.common.filter.RequestIdFilter` (MDC + `X-Request-ID`
  response header); error shape: `com.dams.common.dto.ApiError`
  (`requestId, status, error, message`) via
  `com.dams.common.exception.GlobalExceptionHandler`; auth failures in
  `SecurityConfig` write the same shape. Frontend surfaces it:
  `frontend/src/api/axios.ts` copies `x-request-id` onto
  `error.response.data._requestId`.
- JPA: `ddl-auto: validate` (schema comes only from Flyway), `open-in-view: false`,
  batch fetching tuned for Neon (`default_batch_fetch_size: 64`, jdbc `batch_size: 32`).
- Vocabulary (one name per concept): ReceiveDocument / SettlementLine /
  ExpenseDocument / ExpenseLine / CashDocument / CashDayClose /
  BranchCashOpening / JobCard / Receiver / masters via `MasterType` slugs.
  Never rename one side only.

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

Where this lives in this repo (verified):
- `docker-compose.yml`: `backend` (build `./backend`, `8080:8080`,
  `SPRING_PROFILES_ACTIVE: prod`, Neon URL/user/pass + `JWT_SECRET` +
  `APP_BASE_URL`/`CORS_ALLOWED_ORIGINS` from env, healthcheck
  `wget ... /actuator/health`) + `frontend` (build `./frontend`,
  `VITE_API_URL` build-arg default `http://localhost:8080`, `5173:80`,
  `depends_on: backend`). Swagger at `http://localhost:8080/swagger-ui.html`.
- Profiles: `application.yml` (shared defaults — Neon via env, Hikari fixed
  `maximum-pool-size: 8 / minimum-idle: 8`, `keepalive-time: 60s`,
  `max-lifetime: 25min`, `connection-timeout: 15s`; JWT default
  `dams.jwt.access-token-expiry-hours: 8`; storage default `local`),
  `application-local.yml` (CORS `5173 + 2314` for `dams.bat`/`npm run dev`,
  JWT `720h` local convenience only), `application-prod.yml` (all secrets
  from env).
- Env template: `.env.example` → copy to `.env` (never commit the real Neon
  URL). Local storage dir: `DAMS_STORAGE_LOCAL_DIR` (default OS tmp).
- Commands: backend from `backend/` — `mvn test` / `mvn -B verify`
  (Testcontainers isolation test needs Docker); frontend from `frontend/` —
  `npm ci`, `npm run dev`, `npm run lint`, `npm run build`, `npm test`.
  Docs: `README.md` (run/test/seed), `docs/deployment-guide.md`,
  helper scripts in `scripts/`, Windows helper `dams.bat`.

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

Where this lives in this repo (verified):
- `backend/Dockerfile` (+ `.dockerignore`), `backend/pom.xml` build
  (compiler Lombok processor path, `spring-boot-maven-plugin` excluding Lombok).
- `frontend/Dockerfile` (+ `.dockerignore`), `frontend/nginx.conf`
  (serves the Vite build; `VITE_API_URL` baked at build time),
  `frontend/vite.config.ts`, `frontend/tailwind.config.ts`.
- `.github/workflows/ci.yml`: `backend` job (`mvn -B verify` incl.
  Testcontainers), `frontend` job (`npm ci`, `lint`, `build`, `test`),
  `images` job (GHCR `ghcr.io/<repo>-backend|frontend:sha-<7>|latest`,
  GHA layer cache, frontend `VITE_API_URL` build-arg), `deploy` job (ships
  `compose.prod.yml` over SSH, pull → health-check
  (`:8082/actuator/health` + `:8083/`) → auto-rollback to previous tag).
  Concurrency: one main-branch pipeline at a time, deploys never cancelled
  mid-run. `compose.prod.yml` is the production compose file.

## Working process for this agent

The system is early-stage and will change significantly once real cashiers
use it. Before writing code for a new stage or feature: propose the plan in
plain language first — entities touched, migrations, endpoints, screens —
and wait for confirmation before implementing. Don't treat a prior stage's
implementation as untouchable if new requirements mean it should change;
flag the conflict and ask rather than silently working around it.

Every plan, feature set, or fix batch goes through the **approval gate**
defined in "Verification & change discipline" above: present findings with
evidence, list the exact files, state what will and won't be touched, then
stop and wait. Never start implementing in the same turn the work was
requested. And before reporting any work as complete, run the **post-patch
full reverification** — also defined above — every time, without being
asked.

Where this lives in this repo (verified):
- `CLAUDE.md` (repo root) restates this process plus "Answer first, then
  work" (answer the question up front in plain words before running tools,
  then state what you are about to do in one line) and "Stack is fixed".

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

Where this lives in this repo (verified — do not re-litigate; the spec above wins):
- Cash: `com.dams.cash.controller.CashDocumentController`
  (`/api/v1/cash-documents`: create/list/get/patch/submit/resubmit/delete) +
  `com.dams.cash.controller.CashController` (`/api/v1/cash/drawer`,
  `/opening` first-ever opening by Accountant, `/close-day` GET+POST with
  variance + mandatory remark + `CashDateLock`). Math lives ONLY in
  `com.dams.cash.service.DrawerService#position`:
  `opening + cash-mode receipts (settlement lines whose mode has is_cash) +
  cash IN − cash-mode expenses (expense lines whose mode has is_cash) − cash OUT`,
  one branch + one date (Asia/Kolkata boundary passed by caller); counts every
  contributing document except `DRAFT`/`REJECTED` (money in the drawer the
  moment it changes hands), never review state. Guards: `CashPostingGuard`
  (+ tests `DrawerServiceTest`, `CashCloseServiceTest`, `CashDocumentServiceTest`).
  Frontend: `frontend/src/cashier/CashPage.tsx`, API `frontend/src/api/cash.ts`.
- Toggle: `organization.multi_branch_cashier_access` (default `FALSE` —
  see `V1` + seed `JJ Motors (Demo), FALSE`), read via
  `GET|PATCH /api/v1/organization` (`OrgSettingsController`, Owner writes),
  enforced in `BranchScope` + `SearchService` + receive/expense posting guards.
  Accountant scoping: `user_branch_access` rows (seed: accountant → OOB+OOR).
- Masters: `GET|POST /api/v1/masters/{type}`, `GET|PATCH /api/v1/masters/{type}/{id}`
  (`MastersController`; reads any authenticated org user, writes `OWNER`-only,
  deactivate-never-delete). Slugs in `com.dams.masters.MasterType`:
  `receive-categories`, `receive-statuses`, `settlement-modes`,
  `expense-categories`, `expense-sub-categories` (`?expenseCategoryId=` filter),
  `expense-modes`, `expense-statuses`, `banks`. Flags: receive-category
  `hasClaimFlag`, modes `requires_bank/requires_ref` (+ `is_cash` via V16),
  expense-status `triggers_claim`. Expense limits live with categories.
  Receivers/vendors: `/api/v1/receivers` (same deactivate rule).
  Frontend: `frontend/src/owner/MastersPage.tsx`, API `frontend/src/api/masters.ts`
  (+ `receivers.ts`, `orgSettings.ts`). Test: `MastersServicePurgeTest`
  (no hard delete path).
- Override audit: `GET /api/v1/override-audit`
  (`OverrideAuditController`, Owner+FM), service `OverrideAuditService`,
  query filters user/branch/date; trail columns + `OVERRIDE` audit rows.
  Frontend: `frontend/src/overrideaudit/OverrideAuditPage.tsx`,
  API `frontend/src/api/overrideAudit.ts`. "Overridden · Final" marking is
  required on FM claim-close overrides everywhere the record renders.
- Search: `GET /api/v1/search?q=` (`SearchController`/`SearchService` —
  name/phone/vehicle/job-card/invoice; job-card side branch-scoped via
  `BranchScope`). Frontend: `frontend/src/shared/GlobalSearch.tsx`,
  API `frontend/src/api/search.ts`.
- My Entries: `GET /api/v1/my-entries` (`MyEntriesController` — own entries,
  today + recent, queried highlighted). Resubmit: `POST /receipts/{id}/resubmit`,
  `POST /expenses/{id}/resubmit`, `POST /cash-documents/{id}/resubmit`
  (QUERIED → SUBMITTED). Expense append-while-open: `POST /expenses/{id}/lines`
  until Accountant closes. Frontend: `frontend/src/cashier/MyEntriesPage.tsx`,
  API `frontend/src/api/myEntries.ts`.
- Dashboard KPIs exclude Cash In/Out: `GET /api/v1/dashboard/summary|outstanding|activity`
  (`DashboardController`/`DashboardService` + DTOs `DashboardSummary`,
  `DashboardKpis`, `BranchComparisonRow`, `OutstandingItem`, `ActivityItem`,
  `TrendPoint`, `NamedAmount`). Frontend: `frontend/src/owner/DashboardPage.tsx`,
  API `frontend/src/api/dashboard.ts`.
- No Tally export, no post-approval reversal in v1 — do not build either.
  Expense `transfer-to-claim`: `POST /expenses/{id}/transfer-to-claim`
  (Transfer to Claim path, not a reversal).

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

Where this lives in this repo (verified):
- Mockups: `intial ui prototypes/cashier-home.html`,
  `review-close.html`, `owner-dashboard.html` (+ early non-authoritative
  `dams-er-diagram-v2.mermaid` — where it disagrees with this file, this
  file wins).
- Auth screens/routes: `frontend/src/App.tsx` — public `/login`
  (`auth/LoginPage.tsx`), `/accept-invite` (`auth/AcceptInvitePage.tsx`),
  everything else under `/app/*` behind `auth/ProtectedRoute.tsx` +
  `shell/AppShell.tsx`; `/` → `/app`, unknown → `/login`.
- Auth state: `frontend/src/auth/AuthContext.tsx` (+ `useAuth.ts`),
  `frontend/src/auth/session.ts` (`dams_access_token`, `dams_user_name` in
  `sessionStorage`; `clearSession()`), `frontend/src/api/axios.ts`
  (`VITE_API_URL` default `http://localhost:8080`, Bearer attach, 401 →
  clear + `/login?expired=1`, `X-Request-ID` surfacing), backend
  `com.dams.auth.controller.AuthController`
  (`POST /api/v1/auth/login|accept-invite|change-password`),
  `com.dams.auth.util.JwtUtil`, `com.dams.config.JwtConfig`. No
  `POST /auth/refresh` in v1 — do not add it. Shell account menu:
  `frontend/src/shell/AccountMenu.tsx` (name, role, branch, Logout);
  role landing helper: `frontend/src/shell/RoleLanding.tsx`.
- Role screens (exact files): cashier `cashier/CashierHomePage.tsx`,
  `NewReceiptPage.tsx`, `NewExpensePage.tsx`, `CashPage.tsx`,
  `MyEntriesPage.tsx`, `AddPaymentModal.tsx`, `AttachmentsPanel.tsx`,
  `ViewReceiptsModal.tsx`; accountant `accountant/ReviewQueuePage.tsx`;
  FM `finance/FmQueuePage.tsx`; shared `review/reviewShared.tsx`;
  owner `owner/DashboardPage.tsx`, `owner/TeamAndBranchesPage.tsx`,
  `owner/MastersPage.tsx`; override audit `overrideaudit/OverrideAuditPage.tsx`;
  superadmin `superadmin/OrganizationsPage.tsx`; settings `settings/SettingsPage.tsx`
  (change-password + Owner org settings); shell `shell/AppShell.tsx`,
  `shell/ui.tsx`; search `shared/GlobalSearch.tsx`.
- API clients mirror the backend map: `frontend/src/api/`
  `auth.ts`, `admin.ts`, `branches.ts`, `users.ts`, `masters.ts`,
  `receivers.ts`, `orgSettings.ts`, `customers.ts`, `vehicles.ts`,
  `jobCards.ts`, `receipts.ts`, `expenses.ts`, `cash.ts`, `review.ts`,
  `search.ts`, `myEntries.ts`, `dashboard.ts`, `overrideAudit.ts`,
  `axios.ts`.
- `AppShell` nav/routes (verified): `NAV_BY_ROLE` — SUPER_ADMIN
  (`/app/organizations`, `/app/settings`), OWNER (`/app` Dashboard,
  `/app/team`, `/app/masters`, `/app/override-audit`, `/app/settings`),
  FINANCE_MANAGER (`/app` Approvals & Claims, `/app/override-audit`,
  `/app/settings`), ACCOUNTANT (`/app` Review Queue, `/app/settings`),
  CASHIER (`/app` Home, `/app/cash`, `/app/my-entries`, `/app/settings`);
  nested `<Routes>`: index = role home; `organizations` (SA),
  `team`+`masters` (Owner), `new-receipt`+`new-expense`+`cash`+`my-entries`
  (Cashier), `override-audit` (Owner/FM), `settings` (all), `*` → `/app`.
  Absolute `to="/app/..."` paths on purpose (relative `to` stacks wrong).
- Help: `frontend/src/help/manifest.ts` (`HELP_MANIFEST`, `ROLE_TO_HELP`,
  `articleBody()` via `import.meta.glob('./*/*.md?raw')`) + articles in
  `frontend/src/help/<cashier|accountant|finance-manager|owner|super-admin>/*.md`;
  drawer `frontend/src/help/HelpDrawer.tsx`; shell Help button +
  per-screen `?` deep-links. No backend table.
- Seeded accounts (passwords from migration comments — test phase only):
  Super Admin `dams@jjsoftware.com` / `Welcome12345@` (`V1`, change after
  first login via `POST /api/v1/auth/change-password`); demo org
  `JJ Motors (Demo)` (`multi_branch_cashier_access=FALSE`, branches
  `OOJ/Jeypore`, `OOB/Berhampur`, `OOR/Rayagada`): `owner@jjmotors.demo` /
  `owner123` (Priya Nair), `finance@jjmotors.demo` / `finance123`
  (Rakesh Menon), `accountant@jjmotors.demo` / `accountant123` (Anita Rao,
  branches OOB+OOR), `cashier@jjmotors.demo` / `cashier123` (Bikram Nayak,
  home OOR) — see `V5__seed_demo_dealership.sql`. Sample customers/job
  cards mirror the mockups (`V9`), sample receive/expense/cash docs
  (`V12`, `V15`, `V18`).

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

Where this lives in this repo (verified):
- Email: `com.dams.email.EmailService` + `LoggingEmailService`
  (logs invite links when SMTP is not configured); invite link built from
  `dams.app.base-url` (`APP_BASE_URL`, default `http://localhost:5173`);
  accept route `POST /api/v1/auth/accept-invite` + frontend
  `/accept-invite` page.
- Admin: `GET|POST /api/v1/admin/organizations`,
  `GET|PATCH|DELETE /api/v1/admin/organizations/{id}`
  (`AdminOrgController`, SUPER_ADMIN-only; delete removes the org and all
  its data). Users: `GET|POST /api/v1/users`, `GET|PATCH /api/v1/users/{id}`
  (`UserController`, Owner writes — invite path for real orgs).
  Frontend: `superadmin/OrganizationsPage.tsx`,
  `owner/TeamAndBranchesPage.tsx`.
- Prod deploy: `compose.prod.yml` + `docs/deployment-guide.md` + `deploy/`
  scripts; images from GHCR as built by `ci.yml`.

---

## Backend API map (verified — keep this table current when routes change)

All paths prefixed `/api/v1`. Auth: Bearer JWT (`JwtConfig`); public only:
`/auth/login`, `/auth/accept-invite`, `/attachments/raw` (sig+exp ARE the
auth, like an S3 presigned URL), `/swagger-ui.html`, `/swagger-ui/**`,
`/api-docs/**`, `/actuator/health` — see `SecurityConfig#filterChain`.

| Area | Controller | Routes |
|---|---|---|
| Auth | `auth/controller/AuthController` | `POST /auth/login`, `POST /auth/accept-invite`, `POST /auth/change-password` |
| Admin (cross-org exception) | `admin/controller/AdminOrgController` | `GET|POST /admin/organizations`, `GET|PATCH|DELETE /admin/organizations/{id}` |
| Branches | `branch/controller/BranchController` | `GET /branches`, `GET /branches/{id}`, `POST /branches`, `PATCH /branches/{id}` |
| Users | `user/controller/UserController` | `GET /users`, `GET /users/{id}`, `POST /users`, `PATCH /users/{id}` |
| Org settings | `organization/controller/OrgSettingsController` | `GET /organization`, `PATCH /organization` |
| Masters | `masters/controller/MastersController` | `GET /masters/{type}`, `GET /masters/{type}/{id}`, `POST /masters/{type}` (Owner), `PATCH /masters/{type}/{id}` (Owner) |
| Receivers | `receiver/controller/ReceiverController` | `GET /receivers`, `GET /receivers/{id}`, `POST /receivers`, `PATCH /receivers/{id}` |
| Customers | `customer/controller/CustomerController` | `GET /customers`, `GET /customers/{id}`, `GET /customers/{id}/history`, `POST /customers`, `PATCH /customers/{id}` |
| Vehicles | `vehicle/controller/VehicleController` | `GET /vehicles`, `POST /vehicles` (lookup + deduped create; number normalised) |
| Job cards | `jobcard/controller/JobCardController` | `POST /job-cards` (existing or inline customer/vehicle create), `GET /job-cards/{id}` (derived `{branchCode}-JC-{id}` ref), `PATCH /job-cards/{id}`, `POST /job-cards/{id}/close-claim` (FM) |
| Receipts | `receive/controller/ReceiveDocumentController` | `POST /receipts`, `GET /receipts/{id}`, `POST /receipts/{id}/submit`, `POST /receipts/{id}/resubmit`, `POST /receipts/{id}/lines`, `PATCH /receipts/{id}/lines/{lineNo}`, `DELETE /receipts/{id}/lines/{lineNo}`, `POST|GET /receipts/{id}/attachments`, `POST|GET /receipts/{id}/lines/{lineNo}/attachments` |
| Expenses | `expense/controller/ExpenseDocumentController` | `POST /expenses`, `GET /expenses/{id}`, `PATCH /expenses/{id}`, `POST /expenses/{id}/submit`, `POST /expenses/{id}/resubmit`, `POST /expenses/{id}/transfer-to-claim`, `POST /expenses/{id}/lines`, `PATCH /expenses/{id}/lines/{lineNo}`, `DELETE /expenses/{id}/lines/{lineNo}`, `POST|GET /expenses/{id}/attachments`, `POST|GET /expenses/{id}/lines/{lineNo}/attachments` |
| Cash docs | `cash/controller/CashDocumentController` | `POST /cash-documents`, `GET /cash-documents`, `GET /cash-documents/{id}`, `PATCH /cash-documents/{id}`, `POST /cash-documents/{id}/submit`, `POST /cash-documents/{id}/resubmit`, `DELETE /cash-documents/{id}` |
| Cash day | `cash/controller/CashController` | `GET /cash/drawer`, `POST /cash/opening`, `POST|GET /cash/close-day` |
| Review | `review/controller/ReviewController` | `GET /review/receipts|expenses|cash`, `GET /review/fm/receipts|expenses|cash`, `POST /receipts/{id}/verify|query|reject`, `POST /receipts/{id}/lines/{lineNo}/override`, `POST /receipts/{id}/approve`, `POST /expenses/{id}/verify|query|reject`, `POST /expenses/{id}/lines/{lineNo}/override`, `POST /expenses/{id}/close`, `POST /expenses/{id}/approve`, `POST /cash-documents/{id}/verify|approve|query|reject` |
| Search | `search/controller/SearchController` | `GET /search?q=` |
| My Entries | `myentries/controller/MyEntriesController` | `GET /my-entries` |
| Dashboard | `dashboard/controller/DashboardController` | `GET /dashboard/summary`, `GET /dashboard/outstanding`, `GET /dashboard/activity` |
| Override audit | `audit/controller/OverrideAuditController` | `GET /override-audit` (Owner+FM, filterable user/branch/date) |
| Attachments | `attachment/controller/AttachmentController` | `GET /attachments/{id}`, `DELETE /attachments/{id}`, `GET /attachments/raw` (public w/ signature) |

Docs for every row above come from annotations (`@Operation`/`@Tag`), not a
hand-maintained file — check Swagger UI when in doubt.

## Reverification smoke checklist (fixed click-path per role — use for step 3)

- **Cashier** (`cashier@jjmotors.demo`): login → Home universal search finds a
  seeded customer → history card → Add Payment appends a line to the existing
  open Receive doc (no second doc) → New Receipt / New Expense create →
  Cash page drawer position matches `Opening + cash receipts + Cash In −
  cash expenses − Cash Out` → Close Cash with counted amount (variance +
  mandatory remark when ≠ 0 locks the date) → My Entries shows today+recent
  with queried items highlighted → queried item opens in edit mode →
  Resubmit returns it to SUBMITTED.
- **Accountant** (`accountant@jjmotors.demo`): Review Queue lists SUBMITTED
  docs in OOB+OOR only → verify moves receipt to FM approval (receipt does
  NOT close) → override writes trail row (provisional) → query/reject with
  reason → close an Expense explicitly → set a branch's first-ever opening.
- **Finance Manager** (`finance@jjmotors.demo`): FM queues (receipts incl.
  open/recently-closed claims, expenses, cash) → approve → close a
  Warranty/AMC/CG claim with final override → record shows
  "Overridden · Final" → Override Audit lists who/when/original→new/reason/doc-line.
- **Owner** (`owner@jjmotors.demo`): Dashboard KPIs never include Cash In/Out
  → branch comparison drill-downs → Team & Branches (add branch/user,
  role-conditional branch assignment; multi-branch cashier toggle default
  OFF) → Masters CRUD deactivates, never deletes → Override Audit visible.
- **Super Admin** (`dams@jjsoftware.com`): Organizations list → onboard org +
  first Owner via email invite (not temp password) → invite link
  (`/accept-invite`) sets password → no transactional data visible by default.
- **Guards on every pass:** JWT role/`org_id` drives every view (no toggle,
  `/login` the only public screen); `org_id` filtering intact
  (`CrossOrgIsolationTest` green); maker-checker holds (cannot
  verify/approve own create/last-modify); IDs gap-free/sequential/never
  reused; no applied migration edited; `git diff` contains only the task.

## Filename note

The repo file is `AGENT.md`. Claude Code and Cursor look for `AGENTS.md` by
default — if an agent seems to ignore these rules, check that filename
mapping first before assuming the rules were read.

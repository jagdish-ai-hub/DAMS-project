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
  Adds branches, adds users, assigns roles, extra roles and branch access.
  Read-only on transactions while acting as Owner; never edits them (may
  switch into another role — see "Acting roles").
- **FINANCE_MANAGER** — all branches within their org. Final approval on
  every entry, **except** the carve-out below. Closes Warranty/AMC/CG
  claims, with override authority that is final and locked once used.
  **Close Claim is itself the approval for a claim receipt (rev 49)** —
  there is no separate Approve step for a claim. While its receipt is
  VERIFIED, the FM sees only Query and Close Claim; Close Claim approves
  every VERIFIED receive document on the job card and closes the claim in
  one transaction. See plan.md rev 49.
- **ACCOUNTANT** — one or more assigned branches within their org. Verifies
  submitted entries, can override amounts (provisional — still needs FM
  approval downstream) or the job card's invoice amount, queries or rejects
  with a reason. Closes Expense documents explicitly.
  **Direct approval (org opt-in, default OFF — rev 46):** when an Owner
  turns on `accountant_direct_approve_cash` in Settings, an Accountant may
  approve a SUBMITTED receipt directly — one click, straight to APPROVED,
  skipping the Finance Manager — but only when it is not a claim (no
  `claim_type_id`), its business status isn't "Credit", and every
  settlement line is cash-mode. Everything else still requires FM approval
  as usual. See plan.md rev 46.
  **Accountant queue buckets (rev 69):** the Receipts queue splits into
  *Cash* (the direct-approve rule above — the only bucket with bulk
  approve), *Credit* (business status is "Credit", not a claim) and *Claim
  Transaction* (has a claim type). "Credit" means exactly the Credit status
  — a Received receipt paid part cash / part bank is **not** Credit — so a
  receipt leaves Credit the moment its status is changed (by the Accountant,
  or by the Cashier after adding settlement lines). Receipts in none of the
  three show under All only. See plan.md rev 69.
  **FM-queried entries land back here, not with the Cashier (rev 49):**
  when the FM queries a VERIFIED entry, it returns to the Accountant's own
  queue (not the Cashier's My Entries) as `FM_QUERIED`, with the same
  override tools available to fix it. The Accountant resends it straight
  to the FM. An Accountant's own query on a fresh SUBMITTED entry is
  unchanged — that still goes to the Cashier. See plan.md rev 49.
- **CASHIER** — exactly one branch. Creates Receive and Expense entries,
  adds payments against existing job cards, does daily cash closing.

A user never verifies or approves an entry they created or last modified.
**Exception (rev 59):** a user whose own role is **Owner** may review, verify
or approve entries they made while acting in another role — the Owner owns
the business and is the one person who may act as every role (see "Acting
roles"). Nobody else is exempt: a switched Accountant or Finance Manager is
still blocked from their own entries.

### Acting roles (role switching — plan.md rev 55)

One person can hold more than one job. Each user has a **primary role**
(above) plus optional **extra roles** the Owner grants in Team & Branches:

- Extra roles are `FINANCE_MANAGER` (org-wide, no branches), `ACCOUNTANT` or
  `CASHIER` (each granted for specific branches). An Owner or Super Admin
  cannot be granted extra roles; a grant can't repeat the primary role.
- A **Switch role** button beside Help lets the user pick a branch, then a
  role available at that branch. The server issues a new JWT whose `role`
  is the **acting role**; `primaryRole` and `actingBranchId` ride along. The
  client never chooses its own role.
- **Owner** always has the button and may act as any role at any branch.
  "Read-only on transactions" applies while acting as Owner; while acting as
  Finance Manager / Accountant / Cashier the Owner has that role's powers.
- An acting session is scoped to **the one branch picked**. Acting as
  Cashier at OOR posts to OOR; acting as Accountant at OOR sees OOR only.
  To work another branch, switch again. **Exception:** Finance Manager is
  an org-wide role, so a session acting as Finance Manager still sees every
  branch (the picked branch is only shown in the banner).
- **Attribution never changes.** `created_by` / `last_modified_by` remain the
  real user's id, so Ajay's entries show "Ajay". Audit events additionally
  store `actor_role` while acting ("Ajay · as Cashier"), and each switch is
  itself audited (`ROLE_SWITCHED`).
- **Maker-checker is per person, not per role.** Ajay's cashier entry can
  never be verified or approved by Ajay acting as Accountant — **except when
  the person is an Owner** (rev 59): an Owner acting as Cashier and then as
  Accountant / Finance Manager may review their own entries. When an entry is
  blocked, the review screen says who entered it and in which role, and names
  the people who *can* clear it (active accountants at that branch, or
  Finance Managers), so the entry is never a dead end.
- A revoked grant takes effect on the next request (the acting role is
  re-checked against the DB); the user is sent back to login.

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
  the Vehicle master table. A vehicle belongs to exactly one customer; a
  customer may have many vehicles.
- **Contact and Chassis # (rev 68)**: two **optional** details on a Receipt,
  stored on its **job card** — never copied onto the customer or vehicle
  master, so typing one can never overwrite what is saved there. Contact is
  a phone number as given at that receipt; the form pre-fills it from the
  customer's last saved number until the cashier types their own. Chassis #
  is stored uppercase with no spaces. Both show on the Accountant and
  Finance Manager review card next to the vehicle number ("—" when never
  recorded). Linking an existing job card only fills a blank one, never
  overwrites it.
- **Linking (rev 56)**: on the Receipt, Expense and Job-Card-create screens
  the customer, vehicle and job card are **picked from a branch-scoped
  search, not retyped** (same `BranchScope` rule as universal search:
  cashier = home branch, or org-wide when the multi-branch toggle is ON;
  accountant = assigned branches; owner/FM = all). Typing a customer or
  vehicle that does not exist creates it on save (vehicle deduped on the
  normalised number; a number already registered to a *different* customer is
  rejected, never silently reused). **On the Expense screen the job-card picker
  is labelled "Ooriba ID" and is keyed on the receipt's DAMS-Receive-ID** (e.g.
  `OOR-AUG26-R-001`): typing a receive ID finds its job card, and each result shows
  the receive ID first (the job-card reference only when no numbered receipt
  exists yet). **On the Receipt screen (rev 64) it is different:** a new receipt
  has no receive ID yet (it is assigned on submit), so the picker is hidden behind
  a small **"Link an existing job"** link, placed just above the Documents
  section, and searches by **customer, vehicle no, Job Card / DBM or invoice**
  (a receive ID still matches). Results lead with Customer · Vehicle · DBM; the
  Ooriba ID / job-card reference is the small second line.
  **Vehicle on record under a different name (rev 70):** when the cashier
  saves or submits a Receipt or Expense whose typed vehicle number already
  belongs to a customer other than the one entered, the app never switches
  customer silently. A dialog shows "vehicle ABC is on record under XYZ, but
  you entered BCD" with two option boxes — *XYZ is correct* (save under the
  record's customer) or *Update the name to BCD* (renames that customer, for
  all their records) — and proceeds on the one selected. Only a typed new
  name can be a rename; a *picked* different customer offers only the first.
  The check runs at save/submit only, never while typing. The server enforces
  the same rule: a typed name that differs from the vehicle owner's is
  rejected (409), not ignored.
  An expense/receipt may carry `customer_id`
  + `vehicle_id` even without a job card; if a job card is also given, its
  customer/vehicle must match.
- **Cashier home message boxes (rev 57)**: left box = documents the Accountant
  or Finance Manager queried or rejected (Rejected shown 7 days, not counted);
  right box = the cashier's FM pre-approval requests (waiting / approved /
  queried). Each box has a badge counting only items still needing the cashier;
  it is derived from the document's current state, so resubmitting or submitting
  removes the item -- no read tracking. `FM_QUERIED` is the Accountant's, not shown.
  Clicking a message opens that document for edit / resubmit. The boxes load once
  when the cashier opens Home (login lands there; returning from a document reloads
  it) -- there is no background polling.
- **Job cards may start from an Expense (rev 56)**: a job card is no longer
  created only from a Receive. A Cashier may create one from the Expense form,
  **without a customer** (`job_card.customer_id` nullable; the typed vehicle
  number is kept as text until a customer exists). A later Receipt **attaches
  the customer once** (set-once, then locked). Correcting a wrongly attached
  customer is **not built yet** -- it needs a defined rule for re-assigning the
  vehicle -- so a second attach is refused (flagged for a later stage). A receipt cannot post against a job card that
  still has no customer.

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
   **A settled (auto-closed) receipt still reopens the same way a
   VERIFIED/APPROVED one does: "Add Payment" un-settles it and appends the
   new line, with every existing line staying locked.** If the document was
   VERIFIED or APPROVED at the time, it also moves back to SUBMITTED for
   re-review, same as the VERIFIED/APPROVED reopen case — a self-closed
   receipt is not a dead end, it's just the strictest starting point.
2. **Expenses close by themselves at their last approval step (rev 74).** The
   moment the last reviewer who has to act presses their button, the expense
   goes straight to CLOSED (files freeze, audit shows `CLOSED` with `auto`):
   an in-limit expense with an ordinary status closes when the **Accountant
   verifies** it; one that needs the Finance Manager (over its limit, or a
   status flagged "needs Finance Manager approval", and no pre-approval
   covering the total) closes when the **Finance Manager approves** it. A
   Transfer to Claim expense is *not* auto-closed — the Finance Manager's
   Close Claim ends it. The Accountant's **Close expense** button stays for
   expenses verified before this rule. (Before rev 74 the Accountant had to
   press Close expense as a separate step, and expenses sat at VERIFIED.)
   Status flow: Open
   → In Progress → Awaiting Receipt → Received Receipt → Closed (or
   Transfer to Claim). **Any expense may be transferred to a claim — it need
   not be tagged to a job card, and its job card need not be a Warranty / AMC /
   CGW claim** (e.g. promotional activities the OEM reimburses have no job
   card). Only a REJECTED / CLOSED document, or one awaiting FM approval,
   cannot be transferred.
   **A "Transfer to Claim" expense is closed by the Finance Manager, not the
   Accountant (rev 61).** It goes Cashier → Accountant (verify, or query back
   to the Cashier) → **Finance Manager**, who presses **Close Claim**: they
   enter the **final amount actually recovered** from the claim (default = the
   expense total; 0 is allowed), with a **reason mandatory whenever it
   differs**, or **Query** it back to the Accountant (`FM_QUERIED`, as for any
   verified entry). Close Claim is itself the FM's approval — there is no
   separate Approve. A differing amount is permanent and shown "Overridden ·
   Final" everywhere the record appears, and in Override Audit. The Accountant
   cannot close a claim expense. It reaches the FM only **after** the
   Accountant has verified it. The route follows the status the expense is in
   *now* (checked by the `triggers_claim` flag, never the label): moved onto
   Transfer to Claim after verification it goes to the FM; moved off it before
   the FM closes it, it returns to the Accountant's normal close; once closed
   it is locked. An over-limit claim expense still needs the FM's pre-approval
   before submit (rev 53) — Close Claim comes on top of that, at the end.
   **Claims at a glance (rev 62).** The Owner dashboard (under its branch and
   period filters) and the Finance Manager page show a Claims card:
   **Total claimed**, **Total received**, **Claim rejected / not recovered** and
   **Still open**, for expense claims and warranty / AMC / CG receipt claims
   together, then split by kind. Claimed = received + rejected + still open. A
   claim counts in the period it was **raised** (an expense when submitted; a
   receipt claim when its first live receipt is submitted). *Claimed*: an
   expense's total, or a receipt claim's invoice amount. *Received*: a closed
   claim's Finance Manager final amount; for a still-open receipt claim, the
   payments received so far (an open expense claim has none yet). *Rejected*:
   closed claims only — claimed minus the final amount (a claim closed at ₹0 is
   entirely rejected). *Still open*: open claims only — claimed minus received so
   far. An expense closed by the Accountant before claim closing existed (no
   recorded final amount) is left out. The Expenses KPI is unchanged — it
   counts what was actually spent.
   **Owner dashboard accuracy (rev 71).** (1) **Cash in hand is a running
   position:** the branch's last closing count (or its configured opening),
   plus every cash receipt / Cash In, minus every cash expense / Cash Out
   dated after that close up to today — days that were never closed are
   included, not skipped. The Cash page keeps its one-day formula. Clicking
   the card lists those movements (opening and expenses/Cash Out signed) and
   its total equals the card. (2) **Collections and Expenses stay
   approved-only**, and each card now also shows what is **awaiting approval**
   for the same period (submitted, verified or queried, never drafts or
   rejected). The "pending review" count no longer sits under Cash in hand.
   (3) **Stuck with whom:** a card with three tiles — Cashier, Accountant,
   Finance Manager — each showing how many entries are waiting on that person
   and their value; clicking a tile lists the entries (document, branch,
   party, stage, amount, days waiting) and a row opens the document. *Cashier*
   = queried / sent back (drafts not yet submitted are shown beside it, not in
   its count). *Accountant* = submitted, FM-queried, and expenses verified or
   approved that only the Accountant closes. *Finance Manager* = verified
   receipts and cash movements, expenses that need FM approval (over limit or a
   status that requires it), claim expenses awaiting Close Claim, and
   pre-approval requests. Approved receipts, closed and rejected entries are
   not stuck. (4) **Recent activity** also lists overrides, payments added,
   status / category / claim-type changes, transfers to claim, approval
   requests, pre-approvals and cash re-opens.
   **Closed claims in Collections (rev 73).** Collections follows the Finance
   Manager's decision on a closed claim: for each closed receipt claim, the
   money counted is the **final amount recovered**, not the Cashier's payment
   lines. The payment lines still count on their own dates; the difference
   (final amount − approved lines) is added as one labelled **Claim final amount
   adjustment** on the day the claim was closed, in the same branch — so a claim
   closed lower than entered reduces Collections, one closed higher raises it. It
   shows in the Collections trend, branch table, mode split and drill-down (where
   it is a clickable row that opens the receipt), so the card always equals its
   rows, and it agrees with the Claims card. It is **not cash**: Cash in hand,
   the drawer and Cash In/Out never change. Open (unclosed) claims are unchanged.
   **Outstanding (rev 73):** a job card whose only receipts are **blank drafts**
   (nothing submitted, no payment lines) is not a receivable and is left out.
   Job cards whose receipt was rejected stay listed until the Owner says
   otherwise — a rejected entry may still be owed money.
   **A reviewer may change an expense's business status (rev 58).** The
   Accountant (own branches), Finance Manager and Owner can change it from the
   review screen while the expense is SUBMITTED, VERIFIED, APPROVED or
   FM_QUERIED — not DRAFT/QUERIED (the Cashier's) and not CLOSED/REJECTED. The
   workflow state does not change, and it is audited (`STATUS_CHANGED`;
   `TRANSFERRED_TO_CLAIM` when the new status is the claim status). A status that
   requires FM approval then applies the normal close rule.
   **Owner expense page (rev 60):** the Owner has an **Expenses** page — every
   non-draft expense in every branch, all workflow states, filterable by
   branch / status / date. It is oversight plus status change only: the Owner
   can open any expense and change its business status (same states as above),
   but verify / query / reject / approve / close / line override stay with the
   Accountant and Finance Manager.
   **Names on screen (rev 58):** *Ooriba ID* is a DAMS-Receive-ID (a receipt's
   own, or the receipt an expense is linked to). On an expense, the typed
   number field is *Job ID / PO / SO* (a separate field from the Ooriba ID).
   **Over-limit expenses need Finance Manager pre-approval before submit
   (rev 53).** When any line is above its sub-category limit, the Cashier
   cannot Submit — they **Send for Review** instead. The draft (no number
   yet, locked while waiting) appears to the FM as an approval request at
   the top of their home and in their Expenses tab; the FM **Approves** or
   **Queries** it back to the Cashier with a note (no Reject). Once
   approved, the Cashier Submits it; from there the Accountant verifies and
   closes it as usual, and it does **not** go back to the FM for a second
   approval. If the total later rises above the approved amount it needs
   approval again (Send for Review before Submit; after Submit, the normal
   FM Approve before Close). The same or a lower total submits directly.
   The server enforces all of this — not just the buttons.
   **A business status can require the same approval (rev 54).** An expense
   status flagged "needs Finance Manager approval" (the seeded **Requires
   Finance Approval** status, or any status an Owner marks that way in
   masters) sends the expense through exactly this flow even when every
   line is within its limit: choosing it switches Submit to Send for Review.
   Checked by the flag, never the status name.
   **The FM's Expenses queue lists only expenses that need the FM (rev 60).**
   A VERIFIED expense appears there only when it is over a sub-category limit
   or in a status flagged "needs Finance Manager approval", and no FM
   pre-approval still covers its total. An in-limit expense with an ordinary
   status is the Accountant's to close alone and never reaches the FM. (If a
   reviewer later moves it to a flagged status, or it grows past a
   pre-approval, it appears.)
3. **Warranty / AMC / CG claims are closed explicitly by the Finance
   Manager.** FM may override the final settled amount at closing (e.g.
   accepting Eicher's partial payment as final). That override is
   permanent and must be visibly marked "Overridden · Final" everywhere
   the record is shown afterward.
   **Visibility vs closing are separate.** A claim becomes visible in the
   FM's open-claims list as soon as its receipt enters the review workflow
   (SUBMITTED / QUERIED / FM_QUERIED / VERIFIED / APPROVED) and stays there
   until it is closed — a claim must never sit invisible among ordinary
   receipts just because nobody has approved it yet. **Closing** still
   requires every live receive document on the job card to be at least
   VERIFIED: money goes through maker-checker before a claim is finalised.
   **Close Claim is itself the approval (rev 49) — there is no separate
   Approve step for a claim receipt.** Any live document still VERIFIED is
   approved as part of the same close transaction (same maker-checker
   check and audit trail a standalone Approve would use); the FM sees only
   Query and Close Claim while a claim receipt sits VERIFIED. The list
   shows each claim's workflow status, and the Close Claim action stays
   unavailable (with the reason shown) until every live document is at
   least verified.

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
**What "frozen" means (rev 72):** a receipt is frozen only when it is
**Approved** or **Rejected** (an expense: Approved, Closed or Rejected). A
receipt that is fully paid ("settled") is **not** frozen while it is still in
review — when the Accountant or Finance Manager queries it, the Cashier must be
able to add (and replace a wrong) document. Files are therefore frozen at
approval / claim-close, never merely because a receipt became fully paid.
**Who can see and add documents on a review card (rev 72):** the Accountant,
Finance Manager and Owner review card has a **Documents** section listing every
file attached to the receipt or expense — whole-document and per-line — each
opening in the same viewer the Cashier uses. The **Accountant can also upload**
(in their own branches, while the record is not frozen) and add a note to a
file — e.g. when the Finance Manager sent it back for a missing bill — but
cannot remove the Cashier's files. Finance Manager and Owner are view-only.
**GST on the review card (rev 72):** every receipt shows its customer type
(B2B / B2C); the **GST #** shows the number for a B2B customer and is blank for
B2C.

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
   - **Only the Owner can reopen a closed day (rev 60)** — for a close made
     by mistake. From the Cash page (Owner picks the branch), the Owner
     reopens the branch's **latest** close with a mandatory reason; earlier
     closes can't be reopened first because each day's opening is the
     previous close's counted amount. Reopening removes the close row, so the
     date unlocks and the Cashier can add the missed entries and close again
     with a fresh count. The original close (counted amount, variance,
     remark, who closed it) is preserved in the audit trail as
     `CASH_REOPENED` together with the Owner's reason. Nobody else —
     Cashier, Accountant, Finance Manager — can reopen.
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
   **Job-card business statuses are additionally role-mapped**: each status
   records which roles may set it, so a role's dropdown shows only its own
   statuses. The mapping is Owner-editable data, not code — see plan.md
   rev 44 for the current split and the `deprecated` vs `active` distinction.
   **Retired statuses (rev 65).** A status that is *deprecated* or *inactive* is
   never offered in a dropdown for new work, and the server refuses to set it on a
   job card that does not already have it. A record that already carries one
   keeps it and still shows it (marked "(retired)") — nothing is blanked or
   reassigned. In the Owner's Masters list, deprecated rows are grouped at the
   bottom under a collapsed "Deprecated (n)" heading. (This replaces the earlier
   "deprecated still works, shown last" rule.)
4. **Org-wide Override Audit view** (Owner + FM): one screen listing every
   amount override across the organization — who, when, original → new
   value, reason, document/line ID — filterable by user, branch, and date.
5. **No Tally export or ledger report in v1.** Tally references elsewhere
   in this file are historical context only. **Allowed (rev 67): exporting a
   list the user is already looking at.** The Accountant's "Pending & closed"
   window has an **Export to Excel** button (bottom-right) that downloads
   exactly the rows its filters show, in the same order, as an Excel-friendly
   CSV — one row per settlement / expense line with the transaction's details
   repeated, a transaction with no lines still gets one row, and the
   transaction amount is written once per transaction so the column sums to
   the window's total. It is a copy of the screen, not a ledger or Tally feed.
6. **Post-approval reversal/correction: deferred beyond v1.** V1 relies on
   the maker-checker flow catching errors before approval.
7. **Universal search for every role**, results always scoped by that
   user's branch access (and the cashier toggle in #2).
8. **Queried-entry fix-and-resubmit loop (required v1) — two distinct
   loops, not one (rev 49).** An Accountant's query on a SUBMITTED entry
   still goes to the Cashier: the cashier home's "My Entries" list (today +
   recent) shows it with workflow badges, highlighted, open in edit mode,
   correctable and **Resubmitted** back to SUBMITTED. The same
   append-while-open principle applies to Expense documents: lines can be
   added until the Accountant closes them.
   A Finance Manager's query on a VERIFIED entry is a **second loop that
   never reaches the Cashier**: it returns to the Accountant's own review
   queue as `FM_QUERIED`, fixed there with the same override tools used on
   a fresh entry, and resent straight back to VERIFIED with a "Resend to
   Finance" action. See plan.md rev 49.

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
- There is no **client-side** role toggle. The demo toggle in
  `review-close.html` is not built. The only way to change roles is the
  server-issued **Switch role** flow (see "Acting roles"): the user must
  hold an Owner-granted extra role, and a new JWT is issued. A screen
  always renders from the JWT's acting role, never a client-side choice.
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

**Appearance — platform font (rev 50).** The UI typeface is a platform-wide
setting owned by Super Admin (Settings → Appearance), not per-org and not
per-user. It is chosen from a fixed, code-defined list of vetted fonts —
currently IBM Plex Sans (default) and Inter — never a free-text name or URL,
so every offered font is self-hosted and known to carry the ₹ glyph and
tabular figures. Adding a font = one frontend registry entry + its
`@fontsource` package + the backend allowlist. The chosen font applies to
every role and to the login / accept-invite screens. Changing it never
changes colours, layout, or any workflow.

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
`/auth/login`, `/auth/accept-invite`, `/public/appearance`, `/attachments/raw` (sig+exp ARE the
auth, like an S3 presigned URL), `/swagger-ui.html`, `/swagger-ui/**`,
`/api-docs/**`, `/actuator/health` — see `SecurityConfig#filterChain`.

| Area | Controller | Routes |
|---|---|---|
| Auth | `auth/controller/AuthController` | `POST /auth/login`, `POST /auth/accept-invite`, `POST /auth/change-password` |
| Admin (cross-org exception) | `admin/controller/AdminOrgController` | `GET|POST /admin/organizations`, `GET|PATCH|DELETE /admin/organizations/{id}` |
| Appearance (platform) | `appearance/controller/AppearanceController` | `GET /public/appearance` (unauthenticated), `PUT /admin/appearance` (SUPER_ADMIN) |
| Branches | `branch/controller/BranchController` | `GET /branches`, `GET /branches/{id}`, `POST /branches`, `PATCH /branches/{id}` |
| Users | `user/controller/UserController` | `GET /users`, `GET /users/{id}`, `POST /users`, `PATCH /users/{id}` |
| Org settings | `organization/controller/OrgSettingsController` | `GET /organization`, `PATCH /organization` |
| Masters | `masters/controller/MastersController` | `GET /masters/{type}`, `GET /masters/{type}/mine` (rows the caller's role may pick — role-filtered for `receive-statuses`), `GET /masters/{type}/{id}`, `POST /masters/{type}` (Owner), `PATCH /masters/{type}/{id}` (Owner) |
| Receivers | `receiver/controller/ReceiverController` | `GET /receivers`, `GET /receivers/{id}`, `POST /receivers`, `PATCH /receivers/{id}` |
| Customers | `customer/controller/CustomerController` | `GET /customers` (`?q=`, branch-scoped), `GET /customers/{id}`, `GET /customers/{id}/vehicles` (`?q=`), `GET /customers/{id}/history`, `POST /customers`, `PATCH /customers/{id}` |
| Vehicles | `vehicle/controller/VehicleController` | `GET /vehicles`, `POST /vehicles` (lookup + deduped create; number normalised) |
| Cashier inbox | `myentries/controller/MyEntriesController` | `GET /my-entries/inbox` (CASHIER; queries/rejections received + FM pre-approval replies, with unattended counts) |
| Job cards | `jobcard/controller/JobCardController` | `GET /job-cards` (`?q=&customerId=&vehicleId=`, branch-scoped search on customer/vehicle/DBM/invoice/ref), `POST /job-cards` (existing or inline customer/vehicle create; customer optional), `POST /job-cards/{id}/attach-customer` (set once), `GET /job-cards/{id}` (derived `{branchCode}-JC-{id}` ref), `PATCH /job-cards/{id}` (invoiceNo, invoiceAmount/clear, vehicleNo, dbmId, b2b, gstNo, categoryId, businessStatusId), `POST /job-cards/{id}/close-claim` (FM) |
| Receipts | `receive/controller/ReceiveDocumentController` | `POST /receipts`, `GET /receipts/{id}`, `POST /receipts/{id}/submit`, `POST /receipts/{id}/resubmit`, `POST /receipts/{id}/lines`, `PATCH /receipts/{id}/lines/{lineNo}`, `DELETE /receipts/{id}/lines/{lineNo}`, `POST|GET /receipts/{id}/attachments`, `POST|GET /receipts/{id}/lines/{lineNo}/attachments` |
| Expenses | `expense/controller/ExpenseDocumentController` | `POST /expenses`, `GET /expenses/{id}`, `PATCH /expenses/{id}`, `POST /expenses/{id}/submit`, `POST /expenses/{id}/resubmit`, `POST /expenses/{id}/transfer-to-claim`, `POST /expenses/{id}/request-approval`, `POST /expenses/{id}/lines`, `PATCH /expenses/{id}/lines/{lineNo}`, `DELETE /expenses/{id}/lines/{lineNo}`, `POST|GET /expenses/{id}/attachments`, `POST|GET /expenses/{id}/lines/{lineNo}/attachments` |
| Cash docs | `cash/controller/CashDocumentController` | `POST /cash-documents`, `GET /cash-documents`, `GET /cash-documents/{id}`, `PATCH /cash-documents/{id}`, `POST /cash-documents/{id}/submit`, `POST /cash-documents/{id}/resubmit`, `DELETE /cash-documents/{id}` |
| Cash day | `cash/controller/CashController` | `GET /cash/drawer`, `POST /cash/opening`, `POST|GET /cash/close-day` |
| Review | `review/controller/ReviewController` | `GET /review/receipts|expenses|cash`, `GET /review/receipts/direct-approve-eligible`, `GET /review/fm/receipts|expenses|cash`, `POST /receipts/{id}/verify|query|reject`, `POST /receipts/{id}/resubmit-to-fm`, `POST /receipts/{id}/lines/{lineNo}/override`, `POST /receipts/{id}/override-invoice-amount`, `POST /receipts/{id}/direct-approve`, `POST /receipts/direct-approve` (bulk), `POST /receipts/{id}/approve`, `POST /expenses/{id}/verify|query|reject`, `POST /expenses/{id}/resubmit-to-fm`, `POST /expenses/{id}/lines/{lineNo}/override`, `POST /expenses/{id}/close`, `POST /expenses/{id}/approve`, `GET /review/fm/expense-requests`, `POST /expenses/{id}/pre-approve`, `POST /expenses/{id}/query-approval`, `POST /cash-documents/{id}/verify|approve|query|reject`, `POST /cash-documents/{id}/resubmit-to-fm` |
| Search | `search/controller/SearchController` | `GET /search?q=` |
| AI assistant | `ai/controller/AiController` | `POST /ai/ask`, `GET /ai/brief`, `GET /ai/benchmark`, `GET /ai/anomalies`, `GET /ai/risk`, `GET /ai/queries/roots`, `GET /ai/claims/insights`, `GET /ai/cash/advice`, `GET /ai/close/checklist`, `GET /ai/receivers/duplicates`, `GET /ai/masters/health`, `GET /ai/limits/advice`, `GET /ai/search` (all read-only; only write is `ai_query_log` trace row) |
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

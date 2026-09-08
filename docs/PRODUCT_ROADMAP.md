# DAMS — Product Understanding & High-Value Roadmap

> Part 1 is a complete map of what DAMS is and does today — read this before
> proposing or building anything. Part 2 ranks the features by real
> dealership value (money saved, leakage stopped, hours returned), not novelty.
> Every Part 2 item is **✅ Done** (built as FEAT-35…50 — see
> `docs/VALUE_ADDITIONS.md` §1 table + §5 build log). Per AGENT.md, anything
> new still needs a plain-language plan + confirmation before any code.

---

# Part 1 — How DAMS works today (complete map)

## 1.1 What it is and what it is not

DAMS replaces Excel-based bookkeeping at truck-dealership service stations —
receipts, expenses, cash tracking, warranty/AMC claims — with a small, correct,
auditable system. It is architected as **multi-tenant SaaS from day one**
(shared DB, shared schema, `org_id` on every tenant table, Hibernate `@Filter`
+ `findByIdAndOrgId` defense-in-depth), so new dealership groups onboard
without redesign.

Deliberately NOT accounting software: **Tally remains the system of record**.
DAMS is the clean, verified pipe that feeds accountants (CSV export exists),
plus the owner's live window into branches. Deliberately NOT workshop
management either: no bay scheduling, no inventory, no spare-parts stock.

## 1.2 The five levels + auditor (no switching, ever)

| Role | Scope | Does | Cannot do |
|---|---|---|---|
| SUPER_ADMIN | Platform (`org_id = null`) | Lists orgs, onboards org + first Owner via email invite | Sees any org's transactions |
| OWNER | Own org, all branches | Branches, users, masters, budgets, dashboards, override audit | Edits transactions (read-only) |
| FINANCE_MANAGER | Own org, all branches | Final approval on everything; closes Warranty/AMC/CG claims with a **final, locked** override | — |
| ACCOUNTANT | Assigned branches | Verifies, provisional overrides, query/reject with reason, closes expenses explicitly, sets first cash opening, countersigns breaching cash closes | Touches own created/last-modified docs |
| CASHIER | Exactly one home branch | Receive/Expense entries, Add Payment, Cash In/Out, daily close, fix-and-resubmit, quotes | Posts outside home branch |
| AUDITOR | Own org, all branches, **read-only** | Dashboard, override audit, masters (read-only), follow-ups, exports | Any write, anywhere; no review queues |

Auth: email + password → short-lived JWT (~8h) in `sessionStorage`, no refresh
(post-signoff hardening). JWT role + `org_id` drives every view; `/login` is
the only public screen. Maker-checker is structural: a user never
verifies/approves what they created or last modified.

## 1.3 The clubbing principle and IDs

One cause = one document, many lines. Receive Document ← settlement lines;
Expense Document ← expense lines. Cash movements are single-amount documents,
no lines. IDs (`{branch}-{MMMYY}-{R|E|C}-{seq}`) are server-generated,
gap-free per branch/month/type, never editable; line IDs (`…-L{n}`) never
reused. The job card (DAMS-internal) anchors a vehicle/customer over time —
never the external DBM number, never the vehicle number directly.

## 1.4 The three closing rules (never conflated)

1. **Receipts close themselves** at pending = 0 (`invoice − Σlines`);
   verification does NOT close. 2. **Expenses close explicitly** by the
   Accountant (over-limit needs FM approval first). 3. **Claims close
   explicitly** by the FM, optionally at an overridden final amount that is
   permanent and shown "Overridden · Final" everywhere.

Supporting flows: Add Payment appends a line to the one open doc (never a
second doc); queried entries loop back through the cashier's fix-and-resubmit;
cash day = `Opening + cash receipts + Cash In − cash expenses − Cash Out`,
closed by counted cash (variance remark mandatory when ≠ 0, date locks via
`CashDateLock`); a close breaching the org threshold parks PENDING for an
accountant countersign instead of locking clean (feature off when unset);
locked days reopen **only** by cashier request + FM approve/reject, fully
audited. Attachments (R2 or local disk behind a `StorageService` seam, signed
URLs, frozen on approve/close, SHA-256 warn-on-duplicate at upload). Masters
own every dropdown (deactivate, never delete, 90-day usage guard). Budgets are
caps that inform, never block. Search covers docs, vehicles, customers,
job cards and UTR last-4, always branch-scoped. Credit dues carry due dates +
promises with a ranked defaulter view; B2B customers carry warn-first credit
limits; job cards carry service-due dates and stuck reasons; estimates record
the agreed quote with variance vs the final invoice; staff advances ledger
ADVANCE out / RECOVERY in with derived outstanding; bank statements reconcile
by UTR match with confirm/ignore (matching never moves money).

## 1.5 What each role sees (screens)

Cashier: universal search → customer history (statement share, credit-limit
banner, per-job Quote link, service-due + stuck-reason editors) → Add Payment
/ New Receipt / New Expense / Estimates, Cash page (drawer + close), My Entries
(queried highlighted). Offline: creates queue in a local outbox and sync as
drafts via the banner. Accountant: review queue (receipt/expense/cash tabs,
verify / override / query / reject, close expense, bulk verify), Awaiting
Bills, Reconcile, cash page in read mode (countersign PENDING closes). FM:
approvals + claims (approve, bulk approve, claim close, claim pack, reopen
decisions), Claims Chase, floor, dues, messages, reconcile, staff, estimates.
Owner: dashboard (KPIs, trend, branch comparison, outstanding, activity,
budgets), Dues (follow-ups + defaulters), Floor (WIP + renewals), Messages
(templates + send log), Reconcile, Staff, Estimates, Team & Branches (incl.
user phones, auditor role), Masters, Override Audit, AI hub, Settings
(countersign threshold, digest opt-in). Auditor: dashboard, override audit,
read-only masters — nothing else. Super Admin: organizations + invite
onboarding. Help: bundled Markdown, role-scoped (cashier 9, accountant 7,
finance-manager 5, owner 10 articles), per-screen "?" links. Nightly owner
digest pushes the daybook over WhatsApp at 20:00 IST when opted in (logged
until a real provider is configured).

## 1.6 The AI module is 100% rule-based (no LLM)

All 13 `/ai/*` endpoints compute deterministic aggregates (aging buckets,
variance flags, keyword clusters, additive risk scores, duplicate-name/phone
matching); the only write is an `ai_query_log` row. There is no model key,
no network call, no hallucination surface — treat it as "saved analyses",
not intelligence. Anything needing language understanding is a new build,
not an extension.

## 1.7 Quality gates and deliberate non-goals

244 backend tests + 45 frontend unit + 29 Playwright E2E + JaCoCo 45% floor +
boot-and-probe API smoke all block deploy via the `quality-gate` job, with
health-check auto-rollback. Migrations V25–V36; org purge covers every new
table in FK-safe order. Deliberate non-goals (do not re-propose without
new justification): silent reversals, Tally two-way sync, refresh tokens /
remember-me (post-signoff), full offline-first (scoped outbox built instead:
queued creates sync as drafts; edits/submits need the server), push
notifications (5-min nav-badge poll instead), SMTP email (invite links are
manual-copy in practice), real WhatsApp delivery (seam + logging sender
built; provider keys still needed).

---

# Part 2 — Ranked high-value features (all ✅ Done — see `docs/VALUE_ADDITIONS.md` FEAT-35…50 for the build log)

Ordering = (rupees protected or returned) ÷ (build size), judged against how
a real service station runs: fleet drivers pay and leave, Eicher pays claims
late and short, cashiers handle lakhs in cash, accountants reconcile UTRs by
hand, owners live on WhatsApp — not in dashboards.

## Tier 1 — stops leakage or returns cash directly

### FEAT-35: Receivable follow-up — due dates, aging, reminders, defaulter view
- **Roles:** Cashier (logs promise), Accountant (follows up), Owner (sees exposure).
- **Problem today:** `Credit (Due)` and `B2B Credit` exist as modes, and the
  dashboard lists outstanding as display-only text. Nobody owns the *next
  step*: no due date, no aging beyond display, no reminder, no defaulter list.
  Workshop receivables quietly age into bad debts — the single biggest cash
  leak in this business.
- **Why it pays:** every recovered 30-day+ due is found money; aging visibility
  alone changes collection behaviour.
- **Scope (M):** separate `credit_followup` rows (one live per doc) with
  receivable aging query (0–15/16–30/31–60/60+); defaulter view per customer
  with total exposure; reminder log. Sends through the message provider
  (FEAT-36), but the tracker alone already pays.

### FEAT-36: Message provider seam + payment reminders (WhatsApp/SMS)
- **Roles:** All (system-sent); Owner configures.
- **Problem today:** the only outbound channel is a log-stub email. Owners,
  drivers and fleet managers live on WhatsApp; DAMS cannot reach them.
- **Why it pays:** the enabler for FEAT-35 reminders, FEAT-39 renewals,
  FEAT-42 digest and "received ₹X, balance ₹Y" confirmations that kill
  payment disputes at the counter.
- **Scope (M):** provider interface mirroring `StorageService` (WhatsApp
  Business API first, SMS fallback), own template store, per-message
  audit rows, org-level opt-in. Ships with a logging sender until provider
  keys are configured.

### FEAT-37: Duplicate bill-photo detection (SHA-256 on upload)
- **Roles:** Accountant, FM (warned); Cashier (warned at upload).
- **Problem today:** the risk score lists "duplicate bill hash" in prose but
  nothing computes it. The cheapest fraud in the book is one bill photo
  attached to two expense claims.
- **Why it pays:** exact-duplicate detection is near-zero false positives and
  catches both fraud and honest double-entry.
- **Scope (S):** hash on upload, unique-per-org lookup, non-blocking warning
  badge + audit note on match. (Near-duplicate image matching is a later,
  harder step — do not promise it here.)

### FEAT-38: Claim next-action tracker (owner + due date + escalation)
- **Roles:** FM (owns), Owner (sees slippage).
- **Problem today:** aging buckets show *old* claims; nothing records the
  *next step* (call Eicher Tuesday, upload missing LR copy). Claims die from
  neglect, not age — and a written-off claim is pure loss.
- **Why it pays:** converts the existing aging display into a collection
  workflow; complements the in-progress Claim Chaser drafts with
  accountability.
- **Scope (S–M):** `claim_action (job_card, action, owner, due_date, done)`
  + overdue derivation + chase page. No AI needed.

## Tier 2 — revenue and control

### FEAT-39: AMC / service renewal reminders (brings vehicles back)
- **Roles:** Owner, FM, Accountant, Cashier (branch-scoped floor + renewals).
- **Problem today:** AMC job cards carry dates, but expiry passes silently.
  A lapsed AMC is lost recurring revenue walking to a competitor.
- **Why it pays:** renewal + "service due" nudges to fleet owners directly
  fill bays. Uses the vehicle/customer masters already present.
- **Scope (S):** expiry-derived reminder list + FEAT-36 templates. Smallest
  build with direct revenue attribution in this list.

### FEAT-40: UPI/bank statement reconciliation (UTR auto-match)
- **Roles:** Accountant (daily), FM (exceptions).
- **Problem today:** QR/UPI lines are typed in with a UTR and reconciled by
  eye against statements. Unmatched money sits unexplained; shortfalls surface
  weeks later.
- **Why it pays:** turns the most tedious daily accountant task into an
  exception list; catches mis-postings and missing credits fast.
- **Scope (M):** statement CSV upload → UTR/amount/date match against
  settlement lines → matched / amount-mismatch / unmatched-both-sides lists.
  No bank integration needed in v1 — file upload is enough.

### FEAT-41: Large-variance countersign (second pair of eyes on cash)
- **Roles:** Cashier (counts), Accountant (countersigns).
- **Problem today:** the cashier counts, reports and closes their own day;
  variance is self-declared text. In a cash business that is the weakest
  control in the building.
- **Why it pays:** a countersign above a master-configured threshold (e.g.
  |variance| > ₹2,000) makes shortages visible the same night, while honest
  cashiers welcome the witness.
- **Scope (S):** threshold in org Settings; close with breach → `PENDING`
  state; accountant confirms or queries. No workflow change below threshold.

### FEAT-42: Owner's nightly WhatsApp digest (the brief that gets read)
- **Roles:** Owner.
- **Problem today:** the morning brief (FEAT-10) lives in a dashboard the
  owner opens rarely. The evening numbers travel by phone call today.
- **Why it pays:** collections/expenses/net, unclosed branches, big variances,
  oldest claims — pushed at 8pm where the owner already is. Reuses brief
  aggregates; needs FEAT-36.
- **Scope (S after FEAT-36):** scheduled digest composer + send log. Explicitly
  read-only push — no commands over chat in v1.

### FEAT-43: Shareable customer ledger (fleet-owner statement)
- **Roles:** Cashier (shares at counter), Owner (sends for collection).
- **Problem today:** fleet owners ask "what do we owe across all our
  vehicles?" and the answer is read out from a screen. The thermal slip
  (FEAT-01/27) covers one payment; nothing covers the account.
- **Why it pays:** one-tap statement (dues + payments + balance, WhatsApp or
  print, reusing the slip renderer) shortens every collection call and proves
  professionalism to fleet customers choosing between workshops.
- **Scope (S–M):** ledger query across the job card anchor + statement
  renderer + `wa.me` share. No new data model.

## Tier 3 — solid operational value, moderate build

### FEAT-44: Staff advance / IOU ledger (given vs recovered)
- **Roles:** Cashier (records), Accountant (verifies), Owner (sees exposure).
- **Problem today:** staff advances live in a notebook or memory; recovery
  from wages is disputed. `Daily Wages` exists as a category — advances
  against it do not.
- **Why it pays:** small balances, constant friction; a ledger with
  outstanding-per-staff ends the disputes.
- **Scope (S):** advance/recovery entries linked to a staff master,
  outstanding view. Keep separate from customer flows.

### FEAT-45: Auditor (CA) read-only role
- **Roles:** New: AUDITOR (org-wide read + export, zero writes).
- **Problem today:** the CA gets Excel dumps or someone's login. Both are bad.
- **Why it pays:** removes a real compliance awkwardness at negligible build
  cost; every org already has a CA asking for this at year-end.
- **Scope (S):** role + read-only guards + export rights. No new screens
  beyond access control.

### FEAT-46: B2B credit limits + exposure alerts
- **Roles:** Owner (sets), Cashier (warned), Accountant (sees).
- **Problem today:** B2B dues accumulate per customer with no ceiling; the
  counter keeps extending credit past any sane limit.
- **Why it pays:** a soft block ("exposure ₹1.9L vs limit ₹2L") at posting
  time prevents the catastrophic default, not just reports it.
- **Scope (S–M):** limit per customer + exposure computation. Warn-first in
  v1 (banner + defaulter ranking, posting never blocked); a block needs an
  Owner override path — v2.

### FEAT-47: Expenses-awaiting-bills follow-up list
- **Roles:** Accountant.
- **Problem today:** the `Awaiting Receipt` status exists but nobody herds
  it; month-end becomes a bill-chasing scramble and Tally entries stay
  provisional.
- **Why it pays:** a simple aging list of bill-less expenses converts a
  monthly fire-drill into a daily 5-minute habit.
- **Scope (S):** filtered view on existing status + age + owner/cashier nudge.

## Tier 4 — big bets (genuine value, honest cost — do last)

### FEAT-48: Estimate/quote vs final bill (kill billing disputes)
- **Roles:** Cashier/Service advisor (quotes), Customer (approves), FM (large quotes).
- **Problem today:** job cards carry only the final invoice; the number the
  customer *agreed to* lives in conversation. Disputes erupt at payment time,
  when leverage is worst.
- **Why it pays:** approved estimates with line variance at billing end the
  most unpleasant counter conversation in the business.
- **Scope (L):** estimate entity + approval flow + variance report. New
  workflow surface — prototype with one branch first.

### FEAT-49: Offline-tolerant cash entry (queue-and-sync)
- **Roles:** Cashier.
- **Problem today:** no network = no records = end-of-day reconstruction from
  memory (the exact Excel-era failure mode DAMS exists to kill).
- **Why it pays:** continuity during outages; but note honest limits —
  numbering, maker-checker and locks are server-side, so offline entries must
  stay clearly provisional until synced and numbered.
- **Scope (L):** outbox in local storage + conflict/repair UX. Only after the
  core is stable; half-built offline is worse than none.

### FEAT-50: Floor / WIP board (which vehicles sit unbilled, and since when)
- **Roles:** Owner, FM, Accountant, Cashier (branch-scoped).
- **Problem today:** `business_status` (WIP/Hold) exists per job card but no
  view answers "what's in the bays right now, and what's stuck?" Idle bays
  and forgotten WIP are unbilled revenue rusting outdoors.
- **Why it pays:** WIP aging + stuck-reason tracking raises bay throughput
  without hiring anyone.
- **Scope (M):** read-mostly board over existing statuses + stuck-reason +
  age. Explicitly NOT full workshop management (no scheduling, no inventory)
  — stop at visibility.

## Explicitly NOT proposed (and why)

- **Spare-parts inventory:** a whole second product; the counter-sale receipt
  already covers the money side.
- **Payroll/HR, fuel management, GPS/telematics:** adjacent businesses, not
  this product.
- **Customer mobile app:** WhatsApp templates (FEAT-36) reach drivers and
  fleet owners with zero installs.
- **LLM chatbots / voice entry:** the rule-based AI already answers from
  grounded aggregates; free-text magic adds hallucination risk where money
  figures are quoted.
- **Two-way Tally sync:** AGENT.md decision #5 stands — CSV out is enough for v1.

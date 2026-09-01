# DAMS — Build Plan

> Source of truth alongside AGENT.md. All decisions here are locked unless
> AGENT.md is updated first.

---

## Revision log

- **rev 18 (2026-08-31)** — Global search on the Accountant & Finance Manager home screens
  (frontend only — `/api/v1/search` and `/api/v1/customers/{id}/history` are already open to
  all roles, and search is already branch-scoped per role). New
  `frontend/src/shared/GlobalSearch.tsx`: compact search bar in the blank strip under the page
  subtitle, same debounced `searchApi.query` the cashier uses, results dropdown, and a
  **read-only** customer drawer (shared `Modal`, new optional `maxWidth` prop → 720) showing
  totals / job cards / payment timeline + a per-job-card "View documents" button
  (`ViewReceiptsModal`). No create / pay actions for reviewers. Wired into `ReviewQueuePage`
  and `FmQueuePage` under the subtitle. Cashier home untouched.

- **rev 17 (2026-08-31)** — CI/CD deploy pipeline (no app code change). Owner chose:
  auto-deploy on push to `main`, health-check with automatic rollback to the previous GHCR
  image, nginx + certbot on the VPS, single domain with `/api` proxied, private GHCR images.
  - `ci.yml`: `images` job now passes `VITE_API_URL` (a repo **variable**) as the frontend
    build-arg; the `deploy` placeholder is now a real job — scp `compose.prod.yml`, ssh in,
    `docker compose pull && up -d` at `sha-<commit>`, poll `/actuator/health` + frontend `/`
    for ~90s, and on failure re-launch the previously-running `sha-` tag and fail the run.
    `concurrency: deploy-production` serialises overlapping merges.
  - New `compose.prod.yml` (GHCR `image:` refs with `${DAMS_TAG:-latest}`, `env_file: .env`,
    localhost-only ports, healthchecks), `deploy/dams.env.example` (VPS `/opt/dams/.env`
    template incl. `IMAGE_PREFIX` + R2), `deploy/nginx-dams.conf`, `deploy/bootstrap-vps.sh`
    (one-time: docker + `deploy` user + nginx/certbot + server block), `docs/deployment-guide.md`
    (both paths — automated GitHub Actions **and** manual SFTP: 2A images-from-GHCR, 2B
    jar + static under systemd, 2C registry-free tarball — plus secret table, rollback, ops).
  - GitHub **secrets**: `DEPLOY_SSH_KEY` (dedicated ed25519), `DEPLOY_HOST`, `DEPLOY_USER`,
    `DEPLOY_KNOWN_HOSTS`. GitHub **variables**: `VITE_API_URL`, `DEPLOY_PATH`, `DEPLOY_PORT`.
    Neon/JWT/R2 stay only in the VPS `.env`. `compose.prod.yml config` renders clean.

- **rev 16 (2026-08-31)** — UI polish pass + Help Center rewrite (frontend only, no
  backend, no migration). Owner asked for a friendlier, more polished feel and flagged the
  Help drawer opening wrong / reading thin.
  - **Motion foundation** in `styles/globals.css`: motion tokens (`--dur*`, `--ease`),
    keyframes (fade / slide-up / slide-in-right / scale-in / notice / route / toast /
    spin / shimmer + the `-out` pair), and interaction polish **scoped under `.dams-app`**
    (added to the `AppShell` root) — button hover-brightness + active-press, real
    `:focus-visible` outlines, a soft focus ring on text fields, topbar-nav hover on
    inactive links, quiet table-row hover. `prefers-reduced-motion` guard neutralises all
    of it.
  - **Component entrances**: `ui.tsx` `Modal` (backdrop fade + panel slide-up, now on
    `--shadow-lift`); `AccountMenu` dropdown scale-in; `AppShell` wraps `<Routes>` in a
    `key={location.pathname}` `.dams-route-fade` div so each screen fades in on arrival
    (keyed on path only — an in-place `?editDoc=` replace doesn't re-fire).
  - **Skeletons / spinners**: new `Skeleton` / `SkeletonRows` / `Spinner` exports in
    `ui.tsx` (`.dams-skeleton` shimmer, `.dams-spinner` ring). Every bare `Loading…` swapped
    for a shaped skeleton — Dashboard, both review queues (list + detail), My Entries,
    Cash, Override Audit, Organizations, Cashier home + history, ViewReceiptsModal. Submit /
    Resubmit / Upload buttons show `<Spinner/> …ing…` while busy. `ErrorBanner` + the
    `notice` / `flash` banners animate in (`.dams-anim-notice` / `.dams-anim-toast`).
  - **Help drawer layout fix** (`HelpDrawer.tsx` + `.dams-help-body`): panel
    `overflow:hidden`, both panes `min-width/min-height:0` with their own scroll, responsive
    width (`min(760px,100%)`, nav `clamp(180px,32%,240px)`), slide-in / slide-out with a
    brief post-close mount, and `pre` / a `table` wrapper get `overflow-x:auto` so wide
    content scrolls instead of shoving the article off-screen (the "cut off" bug).
  - **Help content rewrite**: all 19 task articles rewritten in a fuller, plain-language
    voice (what/when → steps with the *why* → what happens next / common mistakes); stale
    lines fixed (e.g. cashier *finding-a-customer* "next release"). New **"Your role in
    DAMS"** orientation article per role (money lifecycle receipt → review → approval →
    claim close), added as the first manifest entry for every role.
  - **Follow-up fixes (same rev):**
    - **Settings page** rebuilt: a read-only account summary card at the top, then each
      setting as a click-to-expand "menu item" (`Collapsible`) — Change password collapsed
      by default, Organization open for the owner. Was two always-expanded cards on a
      narrow column.
    - **Documents panel** on New Receipt / New Expense: `DAMS-Receive-ID` /
      `DAMS-Expenses-ID` stays as the last row of the right header column. The **Documents**
      control (plain — no box) now renders **inside that right column, directly below the ID
      field**, filling the whitespace the shorter right column used to leave. (Iterations:
      full-width row → reverted; boxed full-width block → reverted; final = compact, in-column
      under the ID, per owner's screenshot markup.)
  - Frontend `lint` / `build` / `vitest` green.

- **rev 15 (2026-08-30)** — Stages 8.5 / 9 / 10 / 11 built; V20 demo seed dropped;
  API-performance pass.
  - **Stage 8.5 (Cash review)** and **Stage 9 (Owner dashboard)** built and running against
    Neon (all 125 backend tests green). No migration.
  - **Stage 10 (Super Admin panel)** — `OrganizationsPage` reworked onto the shared
    `shell/ui` primitives: `ErrorBanner`, created-date + cashier-access columns, `Badge`
    status, a type-name-to-confirm **delete modal** (was `window.prompt`), "Copied ✓"
    feedback, per-row busy guards. No new backend — Stage 1 admin endpoints already cover it.
  - **Stage 11 (Help Center)** — framework only (no backend, no migration):
    `help/manifest.ts` (role → ordered articles + `import.meta.glob` bundling every
    `help/<role>/*.md` as raw text), `help/HelpDrawer.tsx` (right-side slide-over: search +
    role TOC + `react-markdown`/`remark-gfm` renderer), `.dams-help-body` typography in
    `globals.css`, a **? Help** button wired into the shell header. All 19 stubs from
    Stages 2–10 are wired; the missing `super-admin/onboarding-an-organization.md` was
    written. **Decision (owner, rev 15): Help stays text-only — no screenshots.** Also not
    done: per-screen contextual `?` deep-links (deferred).
  - **V20 demo seed — dropped.** Owner asked to skip it. Testers add their own data; the
    dashboard windows to the current month so it reads near-zero until they do.
  - **Default masters on org creation.** `MasterProvisioningService` seeds a new org, in
    `AdminOrgService.createOrganization`'s transaction, with the same master catalogue the
    demo dealership ships with (11 receive categories, 7 receive statuses, 7 settlement
    modes, 4 expense categories + 13 sub-categories, 3 expense modes, 6 expense statuses, 6
    banks) — flags included (`is_cash`, `is_claim`, `triggers_claim`). A freshly-onboarded
    Owner now has working dropdowns on day one instead of empty ones. Verified end-to-end
    on Neon (create → 57 master rows → delete).
  - **Bug fix — org purge FK order.** `MastersService.purgeOrg` deleted `expense_category`
    before its `expense_sub_category` children → `DELETE /admin/organizations/{id}` failed
    with a 409 FK violation for any org that had sub-categories (latent: only the
    never-deleted demo org had them before). Now deletes sub-categories first.
    `MastersServicePurgeTest` guards the order.
  - **Auto-docs confirmed live** — springdoc-openapi, `GET /api-docs` (74 paths),
    `/swagger-ui.html`. Added `OpenApiConfig` (the `bearerAuth` scheme every controller
    references was never defined, so Swagger's Authorize button had no field) + real API
    title; `springdoc.swagger-ui.persist-authorization: true`; `local` profile issues a
    30-day JWT so Swagger / the app don't log you out mid-session.
  - **Attachments UI (New Receipt + New Expense).** `AttachmentsPanel` — a "Documents"
    section under DAMS-Receive-ID, always visible. Multi-file pick; each file tagged to the
    whole document ("top level") or one settlement/expense line ("sub-transaction") via a
    per-file dropdown; 10 MB/file, PDF or image (mirrors the backend). On a not-yet-saved
    form the first upload calls `ensureDraft()` (saves the draft in place, no navigation)
    so there's an id to attach to. List shows each doc with its tag, a **View** (signed
    URL, new tab) and **Remove**. Frozen (settled/closed/rejected) → read-only. Backend
    (`ReceiveDocumentController` / `ExpenseDocumentController` upload endpoints,
    `AttachmentService`, `receiptsApi`/`expensesApi` client methods) already existed —
    this is the missing UI. Verified end-to-end via the API (upload doc + line, list,
    signed-URL fetch 200, delete).
  - **Bug fix — signed attachment URL port.** `LocalFilesystemStorageService` built the
    `/attachments/raw` link from `dams.app.base-url` with a hardcoded `:5173`→`:8080`
    rewrite; the `APP_BASE_URL` → `:2314` change broke it (link pointed at the frontend).
    Now uses a dedicated `dams.storage.local.public-base-url` (default `http://localhost:8080`).
  - **`dams.bat`** at repo root — kills ports 8080/2314, loads `.env`, launches backend
    (`:8080`) and frontend (`:2314`, `--strictPort`) each in its own window. `.env`
    `CORS_ALLOWED_ORIGINS` now lists both `:5173` and `:2314`; `APP_BASE_URL` → `:2314`.
    R2 storage keys (`DAMS_STORAGE_PROVIDER` + `R2_*`) added to `.env` / `.env.example`,
    empty — flip `DAMS_STORAGE_PROVIDER=r2` and fill them to switch attachment storage.
  - **API-performance pass (no schema change, Neon stays).** The Owner dashboard was firing
    ~95 DB round-trips (drawer position recomputed per branch ~6×; one pending-review count
    per branch per doc type); now ~25. Measured against Neon `ap-southeast-1`:
    `/dashboard/summary` 8.0 s → 2.0 s, `/dashboard/outstanding` 3.5 s → 0.7 s,
    `/my-entries` 2.9 s → ~1.0 s, `/review/fm/receipts` 2.2 s → 1.4 s.
    - `DrawerService.computedPositions(orgId, branchIds, date)` — one batched roll-up for
      all branches (2 mode lookups + 4 group-by-branch sums + openings) instead of ~8
      queries each.
    - `DashboardService.summary` / `outstanding` — batch-load branches, job cards,
      customers, vehicles, categories, and grouped pending-review / last-close maps once;
      new `countPendingReviewByBranch`, `sumAmountByJobCard`, `sumCashModeByBranchForDate`,
      `sumByBranchForDateAndDirection` repo queries; `PendingAmountCalculator.forJobCards`
      (batch, 2 queries).
    - `MyEntriesService` + `ReviewService` queues — per-id lookup loops replaced with
      `…IdIn` batch queries and full reference maps.
    - `application.yml` — HikariCP pool sizing + keepalive, Hibernate
      `default_batch_fetch_size` / `jdbc.batch_size` / autocommit-off.
  - **Frontend robustness.** `ErrorBanner` now `scrollIntoView`s when a message appears, so
    a failed Submit at the bottom of a long form no longer looks like a dead button. Nav
    audit re-run — all in-app `navigate()` targets are `/app`-prefixed (the earlier bounce
    to `/login` is gone).

- **rev 14 (2026-08-30)** — Stage 8 (FM approval, claim closing, Override Audit) built +
  verified on Neon.
  - **V19** — `audit_event.branch_id` (nullable, FK), backfilled from each row's document
    (receive / expense / cash / job card), + a `(org_id, event_type, created_at DESC)`
    index. `AuditService` gains a branch-aware overload; **review and claim-close events
    set it**, older maker-side events (CREATED / SUBMITTED / LINE_ADDED / SETTLED) stay
    NULL until a later stage wires them — the backfill covers history.
  - **FM approval** lives in `com.dams.review`: `ReviewGuard.requireFinanceManager`;
    `ReviewService.approve{Receipt,Expense}` (VERIFIED → APPROVED); the existing
    `query`/`reject` now **dispatch on role** — Accountant acts from SUBMITTED, FM from
    VERIFIED, both land on QUERIED / REJECTED (one endpoint each, `POST
    /{receipts,expenses}/{id}/query` · `/reject` · `/approve`). FM queue:
    `GET /api/v1/review/fm/{receipts,expenses}` → `{ awaitingApproval, openClaims,
    recentlyClosed }` (last two receipts-only).
  - **Claim close** — `ClaimCloseService` + `POST /api/v1/job-cards/{id}/close-claim`
    `{finalAmount, reason?}`. FM-only; category must be a claim category; no existing
    ClaimClose; **every non-REJECTED receive document on the job card must be APPROVED**
    (409 otherwise). One transaction writes the immutable `claim_close` row **and** flips
    `settled = true` on every open receive document (freezing their receipts), audits
    SETTLED per doc + CLOSED on the job card. `overridden = finalAmount ≠ Σ lines`; reason
    required when overridden. Post-close locks (already wired Stage 4, now covered by
    tests): `PATCH /job-cards` category/status → 409, `POST /receipts` → 409,
    `pending_amount` = 0.
  - **Decisions (confirmed):** FM query/reject → **QUERIED** (back to the cashier), not the
    mockup's SUBMITTED. close-claim needs all receive docs **APPROVED**. FM maker-checker
    is against `created_by` / `last_modified_by` only (role separation already guarantees
    three people).
  - **Override Audit** — `GET /api/v1/override-audit?userId=&branchId=&from=&to=` (OWNER +
    FM). One feed merging the Accountant's line overrides (`OVERRIDE` audit rows) **and**
    the FM's claim-close overrides (`claim_close.overridden` — `kind: "claim"`,
    `amountBefore` = Σ received, `amountAfter` = final amount). Dates default to the last 90
    days, resolved in the org timezone.
  - `JobCardResponse` + `ReceiveDocumentResponse` gain `claimOverridden` /
    `claimOverrideReason` (+ `claimClosedByName` / `claimClosedAt` on the job card) for the
    "Overridden · Final" summary.
  - Frontend: `review/reviewShared.tsx` (extracted `RecordCard` / `QueryRejectBox` so the
    Accountant and FM pages share the record view); `finance/FmQueuePage.tsx` (FM home —
    three-section queue, Approve / Query / Reject, Close-claim modal with live override
    detection, closed-claim summary); `overrideaudit/OverrideAuditPage.tsx`
    (`/override-audit`, nav for OWNER + FM — filter by date / branch / user, merged table).
    `api/review.ts` (+`approve`, `fmQueue`), `api/overrideAudit.ts`, `api/jobCards.ts`
    (+`closeClaim`). *Known gap:* the user filter's dropdown is Owner-only (`GET /users` is
    OWNER-gated); FM filters by date + branch.
  - Help stubs: `finance-manager/approving-entries.md`, `closing-a-claim.md`,
    `override-audit.md`.
  - Tests: backend **118 pass / 2 skipped** (`ReviewGuardTest` +2, `ReviewServiceTest`
    reworked to 19 incl. FM cases, `ClaimCloseServiceTest` ×7, `OverrideAuditServiceTest`
    ×2). Frontend tsc · lint · vitest · build green.

- **rev 13 (2026-08-30)** — Stage 7 (Accountant review queue) built + verified on Neon.
  **No migration** — the line override columns (V11/V14), the workflow-status and
  `audit_event.event_type` CHECKs, and the `(org_id, branch_id, workflow_status)` queue
  indexes were all provisioned in earlier stages.
  - New `com.dams.review` package — `ReviewGuard` (ACCOUNTANT only, branch in scope,
    maker-checker: actor ≠ `created_by` and ≠ `last_modified_by`), `ReviewService`
    (verify / query / reject / line-override on SUBMITTED receipts and expenses; explicit
    expense `close`), `ReviewController`.
  - **Review actions never touch `last_modified_by`** — it tracks the maker's last edit, so
    one accountant may override a line and still verify the same document (matches
    `review-close.html`). The override trail is the line's own `original_amount` /
    `overridden_by` / `overridden_at` columns plus an `OVERRIDE` audit row (detail carries
    `amountBefore/After`, and for expenses `overLimitBefore/After` + a human summary).
  - **`closeExpense`**: allowed from VERIFIED or APPROVED — **but an over-limit expense
    requires APPROVED** (FM sign-off), so it cannot be closed until Stage 8 ships the FM
    approve step. Intended. Ordinary expenses still close from VERIFIED.
  - **`overrideExpenseLine` recomputes `over_limit`** via the same
    `ExpenseDocumentService.recomputeOverLimit` the cashier-side edits use (made public);
    `overrideReceiptLine` re-runs auto-settle via a new `ReceiveDocumentService.refreshSettlement`.
  - **Detail GET is now branch-scoped for every role** (`ReceiveDocumentService.get` /
    `ExpenseDocumentService.get` → `BranchScope.canSeeBranch`): a cashier sees only their
    home branch (org-wide with the multi-branch toggle), an accountant their assigned
    branches, FM / Owner everything. Previously any signed-in org user could GET any
    document by id.
  - **`history` on both document responses** — an oldest-first list of humanised audit
    events (`DocumentHistoryService`), driving the review pane's History card and the
    cashier's "Query from the accountant" banner on a queried entry.
  - Mockup deviations (flagged, built per AGENT.md): expense-close exists though
    `review-close.html` has no UI for it; query sets status to **QUERIED** (not the
    mockup's `SUBMITTED`) to match the Stage-4/6 resubmit loop; no Accountant/FM role
    toggle; the mockup's "Date of Inception" header field stays dropped (rev 10).
  - Frontend: `accountant/ReviewQueuePage.tsx` (`/`, Accountant home + "Review Queue" nav)
    — two-pane queue + overview + record detail with inline override / query / reject
    boxes; `api/review.ts`. Query-note banner added to New Receipt / New Expense.
  - Help stubs: `accountant/reviewing-the-queue.md`, `overriding-an-amount.md`,
    `closing-an-expense.md`.
  - Tests: backend 102 pass / 2 skipped (`ReviewGuardTest` ×6, `ReviewServiceTest` ×14).
    Frontend tsc · lint · vitest · build green.

- **rev 12 (2026-08-30)** — Frontend date-display consistency (no backend / migration
  change). All dates render identically for every viewer regardless of their machine's
  timezone or locale:
  - Four helpers in `frontend/src/shell/ui.tsx` — `fmtDate` (`7 August 2026`),
    `fmtDateShort` (`7 Aug 2026`), `fmtDateTime` (`7 August 2026, 3:12 pm IST` — instants
    converted to Asia/Kolkata), `istToday` (`yyyy-mm-dd` for India, for form defaults and
    `max=` guards).
  - Calendar dates are formatted from the ISO string's parts, never via `new Date(str)`
    (which parses as UTC midnight and slips a day for viewers west of UTC).
  - Storage and the wire format stay ISO; `<input type="date">` stays native.
  - Call sites moved off `new Date().toISOString().slice(0,10)` (was UTC "today", wrong
    for a cashier working the 00:00–05:30 IST window or overseas): New Receipt / New
    Expense line defaults, Add Payment, Cash page `todayStr`. Prose dates now via helper:
    Cash close-day modal, cashier-home payment timeline.

- **rev 11 (2026-08-30)** — Stage 6 (Cash document + day close) built + verified on Neon:
  - **`CashWorkflowStatus` is the full six** — DRAFT / SUBMITTED / VERIFIED / APPROVED /
    QUERIED / REJECTED, matching Receipt and Expense (plan.md's earlier 4-value note is
    superseded — there was no reason cash lacked a correction path). Cashier `resubmit`
    (QUERIED → SUBMITTED) and header edit while DRAFT / QUERIED are wired.
  - **`is_cash` flag on `settlement_mode` + `expense_mode`** (V16, back-filled): the drawer
    math asks the mode, never matches its name. Seeded true for **Cash** + **Adv-Cash**
    (settlement) and **Cash** (expense).
  - **`cash_document`** — one IN-from-bank / OUT-to-bank movement, single amount, no
    sub-lines, no customer/vehicle/job-card. `{branch}-{MONKEY}-C-{seq}` on submit
    (`DocType.C`, gap-free). No branch field — posts under the cashier's home branch
    (`CashPostingGuard`).
  - **Drawer position** (`DrawerService`, the one implementation): `opening + cash-mode
    receipts + cash IN − cash-mode expenses − cash OUT` for a branch/date (Asia/Kolkata).
    Counts every non-DRAFT, non-REJECTED contributor — the cash is physically in the drawer
    regardless of review state; REJECTED is the one exclusion (returned / never received),
    stated as a code comment. Opening chain: latest `cash_day_close.counted_amount` before
    the date → else the Accountant's one-time `branch_cash_opening` → else 0 with
    `openingSet = false`.
  - **`cash_day_close`** — counted amount, auto `variance = counted − computed`,
    `variance_remark` mandatory when non-zero, future dates and re-closing refused. Its
    existence locks the `(branch, date)`.
  - **Lock widened (this was a real gap).** A close doesn't only block new `cash_document`
    rows — a new `CashDateLock` (depends only on `CashDayCloseRepository`, no service cycle:
    `DrawerService` reads receive/expense *repos*, and those services read `CashDateLock`)
    is consulted by `ReceiveDocumentService` and `ExpenseDocumentService`: any **cash-mode**
    settlement or expense line dated on/before the branch's latest close — added, edited, or
    deleted — is refused (409). Non-cash lines on a closed date are still fine.
  - **`branch_cash_opening`** — ACCOUNTANT only, one per branch ever, for a branch within
    their access.
  - **My Entries** now merges receipts + expenses + **cash** (`kind = "CASH"`, "Cash IN /
    OUT from bank"); a queried cash movement opens at `/cash?editDoc=` for fix-and-resubmit.
  - `audit_event` CHECK unchanged (CLOSED covers the day-close; CREATED / SUBMITTED cover
    movements). `OrganizationPurgeService` extended (cash documents / closes / openings).
  - Frontend: `CashPage` (`/cash`, `Cash` in the cashier nav + a home quick-card) — drawer
    breakdown card, movements table, Cash In / Cash Out modal, Close Day modal (live
    variance, conditional remark), `?editDoc=` fix-and-resubmit. Built from AGENT.md — no
    HTML mockup for this screen.
- **rev 10 (2026-08-30)** — Stage 5 (Expense document) built + verified on Neon:
  - **Three master-flag gaps in plan.md, folded in as V13** (flag columns, not name matching —
    AGENT.md "nothing hard-coded"): `expense_mode` gains `requires_bank` / `requires_ref`
    (the same flags `settlement_mode` already had — the expense form asks the mode, never
    matches its name); `expense_business_status` gains `triggers_claim`, marking the one
    status ("Transfer to Claim") the service keys on. Demo rows back-filled.
  - **`date_of_inception` dropped** — same call as Stage 4's "Date of Entry": each line has
    its own `transaction_date`, `created_at` covers "when entered". Not in the schema.
  - **`POST /api/v1/expenses`** — receiver by `receiverId` OR inline `receiverName` (deduped
    on name, like Customer); optional `jobCardId`; `lines[]`; optional `submit`. **No branch
    field** — posts under the cashier's home branch. `ExpensePostingGuard` mirrors the
    receive-side cross-branch block (409 naming both branches); when a `jobCardId` is given it
    must be in the cashier's home branch.
  - **New Expense always creates a fresh document** — no one-open-doc invariant (that exists
    on the receive side only to keep Pending Amount unambiguous; expenses aggregate toward
    nothing).
  - **`over_limit`** — stored on the document, recomputed on every line change: true when any
    line's amount exceeds its sub-category `limit_amount`. Flags, never blocks. Per-line
    `overLimit` is also in the response for the row warning.
  - **Lines stay addable until CLOSED** — Add Expense (`POST /expenses/{id}/lines`) is refused
    only once the document is CLOSED (Accountant, Stage 7) or REJECTED. `ExpenseWorkflowStatus`
    carries the extra `CLOSED` value.
  - **Transfer to Claim** (`POST /expenses/{id}/transfer-to-claim`, and the plain create/patch
    path) — allowed only when the expense sits on a job card whose category is a claim category
    (`receive_category.is_claim`); else 400. Sets the business status to the `triggers_claim`
    row; audited as a new `TRANSFERRED_TO_CLAIM` event (audit CHECK extended in V14).
  - **Doc numbers** — `{branchCode}-{MONKEY}-E-{seq:03d}` (`OOR-AUG26-E-001`), same gap-free
    machinery and on-submit-only timing as the receive side; line ids `{docNo}-L{n}`.
  - **My Entries now merges receipts + expenses** — `MyEntryResponse.kind` is `RECEIPT` |
    `EXPENSE`, sorted newest-first across both; `overLimit` flag for expenses. Seed has an
    over-limit expense (E-002), an unnumbered draft, a CLOSED one, and one at "Transfer to
    Claim" (E-001, on the Kalaivani AMC job card).
  - **Universal search now matches `document_no`** (receive + expense), branch-scoped,
    resolved to the job card's customer — the plan.md cross-cutting search spec listed it but
    Stage 4 hadn't wired it. A standalone overhead expense (no job card) has no customer, so
    its number surfaces via the Accountant queue (Stage 7), not here.
  - `AttachmentService` handles `EXPENSE_DOCUMENT` / `EXPENSE_LINE` parents (freeze on
    close/approve, Stage 7). `OrganizationPurgeService` extended (expense lines / docs).
  - Frontend: `NewExpensePage` (`/new-expense`, incl. `?customerId=` / `?jobCardId=` prefill
    and `?editDoc=` resubmit mode), category→sub-category cascade, per-line limit warnings,
    Transfer to Claim button; `MyEntriesPage` shows both kinds; Cashier home "New Expense"
    (quick-action + history card) wired.
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
  - **Attachments.** `StorageService` interface + two backends, chosen by
    `dams.storage.provider`: `local` (default) — `LocalFilesystemStorageService`, real disk
    store, HMAC-signed 10-min URL served from `/api/v1/attachments/raw` (permit-all — the
    signature is the auth); `r2` — `R2StorageService` over the AWS S3 v2 SDK
    (url-connection-client, Netty/Apache excluded), presigned GET URLs, config via
    `R2_ENDPOINT` / `R2_ACCESS_KEY_ID` / `R2_SECRET_ACCESS_KEY` / `R2_BUCKET`. R2/S3 types
    stay inside that one class.
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
| V13 | `expense_mode` add `requires_bank` / `requires_ref`; `expense_business_status` add `triggers_claim`; back-fill demo rows (form asks the mode/status, never matches its name) | 5 ✅ |
| V14 | `expense_document` (incl. `over_limit`, `last_modified_by`), `expense_line`; `audit_event` CHECK += `TRANSFERRED_TO_CLAIM`; `lower(document_no)` indexes on `receive_document` + `expense_document` | 5 ✅ |
| V15 | **seed** — 5 expense documents (over-limit, unnumbered draft, CLOSED, Transfer-to-Claim, within-limits multi-line), JUL26 `E` `document_sequence` rows | 5 ✅ |
| V16 | `settlement_mode` + `expense_mode` add `is_cash`; back-fill Cash / Adv-Cash (drawer math asks the mode, never matches its name) | 6 ✅ |
| V17 | `cash_document` (single-movement), `branch_cash_opening`, `cash_day_close` | 6 ✅ |
| V18 | **seed** — OOR opening ₹10,000, cash docs C-001…C-004 (one QUERIED), one historical `cash_day_close` | 6 ✅ |
| V19 | `audit_event` add `branch_id` (nullable, FK), backfilled from each row's document; index `(org_id, event_type, created_at DESC)` for Override Audit | 8 ✅ |
| ~~V20~~ | **dropped (rev 15)** — full demo seed. Owner asked to skip; testers add their own data. | — |

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
- `StorageService` — `LocalFilesystemStorageService` (default; HMAC-signed URL via `/api/v1/attachments/raw`) and `R2StorageService` (`dams.storage.provider=r2`; AWS S3 v2 SDK, presigned URLs). `attachment` rows, signed-URL retrieval, freeze-on-settle. `AttachmentServiceTest`.
- `created_by` + `last_modified_by` on every mutation (feeds maker-checker in Stage 7).
- `GET /api/v1/my-entries` — caller's own docs newest-first, `today` / `queried` flags. Seed has a QUERIED doc (Suresh, R-013) for the fix-and-resubmit demo.
- `OrganizationPurgeService` extended (attachments / settlement lines / claim closes / receive docs, FK-safe). `GlobalExceptionHandler`: `DataIntegrityViolation` → 409, upload-too-large → 400.
- Frontend: `NewReceiptPage` (`/new-receipt`, incl. `?editDoc=` resubmit mode), `AddPaymentModal`, `ViewReceiptsModal` (button, not thumbnails), `MyEntriesPage` (`/my-entries`); Cashier home wired (quick-action row + real Add Payment / View Receipts / history figures / search outstanding).
- Help stubs: `cashier/creating-a-receipt.md`, `cashier/adding-a-payment.md`, `cashier/fixing-a-queried-entry.md`
- Tests: backend 56 pass / 2 skipped (Testcontainers — run in CI). Frontend tsc · lint · vitest · build all green.

### Stage 5 — Expense document ✅ (verified on Neon)
**Demo:** Cashier creates an expense (inline receiver, optional job-card tag), picks category/sub-category, sees the limit warning, submits. Add Expense appends lines until the Accountant closes it. Transfer-to-Claim works on warranty/AMC/goodwill job cards. My Entries lists receipts + expenses together.

- V13 (`expense_mode` + `requires_bank` / `requires_ref`; `expense_business_status` + `triggers_claim`; demo rows back-filled) · V14 (`expense_document` incl. `over_limit` / `last_modified_by`, `expense_line`; `audit_event` CHECK gains `TRANSFERRED_TO_CLAIM`; `lower(document_no)` search indexes on both document tables) · V15 seed (5 expense docs — over-limit, unnumbered draft, CLOSED, Transfer-to-Claim, plus a within-limits multi-line; JUL26 `E` sequence rows). See rev 10.
- `POST /api/v1/expenses` — one flow: `receiverId` OR inline `receiverName` (deduped), optional `jobCardId`, `lines[]`, optional `submit`. No branch field — posts under the cashier's home branch. `PATCH /expenses/{id}` edits the header while DRAFT/QUERIED; `submit` / `resubmit` / `lines` (Add Expense) / line edit+delete; `transfer-to-claim`; document + line attachments.
- New Expense always creates a fresh document — no one-open-doc invariant (nothing aggregates toward a figure the way Pending Amount does).
- `over_limit` — stored, recomputed on every line change (`amount > expense_sub_category.limit_amount`); flags for FM approval, never blocks. Per-line `overLimit` in the response.
- Lines addable until CLOSED (Stage 7) or REJECTED. `ExpenseWorkflowStatus` adds `CLOSED`.
- Transfer to Claim — endpoint **and** the plain status path require the expense to sit on a claim-category job card (`receive_category.is_claim`), else 400. Sets the `triggers_claim` status; `TRANSFERRED_TO_CLAIM` audit event.
- Doc numbers — `{branchCode}-{MONKEY}-E-{seq:03d}` (`OOR-AUG26-E-001`), on submit, gap-free (`DocumentNumberService`, `DocType.E`); line ids `{doc}-L{n}`.
- Cross-branch write block — `ExpensePostingGuard` (mirrors `ReceivePaymentGuard`): `role == CASHIER`, home-branch only; a `jobCardId` must be in that branch. 409 names both branches.
- `GET /api/v1/my-entries` now merges receipts + expenses (`kind`), newest-first, `overLimit` flag. `SearchService` matches receive + expense `document_no` (branch-scoped → customer).
- `AttachmentService` handles expense parents (freeze on close/approve — Stage 7). `OrganizationPurgeService` extended (expense lines / docs).
- Masters: `expense-modes` expose `requiresBank` / `requiresRef`; `expense-statuses` expose `triggersClaim` (all editable via `/masters/{type}`).
- Frontend: `NewExpensePage` (`/new-expense`, incl. `?customerId=` / `?jobCardId=` prefill, `?editDoc=` resubmit), category→sub-category cascade, per-line limit warnings, Transfer to Claim button; `MyEntriesPage` shows both kinds; Cashier home "New Expense" (quick-action + history card) wired.
- Help stub: `cashier/recording-an-expense.md`
- Tests: backend 68 pass / 2 skipped (Testcontainers — CI). `ExpenseDocumentServiceTest`, `ExpensePostingGuardTest`, `AttachmentServiceTest` (+ expense cases). Frontend tsc · lint · vitest · build all green.

### Stage 6 — Cash document + day close ✅ (verified on Neon)
**Demo:** Cashier opens the Cash page, sees the live drawer position + breakdown, adds Cash In / Out movements (each its own C-numbered doc), closes the day with a variance remark. Closing locks the date against new cash movements *and* backdated cash-mode receipt/expense lines. My Entries lists receipts + expenses + cash together.

- V16 (`settlement_mode` + `expense_mode` `is_cash` flag, back-filled Cash / Adv-Cash) · V17 (`cash_document` single-movement + `branch_cash_opening` + `cash_day_close`) · V18 seed (OOR opening ₹10,000, four cash docs C-001…C-004 incl. a QUERIED one, one historical `cash_day_close`). See rev 11.
- `CashWorkflowStatus` = DRAFT / SUBMITTED / VERIFIED / APPROVED / QUERIED / REJECTED (full six, like every other document).
- `POST /api/v1/cash-documents` — one IN or OUT movement, single amount, no sub-lines, no branch field (posts under the cashier's home branch). `PATCH` (draft/queried) · `submit` · `resubmit` · delete-a-draft. Verify + approve land with the Stage 7 queue.
- `GET /api/v1/cash/drawer?branchId=&date=` — `DrawerService`: `opening + cash-mode receipts + cash IN − cash-mode expenses − cash OUT`, counting non-DRAFT/non-REJECTED contributors (a comment states the REJECTED-means-returned assumption). Response bundles the breakdown, the close state, and the day's movements.
- `POST /api/v1/cash/opening` — ACCOUNTANT, once per branch, for a branch in their access.
- `POST /api/v1/cash/close-day` — CASHIER: counted amount → auto `variance`; remark mandatory when variance ≠ 0; future date and re-closing refused. `cash_day_close` locks the `(branch, date)`.
- **`CashDateLock`** — a close refuses any *cash-mode* settlement / expense line dated on/before the branch's latest close (add / edit / delete), not just new cash documents. Small component, `CashDayCloseRepository` only — no service cycle.
- `is_cash` exposed on `settlement-modes` / `expense-modes` masters (Owner-editable).
- `GET /api/v1/my-entries` merges cash (`kind = "CASH"`); a queried cash movement opens at `/cash?editDoc=`.
- `OrganizationPurgeService` extended (cash documents / closes / openings).
- Frontend: `CashPage` (`/cash`, cashier nav + home quick-card) — drawer breakdown card, movements table, Cash In / Cash Out modal, Close Day modal (live variance, conditional remark), `?editDoc=` fix-and-resubmit. No HTML mockup — built from AGENT.md.
- Help stubs: `cashier/cash-in-and-out.md`, `cashier/closing-the-day.md`
- Tests: backend 82 pass / 2 skipped (Testcontainers — CI). `DrawerServiceTest`, `CashCloseServiceTest`, `CashDocumentServiceTest`. Frontend tsc · lint · vitest · build all green.

### Stage 7 — Accountant review queue ✅ (verified on Neon)
**Demo:** Accountant sees the queue, verifies / overrides / queries / rejects. Cashier sees queried items in My Entries and fixes + resubmits. Matches `review-close.html` Accountant view.

- No migration — override columns, workflow / event-type CHECKs, and queue indexes all pre-existed (V11/V14). See rev 13.
- `com.dams.review`: `ReviewGuard` (ACCOUNTANT + branch scope + maker-checker), `ReviewService`, `ReviewController`.
- Endpoints: `GET /api/v1/review/{receipts,expenses}`; `POST /api/v1/{receipts,expenses}/{id}/verify` · `/query` · `/reject` · `/lines/{lineNo}/override`; `POST /api/v1/expenses/{id}/close`. (`/resubmit` for CASHIER already shipped Stage 4/6.)
- Review actions do not set `last_modified_by`. `closeExpense` needs APPROVED when over-limit. Line override recomputes `over_limit` (expense) / re-runs auto-settle (receipt).
- Detail GET (`/{receipts,expenses}/{id}`) is now branch-scoped for every role via `BranchScope`.
- `history` (humanised audit events) added to both document responses.
- Frontend: `accountant/ReviewQueuePage.tsx` (Accountant home) — two-pane queue + overview + detail with inline override/query/reject; `api/review.ts`; query-note banner on New Receipt / New Expense.
- Help stubs: `accountant/reviewing-the-queue.md`, `accountant/overriding-an-amount.md`, `accountant/closing-an-expense.md`
- Tests: backend 102 pass / 2 skipped (`ReviewGuardTest`, `ReviewServiceTest`). Frontend tsc · lint · vitest · build green.

### Stage 8 — FM approval, claim closing, Override Audit ✅ (verified on Neon)
**Demo:** FM approves / queries / rejects. FM closes Warranty/AMC/CG claims via `POST /job-cards/{id}/close-claim` with an optional final override ("Overridden · Final" everywhere after). Owner/FM see the org-wide Override Audit screen. Matches `review-close.html` FM view.

- **V19** — `audit_event.branch_id` (nullable), backfilled; index for the override-audit query. See rev 14.
- **FM queue = every VERIFIED receipt and expense** ("Awaiting Final Approval"), plus (receipts) open claims + recently closed. `GET /api/v1/review/fm/{receipts,expenses}`.
- Endpoints: `POST /api/v1/{receipts,expenses}/{id}/approve`; `query` / `reject` now dispatch on role (Accountant SUBMITTED→QUERIED/REJECTED, FM VERIFIED→QUERIED/REJECTED); `POST /api/v1/job-cards/{id}/close-claim {finalAmount, reason?}` (FM only, claim category, all receive docs APPROVED, one txn: immutable `claim_close` + `settled=true` on every open doc + freeze + SETTLED/CLOSED audit; reason required when `finalAmount ≠ Σ lines`).
- Post-close locks (now test-covered): `PATCH /job-cards` category/status → 409, `POST /receipts` → 409, `pending_amount` = 0.
- **Override Audit** — `GET /api/v1/override-audit?userId=&branchId=&from=&to=` (OWNER + FM). Merges Accountant line overrides (`OVERRIDE` audit rows) **and** FM claim-close overrides (`claim_close.overridden`, `kind: "claim"`) in one newest-first feed; 90-day default.
- `JobCardResponse` / `ReceiveDocumentResponse` gain `claimOverridden` / `claimOverrideReason` (+ `claimClosedByName` / `claimClosedAt`).
- Decisions: FM query/reject → **QUERIED** (to the cashier). close-claim needs all receive docs **APPROVED**. FM maker-checker is against `created_by` / `last_modified_by` only.
- Frontend: `review/reviewShared.tsx` (`RecordCard` / `QueryRejectBox` shared by both queues); `finance/FmQueuePage.tsx` (FM home — 3-section queue, Approve/Query/Reject, claim-close modal, closed-claim summary); `overrideaudit/OverrideAuditPage.tsx` (`/override-audit`, OWNER + FM nav).
- Help stubs: `finance-manager/approving-entries.md`, `closing-a-claim.md`, `override-audit.md`
- Tests: backend 118 pass / 2 skipped (`ClaimCloseServiceTest` ×7, `OverrideAuditServiceTest` ×2, `ReviewServiceTest` FM cases, `ReviewGuardTest` FM). Frontend tsc · lint · vitest · build green.

### Stage 8.5 — Cash review ✅ (built; backend green on Neon)
**Why:** AGENT.md decision #1 says cash documents use "the same maker-checker workflow (Submitted → Verified → Approved) as every other document", and the plan's endpoint list has `POST /api/v1/cash-documents/{id}/verify` · `/approve`. Stage 6 built `CashWorkflowStatus` with all six values *and said "verify + approve land with the Stage 7 queue"* — then Stages 7–8 only wired receipts and expenses. A new cash document currently can't move past SUBMITTED. This closes that.

- **No migration** — `CashWorkflowStatus` and the `audit_event` CHECK already cover all six.
- `CashDocumentService` gains `verify` (ACCOUNTANT, SUBMITTED→VERIFIED), `approve` (FM, VERIFIED→APPROVED), and role-dispatched `query` / `reject` (Accountant from SUBMITTED, FM from VERIFIED → QUERIED / REJECTED) — reusing `ReviewGuard` (role + branch scope + maker-checker). Cash has no lines, so no override. Audit each transition with `branch_id`.
- Endpoints: `POST /api/v1/cash-documents/{id}/verify` · `/approve` · `/query` · `/reject`.
- Queue: a **Cash** tab in `ReviewQueuePage` (Accountant) and `FmQueuePage` (FM) alongside Receipts / Expenses — `GET /api/v1/review/cash` (SUBMITTED, in the accountant's branches) and the cash arm of `GET /api/v1/review/fm/cash` (VERIFIED, org-wide). A lightweight cash detail panel (direction / date / amount / bank / ref / remark + History + action bar) — `RecordCard` is line-oriented, cash gets its own compact view.
- Cashier side already works: the Cash page movements table shows the status badge and "Fix & Resubmit" on QUERIED; add the query-note banner to the movement modal (parity with New Receipt / New Expense).
- Tests: `CashDocumentServiceTest` gains verify / approve / query / reject cases (maker-checker, wrong-state, role).

### Stage 9 — Owner dashboard ✅ (built; backend green on Neon; perf-tuned rev 15)
**Demo:** Owner sees KPI cards, trend chart, donut, branch comparison, expenses-by-category, outstanding, activity feed — filterable by branch and by period (Today / Month-to-date). Matches `owner-dashboard.html` dashboard tab. (Team & Branches is already its own screen since Stage 2.)

- **No migration.** Also wires `branch_id` into the maker-side audit calls (`ReceiveDocumentService` / `ExpenseDocumentService` / `CashDocumentService` / `JobCardService` — CREATED / SUBMITTED / LINE_ADDED / SETTLED / CATEGORY_CHANGED) so the activity feed filters by branch for new data (V19 backfilled history).
- **Money figures count APPROVED documents only** (dashboard = verified, Tally-grade numbers); **Cash In/Out (`cash_document`) is excluded from Collections and Expenses** everywhere (AGENT.md decision #1) — it only affects the cash-in-hand KPI.
- `DashboardService` + endpoints (OWNER + FINANCE_MANAGER):
  - `GET /api/v1/dashboard/summary?branchId=&period=today|mtd` → `{ kpis: {collections, expenses, net, cashInHand, pendingReview}, trend: [{date, collections, expenses}] (14-day), byMode: [{mode, amount}], byCategory: [{category, amount}], branchComparison: [{branchCode, branchName, collections, expenses, net, cashInHand, lastClosed, variance, pendingReview}] }`
  - `GET /api/v1/dashboard/outstanding?branchId=` → job cards with `pending_amount > 0` + open claims (APPROVED claim, no `claim_close`) — B2B credit, part-paid jobs, warranty claims "awaiting Eicher settlement"
  - `GET /api/v1/dashboard/activity?branchId=&limit=` → recent audit events joined to doc + actor
  - `cashInHand` reuses `DrawerService.position(branch, today)`; new grouped `sum…` queries on `SettlementLineRepository` / `ExpenseLineRepository` (by branch, by mode/category, by day) for the rest.
- Frontend: `owner/DashboardPage.tsx` (Owner home) — branch + period segments, KPI row with count-up, Collections-vs-Expenses trend (`recharts` area/line), collections-by-mode donut (`recharts` pie), expenses-by-category horizontal bars, branch-comparison table, outstanding list, activity feed; `api/dashboard.ts`. `recharts` is already a dependency.
- Help stubs: `owner/reading-the-dashboard.md`, `owner/comparing-branches.md`
- Tests: `DashboardServiceTest` — KPI math (only APPROVED, `cash_document` excluded), by-mode / by-category grouping, branch + period filter, outstanding includes open claims.

### V20 — full demo seed (accompanies Stage 8.5 + 9)
One additive Flyway migration (`DO $$`, guarded on the demo org) so **every screen for every role has realistic content** and every workflow state is visible somewhere, across all three branches. Supersedes the earlier "top up the seed" idea.

- +~4 customers / vehicles / job cards spread across OOJ / OOB / OOR (some B2B with GST).
- **Receipts** — a part-paid one (Pending > 0), a fully-settled one, one each SUBMITTED / QUERIED / VERIFIED / REJECTED, and **2 open warranty / AMC claims** (APPROVED, `is_claim`, no `claim_close`: one no-payment, one part-paid) for the FM to close live.
- **Expenses** — 2 VERIFIED (one over-limit) for the FM queue, one "Awaiting Receipt", one "Received Receipt", one REJECTED (keep the existing SUBMITTED / DRAFT / CLOSED).
- **Claims** — fix `OOB-JUL26-R-022`'s settlement line to the full ₹23,101 billed so its existing close reads correctly as an override (`₹23,101 → ₹22,875`); + one non-overridden closed claim so "Recently closed" has two.
- **Cash** — OOB + OOJ get an opening, ~3 movements, a day-close and one QUERIED movement each; plus a VERIFIED and an (review-)APPROVED cash doc so the Stage-8.5 cash lane isn't empty; OOR gets a more recent close so "today's drawer" isn't stale.
- **Override Audit** — one settlement-line override + one expense-line override (each with its `OVERRIDE` audit row + `original_amount`, `branch_id` set).
- **Audit history** — CREATED → SUBMITTED → VERIFIED → APPROVED trails (with `branch_id`) on the seeded docs so review "History" panels read as a timeline.
- `document_sequence` counters bumped so live submits don't collide.
- **Not seeded:** attachments (need a real stored object — testers upload their own).

### Stage 10 — Super Admin panel ✅ (frontend only — built rev 15)
**Demo:** Super Admin sees all orgs, onboards a new one, toggles active. Same visual language.

- Super Admin frontend panel (no new backend)
- Org list table, onboard modal (shows the invite link to copy), active toggle
- Help stubs: `super-admin/onboarding-an-organization.md`

### Stage 11 — Help Center ✅ (framework rev 15; layout fix + full content rewrite rev 16; text-only by owner decision; contextual "?" deferred)
**Demo:** Every role opens Help from the shell and sees a clean, role-scoped table of contents that starts with a "Your role in DAMS" orientation article, then task walkthroughs in plain language. (Original plan had a screenshot per step + a contextual "?" per screen — screenshots dropped in rev 15, "?" deferred.)

- Help framework: `frontend/src/help/<role>/*.md` + a manifest per role (slug, title, order, which screen(s) link to it)
- `HelpDrawer` / `/help` route: role-scoped TOC, article renderer (`react-markdown` + `remark-gfm`), in-help search over titles + body
- Contextual `?` control wired into each screen header → deep-links to its article *(deferred)*
- ~~Quality pass~~ ✅ **rev 16** — all 19 task articles rewritten in a fuller plain-language voice + a "Your role in DAMS" orientation article per role; drawer layout bug fixed. ~~capture screenshots~~ — dropped, text-only (rev 15)
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

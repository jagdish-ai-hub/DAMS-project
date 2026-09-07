# DAMS — High-Value Features & Operational Additions

> **Source of truth for all value additions beyond the core v1 spec.**
> This document details each feature added to DAMS, the business problem it solves for truck dealerships, the exact files modified and created, and step-by-step verification instructions.
>
> Status legend: 🚀 In Progress · ✅ Done · 📋 Planned (proposed, not built — needs plan confirmation per AGENT.md before coding).

---

## 1. Executive Summary

| ID | Feature Name | Target Roles | Primary Business Benefit | Files Modified / Created | Status |
|---|---|---|---|---|---|
| **FEAT-01** | **Printable Thermal Slip & PDF Voucher (80mm / A4)** | Cashier, Customer / Driver | Instant paper slip for drivers to hand over to fleet owners; eliminates payment disputes. | `frontend/src/cashier/PrintReceiptModal.tsx`, `CashierHomePage.tsx`, `NewReceiptPage.tsx` | ✅ Done |
| **FEAT-02** | **Dynamic UPI QR Code Generator** | Cashier, Customer | Direct on-screen UPI QR code with exact amount pre-filled; eliminates typing errors. | `frontend/src/cashier/UpiQrModal.tsx`, `NewReceiptPage.tsx`, `AddPaymentModal.tsx` | ✅ Done |
| **FEAT-03** | **Local Draft Auto-Recovery** | Cashier | Protects unsaved line entries from browser crashes, reloads, or power cuts. | `frontend/src/shared/useDraftRecovery.ts`, `NewReceiptPage.tsx`, `NewExpensePage.tsx` | ✅ Done |
| **FEAT-04** | **Tally / Excel CSV Accounting Export** | Accountant, Owner | Direct CSV stream formatted for Tally voucher import (Receipts & Expenses). | `backend/.../export/ExportController.java`, `ExportService.java`, `SettlementLineRepository.java`, `ExpenseLineRepository.java`, `frontend/src/api/export.ts`, `ExportModal.tsx`, `ReviewQueuePage.tsx`, `DashboardPage.tsx` | ✅ Done |
| **FEAT-05** | **Multi-Select Bulk Verification** | Accountant | Bulk review & verify clean entries in one click with maker-checker protection. | `backend/.../review/ReviewController.java`, `ReviewService.java`, `BulkVerifyRequest.java`, `BulkVerifyResponse.java`, `frontend/src/api/review.ts`, `ReviewQueuePage.tsx` | ✅ Done |
| **FEAT-06** | **OEM Claim Aging Buckets (30/60/90+ Days)** | Finance Manager, Owner | Tracks overdue warranty/AMC claims to prevent manufacturer deadline write-offs. | `frontend/src/finance/FmQueuePage.tsx`, `DashboardPage.tsx` | ✅ Done |
| **FEAT-07** | **Cash Variance & Unclosed Day Alerts** | Owner, Finance Manager | Real-time warnings for unclosed branch cash drawers or non-zero count shortages. | `DashboardPage.tsx`, `DashboardService.java` | ✅ Done |
| **FEAT-08** | **Attachment Lightbox & Multi-File Drag-and-Drop** | All Roles (Cashier, Accountant, FM) | In-app PDF/image viewing without leaving the tab + multi-file drag drop. | `frontend/src/cashier/AttachmentsPanel.tsx`, `ViewReceiptsModal.tsx`, `AttachmentLightbox.tsx` | ✅ Done |
| **FEAT-09** | **Ask DAMS — Scoped Org Assistant (Owner/Admin Bot)** | Owner (+ FM read-only) | Natural-language answers over org data with cited doc IDs; cuts dashboard hunting. | `backend/.../ai/AiController.java`, `AiAssistantService.java`, `InsightService.java`, `frontend/src/owner/AskDamsPanel.tsx`, `frontend/src/api/ai.ts` | 🚀 In Progress |
| **FEAT-10** | **Morning Owner Brief (Daily/Weekly Digest)** | Owner | 5-bullet executive summary: collections/expenses/net/cash, pending review, variances, aging claims. | `backend/.../ai/AiAssistantService.java` (brief), `frontend/src/owner/AiInsightsSection.tsx` | 🚀 In Progress |
| **FEAT-11** | **Anomaly & Fraud Watchdog** | Owner, FM | Flags overrides clustering, just-under-limit expenses, after-hours entries, chronic cash variance. | `backend/.../ai/AiWatchdogService.java`, `frontend/src/owner/AiInsightsSection.tsx` | 🚀 In Progress |
| **FEAT-12** | **Claim Chaser (Warranty/AMC/CG)** | FM, Owner | Predicts at-risk claims, drafts OEM follow-up with history, suggests FM accept-range. | `backend/.../ai/AiOpsService.java`, `frontend/src/finance/AiClaimBanner.tsx`, `AiInsightsSection.tsx` | 🚀 In Progress |
| **FEAT-13** | **Query Root-Cause Coach** | Owner, Accountant | Clusters query/reject reasons to fix training/process at source, cuts resubmit loop. | `backend/.../ai/AiWatchdogService.java` (queryRoots), `frontend/src/owner/AiInsightsSection.tsx` | 🚀 In Progress |
| **FEAT-14** | **Branch Benchmark Narrator** | Owner | Plain-English branch comparison (collections vs queries vs cash discipline). | `backend/.../ai/AiAssistantService.java` (benchmark), `frontend/src/owner/AiInsightsSection.tsx` | 🚀 In Progress |
| **FEAT-15** | **Cash Leak & Deposit Advisor** | Owner, FM | Recommends bank-deposit timing, flags drawer-risk days, explains variances. | `backend/.../ai/AiOpsService.java` (cashAdvice), `frontend/src/owner/AiInsightsSection.tsx` | 🚀 In Progress |
| **FEAT-16** | **Document Risk Score (Pre-Approval Health)** | Accountant, FM (visible read-only to Owner) | Risk score per SUBMITTED doc: missing attachment, override, new-customer large cash, duplicate bill hash. | `backend/.../ai/AiWatchdogService.java` (riskScores), `frontend/src/review/AiRiskBadge.tsx`, `ReviewQueuePage.tsx`, `FmQueuePage.tsx` | 🚀 In Progress |
| **FEAT-17** | **Vendor / Receiver Deduper + Spend Intelligence** | Owner, Accountant | Merges duplicate receivers, tracks spend concentration + price drift per sub-category. | `backend/.../ai/AiMastersService.java`, `frontend/src/owner/AiInsightsSection.tsx` | 🚀 In Progress |
| **FEAT-18** | **Masters Janitor** | Owner | Finds dead/duplicate categories, modes, statuses; proposes deactivate (never delete). | `backend/.../ai/AiMastersService.java`, `frontend/src/owner/AiMastersStrip.tsx` | 🚀 In Progress |
| **FEAT-19** | **Expense Limit Advisor** | Owner | Suggests limit revisions from actual breach frequency; Owner approves. | `backend/.../ai/AiMastersService.java` (limitAdvice), `frontend/src/owner/AiMastersStrip.tsx` | 🚀 In Progress |
| **FEAT-20** | **Semantic Universal Search++** | All Roles (scoped) | Typo/fuzzy + intent search ("Innova unpaid last month"); respects branch scope + cashier toggle. | `backend/.../ai/AiSearchService.java`, `frontend/src/shared/GlobalSearch.tsx` (fallback) | 🚀 In Progress |
| **FEAT-21** | **Month-End Close Copilot** | Owner, FM, Accountant | Close checklist (cash days closed? expenses closed? claims transferred?) + sign-off pack. | `backend/.../ai/AiOpsService.java` (closeChecklist), `frontend/src/owner/AiInsightsSection.tsx` | 🚀 In Progress |

---

## 2. Detailed Technical Breakdown

### FEAT-01: Printable Thermal Slip & PDF Payment Receipt (80mm / A4)
- **Dealership Context:** Truck drivers and fleet operators paying at the workshop cash desk require an immediate stamped/signed payment receipt to claim reimbursement from their fleet management company before leaving the premises.
- **Files Modified / Created:**
  - `frontend/src/cashier/PrintReceiptModal.tsx` *(Created)*: Renders a receipt formatted for standard 80mm thermal POS printers and A4 printing via CSS `@media print`.
  - `frontend/src/cashier/NewReceiptPage.tsx` *(Modified)*: Added "Print Receipt" trigger button in document header and after successful receipt submission.
  - `frontend/src/cashier/CashierHomePage.tsx` *(Modified)*: Added "Print Receipt" action button to customer history job card items.
- **Details:** Displays Dealership Name & Branch Header, Receipt Document Number, Job Card / DBM ID reference, Customer Name & Mobile, Vehicle Registration Number, Itemized Settlement Lines (Mode, Bank, Transaction Reference, Amount), Total Received, Remaining Job Card Pending Amount, Cashier Name, Date/Time stamp, and Signature Block.

### FEAT-02: Dynamic UPI QR Code Generator for Payments
- **Dealership Context:** Drivers often scan a static printed shop QR code and miskey the amount or forget paise, causing accounting friction.
- **Files Modified / Created:**
  - `frontend/src/cashier/UpiQrModal.tsx` *(Created)*: Generates a standard NPCI UPI URI (`upi://pay?pa={vpa}&pn={payee}&am={amount}&tr={ref}`) into an on-screen QR code using `qrcode.react`.
  - `frontend/src/cashier/NewReceiptPage.tsx` *(Modified)*: Added inline "UPI QR" action pill next to payment mode selection.
  - `frontend/src/cashier/AddPaymentModal.tsx` *(Modified)*: Added "Show UPI QR" modal button for instant driver scanning.
- **Details:** Cashier enters the payment amount, clicks "Generate UPI QR", and presents the high-contrast QR code to the driver/customer to scan with PhonePe, Google Pay, BHIM, or Paytm. Automatically incorporates transaction reference.

### FEAT-03: Local Draft Auto-Recovery (Offline Resilience)
- **Dealership Context:** Workshop cashiers frequently experience power flickers, browser tab crashes, or accidental page reloads while compiling multiple line entries.
- **Files Modified / Created:**
  - `frontend/src/shared/useDraftRecovery.ts` *(Created)*: Periodic auto-saving hook storing staged lines and inputs in browser `localStorage` keyed by `(orgId, branchId, draftType)`. 48-hour TTL.
  - `frontend/src/cashier/NewReceiptPage.tsx` *(Modified)*: Integrates `useDraftRecovery` for staged settlement lines, showing a restore/discard banner when an uncommitted draft is found.
  - `frontend/src/cashier/NewExpensePage.tsx` *(Modified)*: Integrates `useDraftRecovery` for staged expense lines.
- **Details:** When opening receipt or expense forms with an empty state, if a recent cached draft is detected, prompts the cashier with: *"Unsaved draft from [time] detected (Amount: ₹X, N lines). [Restore Draft] [Discard]"*. Automatically purges the cached draft once submitted.

### FEAT-04: Tally / Excel / CSV Accounting Ledger Export
- **Dealership Context:** `AGENT.md` establishes that Tally remains the accounting system of record. Without an export, accountants must manually re-type every approved receipt and expense into Tally.
- **Files Modified / Created:**
  - `backend/src/main/java/com/dams/export/controller/ExportController.java` *(Created)*: Exposes `GET /api/v1/export/receipts` and `GET /api/v1/export/expenses` with date and branch filtering.
  - `backend/src/main/java/com/dams/export/service/ExportService.java` *(Created)*: Streams RFC-4180 CSV with UTF-8 BOM encoding for direct opening in Microsoft Excel and Tally import bridges.
  - `backend/src/main/java/com/dams/receive/repository/SettlementLineRepository.java` *(Modified)*: Added `findLinesForExport` query with org and branch scoping.
  - `backend/src/main/java/com/dams/expense/repository/ExpenseLineRepository.java` *(Modified)*: Added `findLinesForExport` query with org and branch scoping.
  - `frontend/src/api/export.ts` *(Created)*: Axios wrapper downloading CSV blobs as timestamped files.
  - `frontend/src/shared/ExportModal.tsx` *(Created)*: Modal dialog for selecting date range (7d, 30d, 90d, custom) and branch filter.
  - `frontend/src/accountant/ReviewQueuePage.tsx` *(Modified)*: Added "Export to Tally/Excel" action button in header.
  - `frontend/src/owner/DashboardPage.tsx` *(Modified)*: Added "Export Ledger" action button in header.
- **Details:** Strictly branch-scoped via `BranchScope`: Accountants can only export branches they are assigned to; Owners and Finance Managers can export org-wide. Includes customer phone, vehicle registration number, canonical job card ref (`{branchCode}-JC-{id}`), DBM ID, mode, bank, transaction ref, and amounts.

### FEAT-05: Multi-Select Bulk Verification for Clean Entries
- **Dealership Context:** Accountants reviewing 50+ entries a day waste substantial time clicking into individual entries when standard receipts match the invoice with no anomalies.
- **Files Modified / Created:**
  - `backend/src/main/java/com/dams/review/dto/BulkVerifyRequest.java` *(Created)*: Request record holding `List<Long> ids`.
  - `backend/src/main/java/com/dams/review/dto/BulkVerifyResponse.java` *(Created)*: Response record holding counts and IDs of verified and skipped items.
  - `backend/src/main/java/com/dams/review/controller/ReviewController.java` *(Modified)*: Added `POST /api/v1/receipts/bulk-verify` and `POST /api/v1/expenses/bulk-verify`.
  - `backend/src/main/java/com/dams/review/service/ReviewService.java` *(Modified)*: Added `bulkVerifyReceipts` and `bulkVerifyExpenses` enforcing maker-checker and branch access rules.
  - `frontend/src/api/review.ts` *(Modified)*: Added API client calls for bulk verification.
  - `frontend/src/accountant/ReviewQueuePage.tsx` *(Modified)*: Multi-select checkboxes, "Select All" toggle, and a sticky bottom action bar displaying "Bulk Verify Selected (N)".
- **Details:** Invariant enforcement: Any document created or modified by the reviewing accountant is skipped (maker-checker violation prevented). Any document outside user branch access is rejected.

### FEAT-06: OEM Warranty / AMC Claim Aging Buckets
- **Dealership Context:** Unsettled claims with vehicle manufacturers (Eicher, Tata, etc.) that exceed 90 days are subject to rejection or severe write-offs.
- **Files Modified / Created:**
  - `frontend/src/finance/FmQueuePage.tsx` *(Modified)*:
    - Added aging bucket calculation (`claimAgeDays`, `getAgingBucket`): `0–30d` (Normal), `31–60d` (Follow-up), `61–90d` (Escalate), `90+d` (Critical).
    - Added visual aging badges (`<AgingBadge days={...} />`) on claim queue items.
    - Added quick-filter pill buttons (`All`, `0-30d`, `31-60d`, `61-90d`, `90+d`) to filter open claims.
    - Added OEM Claim Aging Buckets breakdown card in the FM Overview screen.
  - `frontend/src/owner/DashboardPage.tsx` *(Modified)*: Visual aging distribution breakdown within the Outstanding Claims overview.
- **Details:** Gives the Finance Manager and Dealership Owner immediate visibility into aging claims before they hit OEM deadlines.

### FEAT-07: Cash Drawer Variance & Unclosed Day Early-Warning Alerts
- **Dealership Context:** Cash shortages or cashiers forgetting to perform the end-of-day cash close can go unnoticed by dealership owners until weeks later.
- **Files Modified / Created:**
  - `backend/src/main/java/com/dams/dashboard/service/DashboardService.java` *(Pre-existing backend support)*: Checks for past unclosed operating days and non-zero close variances across branches.
  - `frontend/src/owner/DashboardPage.tsx` *(Modified)*: Prominent warning banners for unclosed branch cash drawers or non-zero count shortages.
  - `frontend/src/finance/FmQueuePage.tsx` *(Modified)*: Visual warning indicators on cash queue items.
- **Details:** Warns immediately if any branch failed to close cash yesterday or has an unresolved physical count shortage.

### FEAT-08: Attachment Lightbox & Direct Drag-and-Drop Dropzone
- **Dealership Context:** Reviewers lose focus when clicking attachments opens a new blank browser tab. Cashiers also need to attach multiple receipts at once without multiple picker dialogs.
- **Files Modified / Created:**
  - `frontend/src/shared/AttachmentLightbox.tsx` *(Created)*: In-app modal preview for PDFs (via secure iframe/object embed) and images, featuring zoom in/out, rotate, and full-screen view.
  - `frontend/src/cashier/AttachmentsPanel.tsx` *(Modified)*: Added visual drag-and-drop dropzone supporting multi-file drops and integrated in-app lightbox preview on file clicks.
  - `frontend/src/cashier/ViewReceiptsModal.tsx` *(Modified)*: Integrated in-app lightbox preview directly on attachment click.
- **Details:** Eliminates external browser tab popups and streamlines evidence uploads for cashiers.

---

## 3. AI Features for Owner / Admin (FEAT-09 → FEAT-21) — 🚀 In Progress

> Implementation (rev 24): new `com.dams.ai` module — `AiController` (`/api/v1/ai/*`,
> 13 endpoints), `AiAssistantService` (ask/brief/benchmark), `AiWatchdogService`
> (anomalies/risk/query-roots), `AiOpsService` (claims/cash/close), `AiMastersService`
> (receivers/masters/limits), `AiSearchService` (smart search), `InsightService` +
> `DeterministicInsightService` (swappable phrasing, offline-safe), `AiQueryLog`
> entity + `V20__ai_query_log.sql`. Only write in the module is the ask trace row.
> Frontend: `api/ai.ts`, `owner/AskDamsPanel.tsx`, `owner/AiInsightsSection.tsx`
> (dashboard hub), `owner/AiMastersStrip.tsx`, `finance/AiClaimBanner.tsx`,
> `review/AiRiskBadge.tsx` (queue pills), `GlobalSearch.tsx` smart-search fallback.
> Tests: `AiAssistantServiceTest`, `AiWatchdogServiceTest`, `AiOpsServiceTest`
> (behaviour-named). Verify with `mvn test` (needs JDK 21 — CI) + `npm test`;
> frontend `tsc --noEmit` is clean for all AI files.
>
> Design constraints (from AGENT.md, apply to all AI feats below):
> - Owner stays read-only on transactions — AI drafts/suggests, humans click verify/approve/close.
> - Every query filters by JWT `org_id` (+ branch scope, + cashier multi-branch toggle). No cross-org leakage.
> - Maker-checker holds — AI can never approve its own or anyone's entry.
> - Every AI answer cites real doc/line IDs (`OOR-JUL26-R-021-L1`) or says "not found" — no hallucinated IDs.
> - Provider swappable behind `InsightService` interface (like `StorageService` for R2); no LLM vendor specifics in business logic.
> - Only new write for v1 is `ai_query_log(org_id, user_id, question, doc_ids_cited, request_id)` for traceability. No auto-edits to masters/documents.

### FEAT-09: Ask DAMS — Scoped Org Assistant (Owner/Admin Bot)
- **Why:** Owner won't click 5 filters to answer "OOR collections this week? Which claims >60 days? Why is cash short?" A chat dock on the Dashboard answers in one shot with numbers + links. Cuts daily phone calls to accountant/FM.
- **Where (built, rev 24–25):**
  - Backend: `com.dams.ai.controller.AiController` → `POST /api/v1/ai/ask {question, branchId?}`; `com.dams.ai.service.AiService` (orchestrates) + `InsightService` interface (LLM provider swappable).
  - Chat UX (rev 25): multi-turn thread (session-only history, matches the 8h-JWT minimal-session rule), starter suggestions for the cold start, follow-up chips, clickable cited docs (each asks about that document via the doc-lookup intent below), per-answer copy + ref id, scope badge, `aria-live` answers.
  - Doc-lookup intent (rev 25): a question naming a number like `OOR-JUL26-R-021` resolves that exact document (receive exact match, expense exact match; out-of-scope reads as "not found" so existence never leaks) and reports workflow status, settled/open, line count + total, limit flag — citing only that number.
  - Backend: `com.dams.ai.controller.AiController` → `POST /api/v1/ai/ask {question, branchId?}`; `com.dams.ai.service.AiService` (orchestrates) + `InsightService` interface (LLM provider swappable).
  - Reuses read-only: `DashboardService`, `SearchService`, `OverrideAuditService` — all already `org_id`-scoped.
  - Frontend: `frontend/src/owner/AskDamsPanel.tsx` (dock on `DashboardPage.tsx`), `frontend/src/api/ai.ts`; deep-links to existing Help articles via contextual `?`.
  - Guardrails: JWT role/`org_id` drives every answer; returns `requestId` matching server log line.

### FEAT-10: Morning Owner Brief (Daily/Weekly Digest)
- **Why:** Owner wants a 30-second "is everything OK?" view: collections/expenses/net/cash-in-hand, pending-review count, unclosed days, variances ≠ 0, 60/90+ claims, best/worst branch. Replaces manual morning register check.
- **Where (proposed):**
  - Backend: `com.dams.ai.service.OwnerBriefService` — summarises existing `DashboardSummary` (KPIs, `CashAlertItem`, `ClaimAgingSummary`, `BranchComparisonRow`) into 5 bullets; LLM only for wording, numbers come from service.
  - Frontend: brief card on top of `owner/DashboardPage.tsx` + optional email via existing `EmailService`.
  - Excludes Cash In/Out from collections/expenses KPIs (same rule as dashboard).

### FEAT-11: Anomaly & Fraud Watchdog
- **Why:** Small leaks compound: repeated overrides by one user, round-number expenses just under limit (4990 vs 5000), after-hours entries, same vehicle frequent claims, branch with chronic cash variance. Owner finds out weeks late today.
- **Where (proposed):**
  - Backend: `com.dams.ai.service.AnomalyService` — deterministic rules over override-audit + review + cash-close tables; LLM only explains the flag in plain English.
  - Surfaces in: `DashboardSummary` alerts + `OverrideAuditPage.tsx` badge + `DashboardPage.tsx` banner.
  - Testable: `overrideCluster_detectsSameUserThreeOverridesInSevenDays`, `justUnderLimit_flags4990Against5000Limit`.

### FEAT-12: Claim Chaser (Warranty/AMC/CG)
- **Why:** 90+ day Eicher/Tata claims get rejected/written off. FM needs at-risk prediction + ready follow-up draft with full doc history, plus data-backed accept-range for final override.
- **Where (proposed):**
  - Backend: `com.dams.ai.service.ClaimInsightService` — uses `ClaimAgingSummary` buckets (0–30/31–60/61–90/90+) + job-card history; drafts OEM letter, suggests settlement range from past partial pays.
  - Frontend: aging badges + "Draft follow-up" on `finance/FmQueuePage.tsx`; outstanding card on `owner/DashboardPage.tsx`.
  - Invariant: never auto-closes — FM still calls `POST /job-cards/{id}/close-claim`; override stays permanent + marked "Overridden · Final".

### FEAT-13: Query Root-Cause Coach
- **Why:** Same mistakes resubmit in a loop (missing DBM ID, blurry bill photo, wrong category). Clustering query/reject reasons shows Owner the one training fix that kills 40% of rework.
- **Where (proposed):**
  - Backend: `com.dams.ai.service.QueryInsightService` — groups `queryReason` text by branch/user/category.
  - Frontend: coach card on `owner/DashboardPage.tsx` with link to role Help article + suggested 1-line SOP change.

### FEAT-14: Branch Benchmark Narrator
- **Why:** Tables don't trigger action. "OOR +18% collections but 2.3x queries vs OOB — shift accountant hours there" does.
- **Where (proposed):**
  - Backend: `com.dams.ai.service.BenchmarkService` over existing `BranchComparisonRow` list.
  - Frontend: narrative block above branch-comparison chart in `owner/DashboardPage.tsx`.

### FEAT-15: Cash Leak & Deposit Advisor
- **Why:** Branches sit on risky cash piles or run short on Fridays. Advisor recommends deposit timing ("deposit OOR Thursdays, holds >80k Fridays") and explains variances in words.
- **Where (proposed):**
  - Backend: `com.dams.ai.service.CashInsightService` over `DrawerService` math (`Opening + cash receipts + Cash In − cash expenses − Cash Out`) + close-day variance history.
  - Frontend: advisor strip on `owner/DashboardPage.tsx` + cash page; never changes closing — cashier still enters counted cash + mandatory remark when ≠ 0.

### FEAT-16: Document Risk Score (Pre-Approval Health)
- **Why:** Lets Owner see entry quality without editing (read-only safe): missing attachment, override present, new-customer + large cash, duplicate bill-photo hash. Also triages Accountant/FM queues.
- **Where (proposed):**
  - Backend: `com.dams.ai.service.RiskScoreService` — rules + bill-image hash via `StorageService`; score attached to review items in `ReviewService`.
  - Frontend: risk pill on `review/ReviewQueuePage.tsx` + `finance/FmQueuePage.tsx`; read-only mirror on Owner dashboard.

### FEAT-17: Vendor / Receiver Deduper + Spend Intelligence
- **Why:** "Sharma Motors" vs "S. Sharma Motors" splits spend history; same-part prices drift across branches unnoticed.
- **Where (proposed):**
  - Backend: `com.dams.ai.service.ReceiverInsightService` — fuzzy match on name/phone/UPI + spend concentration + price drift per expense sub-category.
  - Frontend: "Possible duplicates" + merge suggestion in `owner/MastersPage.tsx` / receivers view; merge = deactivate-duplicate, never delete.

### FEAT-18: Masters Janitor
- **Why:** Dropdowns rot: dead categories, duplicate modes, stale business statuses. Clean masters = fewer wrong-category queries.
- **Where (proposed):**
  - Backend: `com.dams.ai.service.MastersInsightService` — usage counts per master type over last 90 days.
  - Frontend: janitor panel in `owner/MastersPage.tsx` proposing `deactivate`, never delete, per Owner masters rule.

### FEAT-19: Expense Limit Advisor
- **Why:** Static limits cause constant friction (diesel advances breach 5k 12x/month). Advisor proposes data-backed revisions; Owner approves.
- **Where (proposed):**
  - Backend: `com.dams.ai.service.LimitAdvisorService` — breach frequency vs approval rate per category/branch.
  - Frontend: inline suggestion on `owner/MastersPage.tsx` expense-limits section; writes only via existing Owner `PATCH /masters/{type}/{id}`.

### FEAT-20: Semantic Universal Search++
- **Why:** Cashiers/owners search like humans: "Innova unpaid last month", mistyped vehicle nos, Hindi/Kannada mix. Today's keyword search misses.
- **Where (proposed):**
  - Backend: extend `com.dams.search` (`SearchService`) — keep vehicle-no normalisation (uppercase, no spaces) + add fuzzy/intent layer; always scoped by branch access + cashier multi-branch toggle (default OFF).
  - Frontend: upgrade `frontend/src/shared/GlobalSearch.tsx` with AI-ranked results + typo tolerance.

### FEAT-21: Month-End Close Copilot
- **Why:** Close is a checklist today in someone's head: all cash days closed? expenses closed? claims transferred? pending-review zero? Copilot makes it explicit + produces Owner sign-off pack.
- **Where (proposed):**
  - Backend: `com.dams.ai.service.CloseChecklistService` — aggregates cash-close, expense status, claim status, review queues per branch/month.
  - Frontend: close card on `owner/DashboardPage.tsx` with per-branch ticks + exportable sign-off summary (KPIs + outstanding + audit rows + requestIds).

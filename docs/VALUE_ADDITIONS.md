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
| **FEAT-22** | **Cash Close Denomination Grid** | Cashier | Type note/coin counts, auto-total the counted cash; kills count-to-typing arithmetic errors. | `frontend/src/cashier/CashPage.tsx` | ✅ Done |
| **FEAT-23** | **Nav Count Badges (Queried / Pending)** | Cashier, Accountant, FM | Queried entries and grown queues surface on the nav (5-min poll, fail-silent) instead of needing a manual check. | `frontend/src/shell/AppShell.tsx` | ✅ Done |
| **FEAT-24** | **Query Reason Templates + Queue Filters** | Accountant, FM | 5 canned query reasons keep notes consistent for root-cause analysis; Over-limit / Has-override / No-bill toggles triage faster. | `frontend/src/review/reviewShared.tsx`, `ReviewQueuePage.tsx`, `FmQueuePage.tsx` | ✅ Done |
| **FEAT-25** | **FM Bulk Approve** | Finance Manager | Approve many VERIFIED docs in one click with maker-checker skips (mirrors accountant bulk verify). | `backend/.../review/ReviewService.java`, `ReviewController.java`, `frontend/src/api/review.ts`, `FmQueuePage.tsx` | ✅ Done |
| **FEAT-26** | **Claim Pack (Copy + Open All Bills)** | FM | One-click claim summary copy + open every bill for the Eicher portal upload. | `frontend/src/review/reviewShared.tsx`, `FmQueuePage.tsx` | ✅ Done |
| **FEAT-27** | **Amount-in-Words + WhatsApp Slip Share** | Cashier, Customer / Driver | Indian-system words line on the slip + `wa.me` share text so drivers can send the receipt to fleet owners instantly. | `frontend/src/cashier/PrintReceiptModal.tsx` | ✅ Done |
| **FEAT-28** | **Camera Capture + Client Compress** | Cashier | Phone camera capture for bills + canvas compress (1600px, JPEG 0.8) before upload; PDFs pass through. | `frontend/src/cashier/AttachmentsPanel.tsx` | ✅ Done |
| **FEAT-29** | **Dashboard Custom Range + Branch Drill-Down** | Owner, FM | Explicit `from/to` range (`period=custom`) + click a branch row to filter; answers "what happened 12–18 Aug in OOB?". | `backend/.../dashboard/`, `frontend/src/api/dashboard.ts`, `owner/DashboardPage.tsx` | ✅ Done |
| **FEAT-30** | **Expense Budgets vs Actual** | Owner | Monthly caps per expense category (`expense_budget`, V23); amber ≥80%, red over; inform-only, never block. | `backend/.../budget/`, `V23__expense_budget.sql`, `frontend/src/api/budgets.ts`, `owner/DashboardPage.tsx`, `owner/MastersPage.tsx` | ✅ Done |
| **FEAT-31** | **Masters Usage Guard** | Owner | 90-day use count per master row; explicit confirmation to deactivate in-use rows. | `backend/.../masters/`, `frontend/src/api/masters.ts`, `owner/MastersPage.tsx` | ✅ Done |
| **FEAT-32** | **Cash-Day Reopen by Request** | Cashier, FM | Locked-day miscounts fixed via request (reason mandatory) → FM approve removes the close / reject keeps it; fully audited, no silent reopen. | `backend/.../cash/`, `V24__cash_close_reopen_request.sql`, `frontend/src/api/cash.ts`, `cashier/CashPage.tsx`, `finance/FmQueuePage.tsx` | ✅ Done |
| **FEAT-33** | **Owner Dashboard Helper Layer** | Owner | Evening-brief one-liner, getting-started checklist, staff scorecard + 14-day register (from existing aggregates), collapsible AI hub. | `frontend/src/owner/DashboardPage.tsx`, `AiInsightsSection.tsx` | ✅ Done |
| **FEAT-34** | **UTR / Transaction-Ref Search** | All Roles (scoped) | `GET /search` also matches settlement/expense transaction refs (min 4 chars), resolved to the job card's customer. | `backend/.../search/SearchService.java` | ✅ Done |
| **FEAT-35** | **Receivable Follow-up (dues with owners + dates)** | Cashier, Accountant, Owner | Due date + customer promise per credit doc; overdue derived live; defaulter view ranked by exposure; WhatsApp reminders. | `V25__credit_followup.sql`, `backend/.../followup/`, `frontend/src/api/followups.ts`, `owner/ReceivablesPage.tsx` | ✅ Done |
| **FEAT-36** | **Message Provider Seam + Templates + Log** | All (system-sent) | Org `{{variable}}` templates, logging sender by default (WhatsApp plugs in later), every attempt audited. | `V26__messaging.sql`, `V35__org_messaging_flags.sql`, `V36__user_phone.sql`, `backend/.../messaging/`, `frontend/src/api/messaging.ts`, `owner/MessagesPage.tsx` | ✅ Done |
| **FEAT-37** | **Duplicate Bill-Photo Detection** | Cashier, Accountant, FM | SHA-256 at upload; same bytes twice warns (never blocks) with the other location. | `V27__attachment_sha256.sql`, `attachment/.../AttachmentService.java`, `AttachmentResponse.duplicateOf`, `cashier/AttachmentsPanel.tsx` | ✅ Done |
| **FEAT-38** | **Claim Next-Action Tracker** | FM, Owner | Next step + owner + due date per open claim; overdue derived; completing keeps history. | `V28__claim_action.sql`, `backend/.../jobcard/` (`ClaimAction*`), `frontend/src/api/claimActions.ts`, `finance/ClaimsChasePage.tsx` | ✅ Done |
| **FEAT-39** | **Service/AMC Renewal Reminders** | Owner, Cashier | `service_due_date` per job card (never defaulted); renewals view (overdue + 45d) with WhatsApp nudges. | `V34__jobcard_followup_fields.sql`, `jobcard/.../BoardService.java` (`renewals`), `cashier/CashierHomePage.tsx` (due editor), `owner/FloorPage.tsx` | ✅ Done |
| **FEAT-40** | **Bank Statement Reconciliation** | Accountant, FM, Owner | Statement CSV upload; EXACT (UTR+amount) and AMOUNT_DATE (±2d) suggestions; confirm/ignore; matching never moves money. | `V29__bank_reconciliation.sql`, `backend/.../recon/`, `frontend/src/api/recon.ts`, `accountant/ReconPage.tsx` | ✅ Done |
| **FEAT-41** | **Large-Variance Countersign** | Cashier, Accountant, Owner | Close breaching the org threshold parks PENDING; accountant confirms (never own close). Off when threshold unset. | `V30__cash_countersign.sql`, `cash/.../CashCloseService.java` (`countersign`), `POST /cash/close-day/{id}/countersign`, `cashier/CashPage.tsx` (accountant read + button), `settings/SettingsPage.tsx` (threshold) | ✅ Done |
| **FEAT-42** | **Nightly Owner Digest (WhatsApp 8pm)** | Owner | Collections/spend/net, pending reviews, unclosed branches pushed where the owner is. Opt-in + owner phone required. | `messaging/.../DigestService.java` (`@Scheduled` 20:00 IST), `organization.digest_enabled`, `settings/SettingsPage.tsx` (toggle), `owner/TeamAndBranchesPage.tsx` (phone) | ✅ Done |
| **FEAT-43** | **Shareable Customer Ledger** | Cashier, Owner | Dues/payments/balance across all vehicles; print like the slip or share on WhatsApp. Read-only over history. | `backend/.../ledger/`, `frontend/src/api/ledger.ts`, `cashier/StatementModal.tsx`, history Statement button | ✅ Done |
| **FEAT-44** | **Staff Advance Ledger** | Cashier, Accountant, Owner | ADVANCE out / RECOVERY in per staff; outstanding derived; recovery can't exceed outstanding; never edited/deleted. | `V31__staff_advance.sql`, `backend/.../staff/`, `frontend/src/api/staff.ts`, `owner/StaffPage.tsx` | ✅ Done |
| **FEAT-45** | **Auditor Read-Only Role** | Auditor (CA) | Org-wide read (dashboard, audit, masters, follow-ups, exports); zero writes; no review queues. | `user/.../Role.java` (+`BranchScope`, `UserService`, phone), controller authorities, `shell/AppShell.tsx` nav/routes, `owner/MastersPage.tsx` (`readOnly`) | ✅ Done |
| **FEAT-46** | **B2B Credit Limits (warn-first)** | Cashier, Accountant, Owner | Limit per customer; exposure = Σ pending; breach banners the counter + ranks defaulters; posting never blocked. | `V32__customer_credit_limit.sql`, `customer/...` (`creditStatus`), `GET /customers/{id}/credit-status`, history banner | ✅ Done |
| **FEAT-47** | **Awaiting-Bills Herd** | Accountant, FM | Expenses parked on 'Awaiting Receipt', oldest first; attach the bill on the entry to clear. | `expense/...` (`awaitingBills`), `GET /expenses/awaiting-bills`, `frontend/src/api/expenses.ts`, `accountant/AwaitingBillsPage.tsx` | ✅ Done |
| **FEAT-48** | **Estimates (quote before work)** | Cashier, FM, Owner | Draft → Approve/Reject; re-quote supersedes (history kept); variance vs final invoice on every response. | `V33__estimate.sql`, `backend/.../estimate/`, `frontend/src/api/estimates.ts`, `cashier/EstimatesPage.tsx` (`?jobCardId=`), history Quote button | ✅ Done |
| **FEAT-49** | **Offline Outbox (queue-and-sync)** | Cashier | Network failures queue creates locally; sync posts them as drafts. Edits/submits need the server and fail loudly. | `frontend/src/shared/outbox.ts`, `shared/OfflineBanner.tsx`, hooked into receipt/expense/cash creates | ✅ Done |
| **FEAT-50** | **Floor / WIP Board** | Owner, FM, Accountant, Cashier | Open jobs oldest first with stuck reasons + pending; read-only visibility, explicitly not workshop management. | `jobcard/.../BoardService.java` (`wip`), `GET /job-cards/board`, history stuck-reason editor, `owner/FloorPage.tsx` | ✅ Done |

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

---

## 4. Usability & Real-Task Batch (FEAT-22 → FEAT-34) — ✅ Done (rev 29)

> All frontend unless noted. No applied migration edited (V23/V24 are new);
> AGENT.md §6 + API map updated alongside. Verified: backend 192 green,
> `tsc`/`eslint`/`vitest`/vite-build clean.

### FEAT-22: Cash Close Denomination Grid
- **Dealership Context:** Cashiers count notes then do mental arithmetic to type the counted total — a daily source of variance typos.
- **Files:** `frontend/src/cashier/CashPage.tsx` (Close Day modal grid ₹500→₹1, auto-sum prefills counted, live variance, remark rule unchanged).

### FEAT-23: Nav Count Badges
- **Dealership Context:** Cashiers never notice a query; accountants never notice a grown queue until they open the page.
- **Files:** `frontend/src/shell/AppShell.tsx` (queried count for cashiers via `myEntries`, queue totals for accountant/FM via `reviewApi`; 5-min poll + route change, fail-silent, role-gated).

### FEAT-24: Query Reason Templates + Queue Filters
- **Dealership Context:** Free-text query reasons ("bill not clear" vs "blurry photo" vs "no bill") can't be clustered; long queues can't be triaged.
- **Files:** `frontend/src/review/reviewShared.tsx` (`QueryRejectBox` template dropdown, editable after fill), `accountant/ReviewQueuePage.tsx` + `finance/FmQueuePage.tsx` (Over-limit / Has-override / No-bill-via-risk toggles).

### FEAT-25: FM Bulk Approve
- **Dealership Context:** FMs clicked 40 clean VERIFIED docs one by one; accountants already had bulk verify (FEAT-05).
- **Files:** `backend/.../review/service/ReviewService.java` (`bulkApproveReceipts/Expenses`, FM guard, VERIFIED→APPROVED, `APPROVED` audit with `bulk=true`), `review/controller/ReviewController.java` (`POST /receipts|expenses/bulk-approve`), `frontend/src/api/review.ts` (server call with one-by-one 404 fallback), `finance/FmQueuePage.tsx` (sticky approve bar with skip reasons). Tests: `ReviewServiceTest` bulk-approve cases.

### FEAT-26: Claim Pack (Copy + Open All Bills)
- **Dealership Context:** FMs downloaded bills one by one for the Eicher portal.
- **Files:** `frontend/src/review/reviewShared.tsx` (`ClaimPackButtons`), `finance/FmQueuePage.tsx` (`ClaimRowPack`) — copy claim summary via `useCopy`, open every doc+line bill via signed URLs. No backend change.

### FEAT-27: Amount-in-Words + WhatsApp Slip Share
- **Dealership Context:** Drivers need a receipt their fleet owner trusts, sent before they leave the counter.
- **Files:** `frontend/src/cashier/PrintReceiptModal.tsx` (`amountInWordsIndian` — Thousand/Lakh/Crore, printed under the total; `wa.me/?text=` share with branch/docNo/amount+words/job-card/date). Print CSS untouched.

### FEAT-28: Camera Capture + Client Compress
- **Dealership Context:** Counter phones on slow networks fail 10MB bill photos.
- **Files:** `frontend/src/cashier/AttachmentsPanel.tsx` (`capture="environment"` camera button, canvas compress to max 1600px JPEG 0.8, PDF passthrough, 10MB guard kept).

### FEAT-29: Dashboard Custom Range + Branch Drill-Down
- **Dealership Context:** Today/MTD can't answer "what happened 12–18 Aug in OOB?"; the branch table was dead text.
- **Files:** `backend/.../dashboard/` (`summary` takes `from/to`, explicit range overrides period, 400 on half-open/inverted), `frontend/src/api/dashboard.ts` (`DashboardPeriod` gains `custom`), `owner/DashboardPage.tsx` (Today/MTD/Custom seg, date inputs, clickable comparison rows). Tests: `DashboardServiceTest` custom-range cases.

### FEAT-30: Expense Budgets vs Actual
- **Dealership Context:** Static sub-category limits flag single lines, but nothing shows monthly department spend vs plan.
- **Files:** `V23__expense_budget.sql` + `budget/` package (`BudgetController`: `GET ?month=`, OWNER `PUT` upsert `{categoryId, monthKey, capAmount}`), `frontend/src/api/budgets.ts`, dashboard category budget bars (amber ≥80%, red over), Masters expense-categories budget inputs. Caps inform, never block. Tests: `BudgetServiceTest`.

### FEAT-31: Masters Usage Guard
- **Dealership Context:** Owners fear deactivating a row that history depends on.
- **Files:** `GET /masters/{type}/usage` → `[{id, name, useCount, usedLast90d}]` (`MastersService` counting job cards/lines, statuses/categories report 0 with a why-comment), `frontend/src/api/masters.ts`, `owner/MastersPage.tsx` ("used Nx in 90 days", explicit confirm for in-use rows). Deactivate-never-delete unchanged.

### FEAT-32: Cash-Day Reopen by Request
- **Dealership Context:** A typed-but-miscounted close had no correction path (AGENT.md #6 updated: request-only exception, no silent reopen).
- **Files:** `V24__cash_close_reopen_request.sql` (one PENDING per branch/date) + `cash/` package (`CashReopenService`, `CashReopenController`: cashier POST w/ reason, Acct/FM/Owner GET, FM approve removes the close + dual audit / reject keeps lock), `frontend/src/api/cash.ts` (`reopenApi`), `cashier/CashPage.tsx` (request box on locked days), `finance/FmQueuePage.tsx` (pending card with approve/reject). Tests: `CashReopenServiceTest`.

### FEAT-33: Owner Dashboard Helper Layer
- **Dealership Context:** Owners skim: one-line brief, setup checklist, who generates rework, Excel-like register, less AI clutter.
- **Files:** `owner/DashboardPage.tsx` (evening-brief line from KPIs/alerts/outstanding; `localStorage` getting-started checklist; staff scorecard + 14-day register from the activity feed/trend with a comment on derivation limits), `owner/AiInsightsSection.tsx` (brief + watchdog always visible, rest behind "Show all N insights").

### FEAT-34: UTR / Transaction-Ref Search
- **Dealership Context:** "Money left, which entry was it?" — accountants search by the UTR fragment on the bank SMS.
- **Files:** `backend/.../search/SearchService.java` (settlement/expense `transaction_ref` contains-match, min 4 chars, org + branch scoped, resolved to the job card's customer as match field `"UTR"`). Tests: `SearchBranchScopeTest` UTR cases.

## 5. Receivables-to-Floor Batch (FEAT-35 → FEAT-50) — ✅ Done

> Ranked by rupees protected ÷ build size (see `docs/PRODUCT_ROADMAP.md` Part 2).
> New migrations V25–V36 (no applied migration edited); new packages `followup/`,
> `messaging/`, `recon/`, `staff/`, `estimate/`, `ledger/` + job-card board/claim-action
> extensions; `OrganizationPurgeService` covers every new table in FK-safe order;
> AGENT.md API map updated alongside. Verified: backend 244 green (37 new),
> `tsc`/`eslint`/vitest (45) clean, Playwright 29 green (12 new).

### FEAT-35: Receivable Follow-up
- **Dealership Context:** Credit dues aged silently — the outstanding list was display-only with no owner, date, or next step.
- **Files:** `V25__credit_followup.sql` (one live row per doc, overdue derived never stored) + `followup/` package (`FollowupService`: open/re-promise/close, defaulter ranking by live outstanding, branch-scoped reads), `GET|POST /followups`, `GET /followups/defaulters`, `frontend/src/api/followups.ts`, `owner/ReceivablesPage.tsx` (promise form, overdue filter, Remind via FEAT-36, Collected). Tests: `FollowupServiceTest`.

### FEAT-36: Message Provider Seam + Templates + Log
- **Dealership Context:** The only outbound channel was a log-stub email; owners, drivers and fleet managers live on WhatsApp.
- **Files:** `V26__messaging.sql` (templates + append-only log, defaults seeded per org) + `V35__org_messaging_flags.sql` (`digest_enabled`) + `V36__user_phone.sql`; `messaging/` package (`MessageSender` seam + `LoggingMessageSender`, `{{variable}}` renderer, LOGGED vs SENT vs FAILED), `GET /messages/templates|log`, `POST /messages/send`, `owner/MessagesPage.tsx` (send form, log). Tests: `MessagingServiceTest`.

### FEAT-37: Duplicate Bill-Photo Detection
- **Dealership Context:** The cheapest fraud is one bill photo attached to two claims; the risk score mentioned hashes nothing computed.
- **Files:** `V27__attachment_sha256.sql` + `AttachmentService.upload` (SHA-256 over bytes, same-org lookup excluding self), `AttachmentResponse.duplicateOf`, `cashier/AttachmentsPanel.tsx` (amber warning banner, attach-anyway). Warn-only by design — estimate+claim re-uploads are honest.

### FEAT-38: Claim Next-Action Tracker
- **Dealership Context:** Aging buckets show old claims; nothing records the next step, so claims die from neglect and write off as pure loss.
- **Files:** `V28__claim_action.sql` (no deletes — chase history matters at write-off) + `jobcard/` (`ClaimAction*`, `ClaimActionController`: FM/Owner writes, overdue derived), `frontend/src/api/claimActions.ts`, `finance/ClaimsChasePage.tsx`. Tests: `ClaimActionServiceTest`.

### FEAT-39: Service/AMC Renewal Reminders
- **Dealership Context:** AMC dates passed silently; lapsed AMCs are revenue walking to a competitor.
- **Files:** `V34__jobcard_followup_fields.sql` (`service_due_date`, never defaulted) + `BoardService.renewals` (overdue + 45d, most-overdue first), `GET /job-cards/renewals`, history due-date editor, `owner/FloorPage.tsx` renewals tab with WhatsApp nudges. Tests: `BoardServiceTest`.

### FEAT-40: Bank Statement Reconciliation
- **Dealership Context:** QR/UPI lines typed with a UTR are reconciled by eye; unmatched money surfaces weeks later.
- **Files:** `V29__bank_reconciliation.sql` (`recon_batch` + `recon_line`) + `recon/` package (CSV parse with IN/UK date formats, EXACT = UTR-last-12 + amount, AMOUNT_DATE = ±2d suggestion-only, one-line-one-match, confirm/ignore; matching never moves money), `GET|POST /recon/*`, `frontend/src/api/recon.ts`, `accountant/ReconPage.tsx`. Tests: `ReconMatchingTest`.

### FEAT-41: Large-Variance Countersign
- **Dealership Context:** The cashier counts, reports and closes their own day — variance is self-declared text, the weakest control in a cash business.
- **Files:** `V30__cash_countersign.sql` (`countersign_status` + org threshold, NULL = off) + `CashCloseService.closeDay` (breach parks PENDING) and `countersign` (accountant confirms, never own close) + `POST /cash/close-day/{id}/countersign`, `cashier/CashPage.tsx` (accountant read mode + countersign button), `settings/SettingsPage.tsx` (threshold). Tests: `CashCountersignTest`.

### FEAT-42: Nightly Owner Digest
- **Dealership Context:** The morning brief lives in a dashboard the owner rarely opens; evening numbers travel by phone call.
- **Files:** `messaging/.../DigestService.java` (`@Scheduled` 20:00 IST, per-org try/catch, `TenantContext` set/clear), reuses `DashboardService.summary`, opt-in + owner-phone skip rules, org toggle in Settings, phone on Team page.

### FEAT-43: Shareable Customer Ledger
- **Dealership Context:** "What do we owe across all our vehicles?" was read out from a screen; the slip covers one payment, nothing covers the account.
- **Files:** `ledger/` package (read-only composition over `CustomerService.history`), `GET /ledger/customers/{id}/statement`, `frontend/src/api/ledger.ts`, `cashier/StatementModal.tsx` (print CSS like the slip + `wa.me` share), history Statement button.

### FEAT-44: Staff Advance Ledger
- **Dealership Context:** Staff advances live in a notebook; recovery-from-wages is disputed monthly.
- **Files:** `V31__staff_advance.sql` (own staff master — neither customers nor receivers — plus append-only entries) + `staff/` package (outstanding derived, recovery capped at outstanding, deactivate-not-duplicate, inactive staff take nothing), `GET|POST /staff/*`, `frontend/src/api/staff.ts`, `owner/StaffPage.tsx`. Tests: `StaffServiceTest`.

### FEAT-45: Auditor Read-Only Role
- **Dealership Context:** The CA gets Excel dumps or someone's login. Both are bad.
- **Files:** `Role.AUDITOR` (string-mapped, no DDL) + `BranchScope` org-wide + read authorities on dashboard/audit/export/customer-GETs/followups/claims/masters-GET/budgets-GET + `UserService` (org-wide binding, phone, label) + `shell/AppShell.tsx` (Dashboard/Audit/Masters nav, home = dashboard) + `owner/MastersPage.tsx` (`readOnly`: no Add/Edit/budget inputs, `ReceiversSection` too) + `ROLE_TO_HELP` reuses owner reading guides. Review queues excluded (action-dense). Tests: e2e auditor isolation + masters read-only.

### FEAT-46: B2B Credit Limits (warn-first)
- **Dealership Context:** B2B dues accumulate with no ceiling; the counter keeps extending credit past any sane limit.
- **Files:** `V32__customer_credit_limit.sql` (NULL = no limit) + `CustomerService.creditStatus` (exposure = Σ pending) + `GET /customers/{id}/credit-status`, history breach banner, defaulter ranking input. Posting never blocked in v1 (hard block needs an Owner override path — v2).

### FEAT-47: Awaiting-Bills Herd
- **Dealership Context:** 'Awaiting Receipt' status existed but nobody herded it; month-end became a bill-chasing scramble with provisional Tally entries.
- **Files:** `ExpenseDocumentService.awaitingBills` (matches the seeded status by name; empty — never wrong — if renamed) + `GET /expenses/awaiting-bills`, `frontend/src/api/expenses.ts`, `accountant/AwaitingBillsPage.tsx`.

### FEAT-48: Estimates
- **Dealership Context:** The job card carries only the final invoice; the agreed figure lives in conversation and disputes erupt at payment time.
- **Files:** `V33__estimate.sql` (`estimate` + `estimate_line`, SUPERSEDED not deleted) + `estimate/` package (create totals, supersede-live, DRAFT-only decide, variance vs invoice on every response), `GET|POST /estimates`, `frontend/src/api/estimates.ts`, `cashier/EstimatesPage.tsx` (`?jobCardId=`), history Quote button. Tests: `EstimateServiceTest`.

### FEAT-49: Offline Outbox
- **Dealership Context:** No network = no records = end-of-day reconstruction from memory — the Excel-era failure mode DAMS exists to kill.
- **Files:** `frontend/src/shared/outbox.ts` (localStorage queue, `isOfflineError` = no-response failures only) + `shared/OfflineBanner.tsx` (offline notice, queued list with discard, sync-as-draft one-by-one with navigation to the created draft, server rejections stay queued with reason), hooked into receipt/expense/cash-movement creates. Edits/submits/closes need the server and fail loudly. Tests: `shared/outbox.test.ts`.

### FEAT-50: Floor / WIP Board
- **Dealership Context:** `business_status` exists per job card but no view answers "what's in the bays, and what's stuck?" — idle bays are unbilled revenue.
- **Files:** `BoardService.wip` (open cards, oldest first, stuck reason + pending via the batched calculator) + `GET /job-cards/board`, history stuck-reason editor (`PATCH /job-cards`), `owner/FloorPage.tsx`. Explicitly not workshop management (no scheduling, no inventory). Tests: `BoardServiceTest`.

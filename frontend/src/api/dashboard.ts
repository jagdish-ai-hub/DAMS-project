import api from './axios'

export type DashboardPeriod = 'today' | 'mtd'

export interface DashboardKpis {
  collections: number
  expenses: number
  net: number
  cashInHand: number
  pendingReview: number
  /** rev 71 — money on entries still in the workflow (submitted / verified / queried) for the same
   * period: the approved-only headline never hides a pile waiting. */
  collectionsAwaiting: number
  expensesAwaiting: number
}

export interface TrendPoint {
  date: string
  collections: number
  expenses: number
}

export interface NamedAmount {
  name: string
  amount: number
}

export interface BranchComparisonRow {
  branchId: number
  branchCode: string
  branchName: string
  collections: number
  expenses: number
  net: number
  cashInHand: number
  lastClosed: string | null
  variance: number | null
  pendingReview: number
}

export interface DashboardSummary {
  scope: string
  period: DashboardPeriod
  kpis: DashboardKpis
  trend: TrendPoint[]
  byMode: NamedAmount[]
  byCategory: NamedAmount[]
  branchComparison: BranchComparisonRow[]
}

export interface OutstandingItem {
  kind: 'job-card' | 'b2b' | 'claim'
  name: string
  sub: string
  amount: number
  documentNo: string | null
  branchCode: string | null
}

export interface ActivityItem {
  actor: string
  action: string
  documentNo: string | null
  description: string
  amount: number | null
  branchCode: string | null
  at: string
}

/** One receipt or expense line behind a KPI — the reconciliation breakdown row shape. */
export interface MoneyMovementItem {
  /** cash-in / cash-out / opening only appear in the Cash in hand breakdown (rev 71); claim-adjustment only
   * in the Collections breakdown (rev 73) — a closed claim counted at its final amount. */
  kind: 'receipt' | 'expense' | 'cash-in' | 'cash-out' | 'opening' | 'claim-adjustment'
  /** null on the opening row — there is no document behind it. */
  documentId: number | null
  documentNo: string | null
  workflowStatus: string
  date: string
  createdAt: string
  branchCode: string | null
  party: string
  description: string
  modeName: string
  amount: number
}

/** One entry waiting on a person — an unsent draft, a query to fix, or a review to do (rev 71). */
export interface PendingItem {
  type: 'receipt' | 'expense' | 'cash'
  id: number
  documentNo: string | null
  branchId: number | null
  branchCode: string
  party: string
  category: string
  amount: number
  workflowStatus: string
  /** Plain words for why it is with them, e.g. "Awaiting verification". */
  stage: string
  /** An unsent draft (Cashier only) — shown beside, not inside, the Cashier's count. */
  draft: boolean
  /** When it was submitted (created, for a draft) — the days-waiting clock. */
  since: string | null
}

export type PendingHolder = 'CASHIER' | 'ACCOUNTANT' | 'FINANCE_MANAGER'

export interface PendingGroup {
  holder: PendingHolder
  label: string
  count: number
  amount: number
  draftCount: number
  draftAmount: number
  items: PendingItem[]
}

export interface PendingWork {
  groups: PendingGroup[]
}

/** One slice of the claims summary (rev 62). claimed = received + rejected + pending. */
export interface ClaimTotals {
  count: number
  open: number
  claimed: number
  received: number
  /** Closed claims only — claimed minus the Finance Manager's final amount. */
  rejected: number
  /** Open claims only — claimed minus received so far. */
  pending: number
}

export interface ClaimsSummary {
  period: DashboardPeriod
  total: ClaimTotals
  expenses: ClaimTotals
  receipts: ClaimTotals
}

export const dashboardApi = {
  /** rev 62 — claimed / received / rejected / pending, expense + receipt claims. Owner and Finance Manager. */
  claims(period: DashboardPeriod, branchId?: number) {
    return api.get<ClaimsSummary>('/api/v1/dashboard/claims', { params: { period, branchId } })
  },
  summary(period: DashboardPeriod, branchId?: number) {
    return api.get<DashboardSummary>('/api/v1/dashboard/summary', { params: { period, branchId } })
  },
  outstanding(branchId?: number) {
    return api.get<OutstandingItem[]>('/api/v1/dashboard/outstanding', { params: { branchId } })
  },
  activity(branchId?: number, limit = 20) {
    return api.get<ActivityItem[]>('/api/v1/dashboard/activity', { params: { branchId, limit } })
  },
  collectionsBreakdown(period: DashboardPeriod, branchId?: number) {
    return api.get<MoneyMovementItem[]>('/api/v1/dashboard/collections-breakdown', { params: { period, branchId } })
  },
  /** rev 71 — the movements that add up to Cash in hand: opening + everything since the last close. */
  cashBreakdown(branchId?: number) {
    return api.get<MoneyMovementItem[]>('/api/v1/dashboard/cash-breakdown', { params: { branchId } })
  },
  /** rev 71 — who every unfinished entry is waiting on. */
  pendingWork(branchId?: number) {
    return api.get<PendingWork>('/api/v1/dashboard/pending-work', { params: { branchId } })
  },
  expensesBreakdown(period: DashboardPeriod, branchId?: number) {
    return api.get<MoneyMovementItem[]>('/api/v1/dashboard/expenses-breakdown', { params: { period, branchId } })
  },
}

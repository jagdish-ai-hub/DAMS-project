import api from './axios'

export type DashboardPeriod = 'today' | 'mtd'

export interface DashboardKpis {
  collections: number
  expenses: number
  net: number
  cashInHand: number
  pendingReview: number
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
  kind: 'receipt' | 'expense'
  documentId: number
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
  expensesBreakdown(period: DashboardPeriod, branchId?: number) {
    return api.get<MoneyMovementItem[]>('/api/v1/dashboard/expenses-breakdown', { params: { period, branchId } })
  },
}

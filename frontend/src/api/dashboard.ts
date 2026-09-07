import api from './axios'

export type DashboardPeriod = 'today' | 'mtd' | 'custom'

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

export const dashboardApi = {
  summary(period: DashboardPeriod, branchId?: number, from?: string, to?: string) {
    // from/to only apply when period is 'custom' — the backend ignores them
    // otherwise, so older callers passing two args keep working unchanged.
    return api.get<DashboardSummary>('/api/v1/dashboard/summary', { params: { period, branchId, from, to } })
  },
  outstanding(branchId?: number) {
    return api.get<OutstandingItem[]>('/api/v1/dashboard/outstanding', { params: { branchId } })
  },
  activity(branchId?: number, limit = 20) {
    return api.get<ActivityItem[]>('/api/v1/dashboard/activity', { params: { branchId, limit } })
  },
}

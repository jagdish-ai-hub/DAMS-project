import api from './axios'

/** One amount override — an Accountant line override ('receipt' / 'expense') or an FM claim close ('claim'). */
export interface OverrideAuditEntry {
  at: string
  kind: 'receipt' | 'expense' | 'claim'
  actorName: string
  branchId: number | null
  branchCode: string | null
  documentNo: string | null
  lineId: string | null
  amountBefore: number | null
  amountAfter: number | null
  reason: string | null
}

export interface OverrideAuditFilter {
  userId?: number
  branchId?: number
  from?: string // yyyy-mm-dd
  to?: string
}

export const overrideAuditApi = {
  list(filter: OverrideAuditFilter = {}) {
    return api.get<OverrideAuditEntry[]>('/api/v1/override-audit', { params: filter })
  },
}

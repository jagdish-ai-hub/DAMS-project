import api from './axios'

/** URL slugs — must match the backend MasterType. */
export type MasterTypeSlug =
  | 'receive-categories'
  | 'receive-statuses'
  | 'settlement-modes'
  | 'expense-categories'
  | 'expense-sub-categories'
  | 'expense-modes'
  | 'expense-statuses'
  | 'banks'

export interface MasterRow {
  id: number
  type: MasterTypeSlug
  name: string
  active: boolean
  sortOrder: number
  isClaim?: boolean
  requiresBank?: boolean
  requiresRef?: boolean
  isCash?: boolean
  triggersClaim?: boolean
  expenseCategoryId?: number
  limitAmount?: number | null
}

export interface MasterRequest {
  name: string
  active?: boolean
  sortOrder?: number
  isClaim?: boolean
  requiresBank?: boolean
  requiresRef?: boolean
  isCash?: boolean
  triggersClaim?: boolean
  expenseCategoryId?: number
  limitAmount?: number | null
}

export interface MasterUsageRow {
  id: number
  name?: string
  useCount: number
  usedLast90d?: boolean
}

export const mastersApi = {
  list(type: MasterTypeSlug, expenseCategoryId?: number) {
    const params = expenseCategoryId != null ? { expenseCategoryId } : undefined
    return api.get<MasterRow[]>(`/api/v1/masters/${type}`, { params })
  },
  create(type: MasterTypeSlug, data: MasterRequest) {
    return api.post<MasterRow>(`/api/v1/masters/${type}`, data)
  },
  update(type: MasterTypeSlug, id: number, data: MasterRequest) {
    return api.patch<MasterRow>(`/api/v1/masters/${type}/${id}`, data)
  },
  /**
   * 90-day usage counts per row, for the deactivate guard. The backend may not
   * implement this yet — callers treat a 404 as "no usage data" and skip
   * silently instead of erroring.
   */
  usage(type: MasterTypeSlug) {
    return api.get<MasterUsageRow[]>(`/api/v1/masters/${type}/usage`)
  },
}

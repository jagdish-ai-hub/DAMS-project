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
  | 'claim-types'
  | 'upi-vpas'

/** Roles that can be granted a job-card business status. */
export type StatusRole = 'CASHIER' | 'ACCOUNTANT' | 'FINANCE_MANAGER'

export interface MasterRow {
  id: number
  type: MasterTypeSlug
  name: string
  active: boolean
  sortOrder: number
  requiresBank?: boolean
  requiresRef?: boolean
  triggersClaim?: boolean
  expenseCategoryId?: number
  limitAmount?: number | null
  vpa?: string
  /** receive-statuses only: still usable, but on its way out — shown last and marked. */
  deprecated?: boolean
  /** receive-statuses only: which roles may set this status. */
  allowedRoles?: StatusRole[]
}

export interface MasterRequest {
  name: string
  active?: boolean
  sortOrder?: number
  requiresBank?: boolean
  requiresRef?: boolean
  triggersClaim?: boolean
  expenseCategoryId?: number
  limitAmount?: number | null
  vpa?: string
  deprecated?: boolean
  /** Replaces the grants wholesale. Omit to leave them unchanged. */
  allowedRoles?: StatusRole[]
}

export const mastersApi = {
  list(type: MasterTypeSlug, expenseCategoryId?: number) {
    const params = expenseCategoryId != null ? { expenseCategoryId } : undefined
    return api.get<MasterRow[]>(`/api/v1/masters/${type}`, { params })
  },
  /** Only the rows the signed-in user may pick — role-filtered for receive-statuses. */
  listSelectable(type: MasterTypeSlug) {
    return api.get<MasterRow[]>(`/api/v1/masters/${type}/mine`)
  },
  create(type: MasterTypeSlug, data: MasterRequest) {
    return api.post<MasterRow>(`/api/v1/masters/${type}`, data)
  },
  update(type: MasterTypeSlug, id: number, data: MasterRequest) {
    return api.patch<MasterRow>(`/api/v1/masters/${type}/${id}`, data)
  },
}

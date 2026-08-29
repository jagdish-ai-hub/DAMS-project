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
  expenseCategoryId?: number
  limitAmount?: number | null
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
}

import api from './axios'

/**
 * Owner monthly expense budgets (FEAT: budgets UI). The backend owns the
 * /api/v1/budgets resource; this module is only a typed wrapper. Month keys
 * are 'YYYYMM' (e.g. '202609'). If the backend does not implement budgets yet
 * (404), callers hide the budget UI gracefully instead of erroring.
 */
export interface BudgetRow {
  categoryId: number
  categoryName?: string
  monthKey: string
  cap: number
}

/** Backend shape (BudgetResponse): {id, categoryId, monthKey, capAmount}. */
interface BudgetBackendRow {
  categoryId: number
  categoryName?: string
  monthKey: string
  capAmount?: number
  cap?: number
}

function normalize(r: BudgetBackendRow): BudgetRow {
  return { categoryId: r.categoryId, categoryName: r.categoryName, monthKey: r.monthKey, cap: Number(r.capAmount ?? r.cap ?? 0) }
}

export const budgetsApi = {
  async list(month: string) {
    const res = await api.get<BudgetBackendRow[]>('/api/v1/budgets', { params: { month } })
    return { ...res, data: (res.data ?? []).map(normalize) }
  },
  async upsert(categoryId: number, monthKey: string, cap: number) {
    // Backend upserts via PUT {categoryId, monthKey, capAmount} (V23 expense_budget).
    const res = await api.put<BudgetBackendRow>('/api/v1/budgets', { categoryId, monthKey, capAmount: cap })
    return { ...res, data: normalize(res.data) }
  },
}

/** '2026-09-07' → '202609'. Returns '' for anything unparseable. */
export function monthKeyOf(isoDate: string): string {
  const m = /^(\d{4})-(\d{2})-\d{2}/.exec(isoDate)
  return m ? `${m[1]}${m[2]}` : ''
}

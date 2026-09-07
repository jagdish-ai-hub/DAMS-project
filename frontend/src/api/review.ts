import api from './axios'
import type { ReceiveDocument } from './receipts'
import type { ExpenseDocument } from './expenses'
import type { CashDocument } from './cash'

export type ReviewType = 'receipt' | 'expense' | 'cash'

/** One row of a review queue — the overview panel is derived from a list of these. */
export interface ReviewQueueItem {
  type: ReviewType
  id: number
  documentNo: string | null
  branchId: number
  branchCode: string
  partyName: string
  categoryName: string
  amount: number
  overLimit: boolean
  hasOverride: boolean
  submittedAt: string | null
}

/** The Finance Manager's queue for one document type (open claims / recently closed are receipts only). */
export interface FmQueue {
  awaitingApproval: ReviewQueueItem[]
  openClaims: ReviewQueueItem[]
  recentlyClosed: ReviewQueueItem[]
}

type AnyReviewDoc = ReceiveDocument | ExpenseDocument | CashDocument

const SEGMENT: Record<ReviewType, string> = {
  receipt: 'receipts',
  expense: 'expenses',
  cash: 'cash-documents',
}

const base = (t: ReviewType) => `/api/v1/${SEGMENT[t]}`
const fmSegment = (t: ReviewType) => (t === 'expense' ? 'expenses' : t === 'cash' ? 'cash' : 'receipts')

export interface BulkVerifyResponse {
  verifiedCount: number
  verifiedIds: number[]
  skippedReasons: string[]
}

export const reviewApi = {
  receiptQueue() {
    return api.get<ReviewQueueItem[]>('/api/v1/review/receipts')
  },
  expenseQueue() {
    return api.get<ReviewQueueItem[]>('/api/v1/review/expenses')
  },
  cashQueue() {
    return api.get<ReviewQueueItem[]>('/api/v1/review/cash')
  },
  queue(t: ReviewType) {
    return api.get<ReviewQueueItem[]>(`/api/v1/review/${SEGMENT[t] === 'cash-documents' ? 'cash' : SEGMENT[t]}`)
  },
  fmQueue(t: ReviewType) {
    return api.get<FmQueue>(`/api/v1/review/fm/${fmSegment(t)}`)
  },

  verify(t: ReviewType, id: number) {
    return api.post<AnyReviewDoc>(`${base(t)}/${id}/verify`)
  },
  bulkVerify(t: 'receipt' | 'expense', ids: number[]) {
    return api.post<BulkVerifyResponse>(`${base(t)}/bulk-verify`, { ids })
  },
  /**
   * FM bulk approve — server bulk endpoint (VERIFIED → APPROVED, maker-checker
   * skips server-side). Falls back to one-by-one approve if the server path
   * is unavailable, keeping the same response shape for shared UI.
   */
  async bulkApprove(t: 'receipt' | 'expense', ids: number[]): Promise<{ data: BulkVerifyResponse }> {
    try {
      const res = await api.post<BulkVerifyResponse>(`${base(t)}/bulk-approve`, { ids })
      return res
    } catch (e) {
      const status = (e as { response?: { status?: number } })?.response?.status
      if (status !== 404 && status !== 405) throw e
      const verifiedIds: number[] = []
      const skippedReasons: string[] = []
      for (const id of ids) {
        try {
          await api.post(`${base(t)}/${id}/approve`)
          verifiedIds.push(id)
        } catch (err) {
          const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
            ?? `Document #${id} could not be approved.`
          skippedReasons.push(`#${id}: ${msg}`)
        }
      }
      return { data: { verifiedCount: verifiedIds.length, verifiedIds, skippedReasons } }
    }
  },
  approve(t: ReviewType, id: number) {
    return api.post<AnyReviewDoc>(`${base(t)}/${id}/approve`)
  },
  query(t: ReviewType, id: number, note: string) {
    return api.post<AnyReviewDoc>(`${base(t)}/${id}/query`, { note })
  },
  reject(t: ReviewType, id: number, reason: string) {
    return api.post<AnyReviewDoc>(`${base(t)}/${id}/reject`, { reason })
  },
  overrideLine(t: ReviewType, id: number, lineNo: number, amount: number, reason: string) {
    return api.post<ReceiveDocument | ExpenseDocument>(`${base(t)}/${id}/lines/${lineNo}/override`, { amount, reason })
  },
  closeExpense(id: number) {
    return api.post<ExpenseDocument>(`/api/v1/expenses/${id}/close`)
  },
}

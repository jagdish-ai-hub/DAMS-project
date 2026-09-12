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
  workflowStatus: string
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
  skippedCount: number
  verifiedIds: number[]
  skippedIds: number[]
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
  /** What this accountant has already verified or later (VERIFIED/APPROVED[/CLOSED]) — the
   * "goes missing once reviewed" gap: this is what lets it show up again. */
  verifiedQueue(t: ReviewType) {
    const segment = SEGMENT[t] === 'cash-documents' ? 'cash' : SEGMENT[t]
    return api.get<ReviewQueueItem[]>(`/api/v1/review/${segment}/verified`)
  },
  fmQueue(t: ReviewType) {
    return api.get<FmQueue>(`/api/v1/review/fm/${fmSegment(t)}`)
  },

  verify(t: ReviewType, id: number) {
    return api.post<AnyReviewDoc>(`${base(t)}/${id}/verify`)
  },
  bulkVerify(t: 'receipt' | 'expense', ids: number[]) {
    return api.post<BulkVerifyResponse>(`${base(t)}/bulk-verify`, { documentIds: ids })
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

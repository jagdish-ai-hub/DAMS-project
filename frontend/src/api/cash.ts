import api from './axios'
import type { DocumentHistoryEntry } from './receipts'

export type { DocumentHistoryEntry }

export type CashDirection = 'IN' | 'OUT'
export type CashWorkflowStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'VERIFIED'
  | 'APPROVED'
  | 'QUERIED'
  | 'REJECTED'

export interface CashDocument {
  id: number
  documentNo: string | null
  direction: CashDirection
  transactionDate: string
  amount: number
  bankId: number | null
  bankName: string | null
  transactionRef: string | null
  remark: string | null
  workflowStatus: CashWorkflowStatus
  branchId: number
  branchCode: string | null
  branchName: string | null
  createdBy: number
  createdByName: string | null
  lastModifiedBy: number | null
  createdAt: string
  submittedAt: string | null
  history: DocumentHistoryEntry[]
}

export interface CashDayClose {
  id: number
  branchId: number
  branchCode: string | null
  closeDate: string
  openingAmount: number
  computedClosing: number
  countedAmount: number
  variance: number
  varianceRemark: string | null
  closedBy: number
  closedByName: string | null
  closedAt: string
}

export interface CashDrawer {
  branchId: number
  branchCode: string | null
  branchName: string | null
  date: string
  opening: number
  openingSet: boolean
  cashReceipts: number
  cashIn: number
  cashExpenses: number
  cashOut: number
  computedPosition: number
  closed: boolean
  close: CashDayClose | null
  movements: CashDocument[]
}

export interface CreateCashDocumentRequest {
  direction: CashDirection
  transactionDate: string
  amount: number
  bankId?: number | null
  transactionRef?: string
  remark?: string
  submit?: boolean
}

export interface CashDocumentPatchRequest {
  direction?: CashDirection
  transactionDate?: string
  amount?: number
  bankId?: number | null
  transactionRef?: string
  remark?: string
}

export interface CloseDayRequest {
  branchId?: number
  closeDate: string
  countedAmount: number
  varianceRemark?: string
}

export interface CashOpeningRequest {
  branchId: number
  openingDate: string
  amount: number
}

export const cashApi = {
  drawer(date: string, branchId?: number) {
    return api.get<CashDrawer>('/api/v1/cash/drawer', { params: { date, branchId } })
  },
  get(id: number) {
    return api.get<CashDocument>(`/api/v1/cash-documents/${id}`)
  },
  create(data: CreateCashDocumentRequest) {
    return api.post<CashDocument>('/api/v1/cash-documents', data)
  },
  patch(id: number, data: CashDocumentPatchRequest) {
    return api.patch<CashDocument>(`/api/v1/cash-documents/${id}`, data)
  },
  submit(id: number) {
    return api.post<CashDocument>(`/api/v1/cash-documents/${id}/submit`)
  },
  resubmit(id: number) {
    return api.post<CashDocument>(`/api/v1/cash-documents/${id}/resubmit`)
  },
  remove(id: number) {
    return api.delete(`/api/v1/cash-documents/${id}`)
  },
  closeDay(data: CloseDayRequest) {
    return api.post<CashDayClose>('/api/v1/cash/close-day', data)
  },
  setOpening(data: CashOpeningRequest) {
    return api.post<CashDrawer>('/api/v1/cash/opening', data)
  },
}

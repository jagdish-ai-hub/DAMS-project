import api from './axios'
import type { Attachment, DocumentHistoryEntry, SignedUrl } from './receipts'

export type { DocumentHistoryEntry }

export type ExpenseWorkflowStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'VERIFIED'
  | 'APPROVED'
  | 'QUERIED'
  | 'REJECTED'
  | 'CLOSED'

export interface ExpenseLine {
  id: number
  lineNo: number
  lineId: string | null
  transactionDate: string
  subCategoryId: number
  subCategoryName: string | null
  limitAmount: number | null
  overLimit: boolean
  expenseModeId: number
  expenseModeName: string | null
  amount: number
  originalAmount: number | null
  bankId: number | null
  bankName: string | null
  transactionRef: string | null
  remark: string | null
  overridden: boolean
  overrideReason: string | null
  overriddenAt: string | null
  attachmentCount: number
  createdAt: string
}

export interface ExpenseDocument {
  id: number
  documentNo: string | null
  workflowStatus: ExpenseWorkflowStatus
  overLimit: boolean
  branchId: number
  branchCode: string | null
  branchName: string | null
  jobCardId: number | null
  jobCardReference: string | null
  receiverId: number
  receiverName: string | null
  receiverPhone: string | null
  customerId: number | null
  customerName: string | null
  vehicleNo: string | null
  expenseCategoryId: number
  expenseCategoryName: string | null
  businessStatusId: number
  businessStatusName: string | null
  businessStatusTriggersClaim: boolean
  claimEligible: boolean
  totalAmount: number
  createdBy: number
  createdByName: string | null
  lastModifiedBy: number | null
  createdAt: string
  submittedAt: string | null
  lines: ExpenseLine[]
  history: DocumentHistoryEntry[]
}

/** One expense line as sent to the API. */
export interface ExpenseLineInput {
  transactionDate: string
  subCategoryId: number
  expenseModeId: number
  amount: number
  bankId?: number | null
  transactionRef?: string
  remark?: string
}

export interface CreateExpenseRequest {
  jobCardId?: number
  receiverId?: number
  receiverName?: string
  receiverPhone?: string
  expenseCategoryId: number
  businessStatusId: number
  lines: ExpenseLineInput[]
  submit?: boolean
}

export interface ExpensePatchRequest {
  jobCardId?: number
  receiverId?: number
  receiverName?: string
  receiverPhone?: string
  expenseCategoryId?: number
  businessStatusId?: number
}

export const expensesApi = {
  get(id: number) {
    return api.get<ExpenseDocument>(`/api/v1/expenses/${id}`)
  },
  create(data: CreateExpenseRequest) {
    return api.post<ExpenseDocument>('/api/v1/expenses', data)
  },
  patch(id: number, data: ExpensePatchRequest) {
    return api.patch<ExpenseDocument>(`/api/v1/expenses/${id}`, data)
  },
  submit(id: number) {
    return api.post<ExpenseDocument>(`/api/v1/expenses/${id}/submit`)
  },
  resubmit(id: number) {
    return api.post<ExpenseDocument>(`/api/v1/expenses/${id}/resubmit`)
  },
  transferToClaim(id: number) {
    return api.post<ExpenseDocument>(`/api/v1/expenses/${id}/transfer-to-claim`)
  },
  addLine(id: number, line: ExpenseLineInput) {
    return api.post<ExpenseDocument>(`/api/v1/expenses/${id}/lines`, line)
  },
  updateLine(id: number, lineNo: number, line: ExpenseLineInput) {
    return api.patch<ExpenseDocument>(`/api/v1/expenses/${id}/lines/${lineNo}`, line)
  },
  deleteLine(id: number, lineNo: number) {
    return api.delete<ExpenseDocument>(`/api/v1/expenses/${id}/lines/${lineNo}`)
  },

  // --- attachments (same shapes as the receive side) ---
  documentAttachments(id: number) {
    return api.get<Attachment[]>(`/api/v1/expenses/${id}/attachments`)
  },
  lineAttachments(id: number, lineNo: number) {
    return api.get<Attachment[]>(`/api/v1/expenses/${id}/lines/${lineNo}/attachments`)
  },
  attachToDocument(id: number, file: File) {
    const form = new FormData()
    form.append('file', file)
    return api.post<Attachment>(`/api/v1/expenses/${id}/attachments`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  attachToLine(id: number, lineNo: number, file: File) {
    const form = new FormData()
    form.append('file', file)
    return api.post<Attachment>(`/api/v1/expenses/${id}/lines/${lineNo}/attachments`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  signedUrl(attachmentId: number) {
    return api.get<SignedUrl>(`/api/v1/attachments/${attachmentId}`)
  },
  deleteAttachment(attachmentId: number) {
    return api.delete(`/api/v1/attachments/${attachmentId}`)
  },
}

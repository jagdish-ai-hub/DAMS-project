import api from './axios'
import type { Attachment, DocumentHistoryEntry, SignedUrl } from './receipts'

export type { DocumentHistoryEntry }

export type ExpenseWorkflowStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'VERIFIED'
  | 'APPROVED'
  | 'QUERIED'
  | 'FM_QUERIED'
  | 'REJECTED'
  | 'CLOSED'

/** FM pre-approval of an over-limit expense (rev 53); null = never requested. */
export type PreApprovalStatus = 'PENDING' | 'APPROVED' | 'QUERIED'

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
  invoiceNo: string | null
  dbmId: string | null
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
  /** rev 53 — FM pre-approval of an over-limit expense, before it's submitted. */
  preApprovalStatus: PreApprovalStatus | null
  preApprovedAmount: number | null
  preApprovedByName: string | null
  preApprovedAt: string | null
  approvalRequestedAt: string | null
  /** Approved and the total is still within the approved amount — may be submitted / closed. */
  preApprovalCovers: boolean
  /** rev 54 — needs FM approval: over a limit, or its status is flagged "requires FM approval". */
  needsFmApproval: boolean
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
  customerName?: string
  vehicleNo?: string
  invoiceNo?: string
  dbmId?: string
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
  customerName?: string
  vehicleNo?: string
  invoiceNo?: string
  dbmId?: string
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
  /** rev 53 — send an over-limit draft to the Finance Manager for pre-approval instead of submitting. */
  requestApproval(id: number) {
    return api.post<ExpenseDocument>(`/api/v1/expenses/${id}/request-approval`)
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
  attachToDocument(id: number, file: File, comment?: string) {
    const form = new FormData()
    form.append('file', file)
    if (comment) form.append('comment', comment)
    return api.post<Attachment>(`/api/v1/expenses/${id}/attachments`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  attachToLine(id: number, lineNo: number, file: File, comment?: string) {
    const form = new FormData()
    form.append('file', file)
    if (comment) form.append('comment', comment)
    return api.post<Attachment>(`/api/v1/expenses/${id}/lines/${lineNo}/attachments`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  signedUrl(attachmentId: number) {
    return api.get<SignedUrl>(`/api/v1/attachments/${attachmentId}`)
  },
  updateAttachmentComment(attachmentId: number, comment: string) {
    return api.patch<Attachment>(`/api/v1/attachments/${attachmentId}`, { comment })
  },
  deleteAttachment(attachmentId: number) {
    return api.delete(`/api/v1/attachments/${attachmentId}`)
  },
}

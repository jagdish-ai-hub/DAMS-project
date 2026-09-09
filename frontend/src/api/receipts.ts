import api from './axios'

export type WorkflowStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'VERIFIED'
  | 'APPROVED'
  | 'QUERIED'
  | 'REJECTED'

/** One humanised audit event — the review pane's History card and the cashier's "why queried". */
export interface DocumentHistoryEntry {
  actor: string
  action: string
  note: string | null
  at: string
}

export interface SettlementLine {
  id: number
  lineNo: number
  lineId: string | null
  transactionDate: string
  settlementModeId: number
  settlementModeName: string | null
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

export interface ReceiveDocument {
  id: number
  documentNo: string | null
  workflowStatus: WorkflowStatus
  settled: boolean
  jobCardId: number
  jobCardReference: string
  branchId: number
  branchCode: string | null
  branchName: string | null
  customerId: number
  customerName: string | null
  customerPhone: string | null
  vehicleNo: string | null
  dbmId: string | null
  invoiceNo: string | null
  invoiceAmount: number | null
  b2b: boolean
  gstNo: string | null
  categoryId: number
  categoryName: string | null
  isClaim: boolean
  businessStatusId: number
  businessStatusName: string | null
  pendingAmount: number
  settledViaClaimClose: boolean
  claimFinalAmount: number | null
  claimOverridden: boolean
  claimOverrideReason: string | null
  totalReceived: number
  canRecordPayment: boolean
  createdBy: number
  createdByName: string | null
  lastModifiedBy: number | null
  createdAt: string
  submittedAt: string | null
  lines: SettlementLine[]
  history: DocumentHistoryEntry[]
}

/** One settlement line as sent to the API. */
export interface SettlementLineInput {
  transactionDate: string
  settlementModeId: number
  amount: number
  bankId?: number | null
  transactionRef?: string
  remark?: string
}

export interface CreateReceiptRequest {
  jobCardId?: number
  // inline job card (used only when jobCardId is absent)
  customerId?: number
  customerName?: string
  customerPhone?: string
  vehicleId?: number
  vehicleNo?: string
  dbmId?: string
  invoiceNo?: string
  invoiceAmount?: number
  b2b?: boolean
  gstNo?: string
  categoryId?: number
  businessStatusId?: number
  lines: SettlementLineInput[]
  submit?: boolean
}

export interface Attachment {
  id: number
  filename: string
  contentType: string
  sizeBytes: number
  frozen: boolean
  uploadedAt: string
}

export interface SignedUrl {
  url: string
  filename: string
  contentType: string
}

export const receiptsApi = {
  get(id: number) {
    return api.get<ReceiveDocument>(`/api/v1/receipts/${id}`)
  },
  create(data: CreateReceiptRequest) {
    return api.post<ReceiveDocument>('/api/v1/receipts', data)
  },
  submit(id: number) {
    return api.post<ReceiveDocument>(`/api/v1/receipts/${id}/submit`)
  },
  resubmit(id: number) {
    return api.post<ReceiveDocument>(`/api/v1/receipts/${id}/resubmit`)
  },
  addLine(id: number, line: SettlementLineInput) {
    return api.post<ReceiveDocument>(`/api/v1/receipts/${id}/lines`, line)
  },
  updateLine(id: number, lineNo: number, line: SettlementLineInput) {
    return api.patch<ReceiveDocument>(`/api/v1/receipts/${id}/lines/${lineNo}`, line)
  },
  deleteLine(id: number, lineNo: number) {
    return api.delete<ReceiveDocument>(`/api/v1/receipts/${id}/lines/${lineNo}`)
  },

  // --- attachments ---
  documentAttachments(id: number) {
    return api.get<Attachment[]>(`/api/v1/receipts/${id}/attachments`)
  },
  lineAttachments(id: number, lineNo: number) {
    return api.get<Attachment[]>(`/api/v1/receipts/${id}/lines/${lineNo}/attachments`)
  },
  attachToDocument(id: number, file: File) {
    const form = new FormData()
    form.append('file', file)
    // let the browser set multipart/form-data + boundary (override the axios JSON default)
    return api.post<Attachment>(`/api/v1/receipts/${id}/attachments`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  attachToLine(id: number, lineNo: number, file: File) {
    const form = new FormData()
    form.append('file', file)
    return api.post<Attachment>(`/api/v1/receipts/${id}/lines/${lineNo}/attachments`, form, {
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

import api from './axios'

export interface JobCard {
  id: number
  reference: string
  branchId: number
  branchCode: string | null
  branchName: string | null
  /** null for a job card opened from an Expense that has no customer yet. */
  customerId: number | null
  customerName: string | null
  customerPhone: string | null
  vehicleId: number | null
  vehicleNo: string | null
  dbmId: string | null
  invoiceNo: string | null
  invoiceAmount: number | null
  b2b: boolean
  gstNo: string | null
  categoryId: number
  categoryName: string | null
  claimTypeId: number | null
  claimTypeName: string | null
  isClaim: boolean
  businessStatusId: number
  businessStatusName: string | null
  pendingAmount: number
  settledViaClaimClose: boolean
  claimFinalAmount: number | null
  claimOverridden: boolean
  claimOverrideReason: string | null
  claimClosedByName: string | null
  claimClosedAt: string | null
  canRecordPayment: boolean
  createdAt: string
  /** Numbered DAMS-Receive-IDs ("Ooriba ID"), newest first. */
  receiveDocumentNos: string[]
}

/** One row of the branch-scoped job-card picker. */
export interface JobCardSearchHit {
  id: number
  reference: string
  branchId: number
  branchCode: string
  customerId: number | null
  customerName: string | null
  vehicleId: number | null
  vehicleNo: string | null
  dbmId: string | null
  invoiceNo: string | null
  categoryId: number
  createdAt: string
  /** Numbered DAMS-Receive-IDs ("Ooriba ID"), newest first; empty until a receipt is submitted. */
  receiveDocumentNos: string[]
}

export interface AttachCustomerRequest {
  customerId?: number
  customerName?: string
  customerPhone?: string
}

export interface CloseClaimRequest {
  finalAmount: number
  reason?: string
}

export interface JobCardCreateRequest {
  customerId?: number
  customerName?: string
  customerPhone?: string
  vehicleId?: number
  vehicleNo?: string
  branchId?: number
  categoryId: number
  /** Warranty / AMC / CGW, etc. Set to make this job card a claim from the start. */
  claimTypeId?: number
  businessStatusId: number
  dbmId?: string
  invoiceNo?: string
  invoiceAmount?: number
  b2b?: boolean
  gstNo?: string
}

export interface JobCardPatchRequest {
  invoiceNo?: string
  invoiceAmount?: number
  clearInvoiceAmount?: boolean
  vehicleNo?: string
  dbmId?: string
  b2b?: boolean
  gstNo?: string
  categoryId?: number
  /** 0 clears it (job card is no longer a claim); undefined leaves it unchanged. */
  claimTypeId?: number
  businessStatusId?: number
}

export const jobCardsApi = {
  search(params: { q?: string; customerId?: number; vehicleId?: number }) {
    return api.get<JobCardSearchHit[]>('/api/v1/job-cards', { params })
  },
  attachCustomer(id: number, data: AttachCustomerRequest) {
    return api.post<JobCard>(`/api/v1/job-cards/${id}/attach-customer`, data)
  },
  get(id: number) {
    return api.get<JobCard>(`/api/v1/job-cards/${id}`)
  },
  create(data: JobCardCreateRequest) {
    return api.post<JobCard>('/api/v1/job-cards', data)
  },
  patch(id: number, data: JobCardPatchRequest) {
    return api.patch<JobCard>(`/api/v1/job-cards/${id}`, data)
  },
  closeClaim(id: number, data: CloseClaimRequest) {
    return api.post<JobCard>(`/api/v1/job-cards/${id}/close-claim`, data)
  },
}

import api from './axios'

export interface JobCard {
  id: number
  reference: string
  branchId: number
  branchCode: string | null
  branchName: string | null
  customerId: number
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
  serviceDueDate: string | null
  stuckReason: string | null
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
  businessStatusId: number
  dbmId?: string
  invoiceNo?: string
  invoiceAmount?: number
  b2b?: boolean
  gstNo?: string
  serviceDueDate?: string | null
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
  businessStatusId?: number
  serviceDueDate?: string | null
  clearServiceDueDate?: boolean
  stuckReason?: string | null
}

export interface WipRow {
  jobCardId: number
  reference: string
  branchId: number
  branchCode: string
  customerName: string
  vehicleNo: string | null
  categoryName: string
  businessStatusName: string
  openedDate: string
  ageDays: number
  stuckReason: string | null
  pendingAmount: number
}

export interface RenewalRow {
  jobCardId: number
  reference: string
  branchId: number
  branchCode: string
  customerName: string
  customerPhone: string | null
  vehicleNo: string | null
  serviceDueDate: string
  daysUntilDue: number
}

export const jobCardsApi = {
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
  board() {
    return api.get<WipRow[]>('/api/v1/job-cards/board')
  },
  renewals() {
    return api.get<RenewalRow[]>('/api/v1/job-cards/renewals')
  },
}

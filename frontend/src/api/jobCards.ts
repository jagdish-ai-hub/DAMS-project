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
}

export interface CloseClaimRequest {
  finalAmount: number
  reason?: string
}

export interface JobCardCreateRequest {
  branchId?: number
  customerId?: number
  customerName?: string
  customerPhone?: string
  vehicleNo?: string
  dbmId?: string
  categoryId?: number
  businessStatusId?: number
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
  businessStatusId?: number
}

export const jobCardsApi = {
  create(data: JobCardCreateRequest) {
    return api.post<JobCard>('/api/v1/job-cards', data)
  },
  get(id: number) {
    return api.get<JobCard>(`/api/v1/job-cards/${id}`)
  },
  patch(id: number, data: JobCardPatchRequest) {
    return api.patch<JobCard>(`/api/v1/job-cards/${id}`, data)
  },
  closeClaim(id: number, data: CloseClaimRequest) {
    return api.post<JobCard>(`/api/v1/job-cards/${id}/close-claim`, data)
  },
}

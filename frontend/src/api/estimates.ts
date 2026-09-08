import api from './axios'

export interface EstimateLine {
  lineNo: number
  description: string
  amount: number
}

export interface Estimate {
  id: number
  jobCardId: number
  status: 'DRAFT' | 'APPROVED' | 'REJECTED' | 'SUPERSEDED'
  total: number
  lines: EstimateLine[]
  invoiceAmount: number | null
  varianceVsInvoice: number | null
  approvedBy: number | null
  decidedAt: string | null
  decisionNote: string | null
  createdBy: number
  createdAt: string
}

export const estimatesApi = {
  forJobCard(jobCardId: number) {
    return api.get<Estimate[]>('/api/v1/estimates', { params: { jobCardId } })
  },
  create(data: { jobCardId: number; lines: { description: string; amount: number }[] }) {
    return api.post<Estimate>('/api/v1/estimates', data)
  },
  approve(id: number, note?: string) {
    return api.post<Estimate>(`/api/v1/estimates/${id}/approve`, null, {
      params: note ? { note } : undefined,
    })
  },
  reject(id: number, note?: string) {
    return api.post<Estimate>(`/api/v1/estimates/${id}/reject`, null, {
      params: note ? { note } : undefined,
    })
  },
}

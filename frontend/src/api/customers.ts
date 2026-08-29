import api from './axios'

export interface VehicleRef {
  id: number
  vehicleNo: string
}

export interface Customer {
  id: number
  name: string
  phone: string | null
  vehicles: VehicleRef[]
  createdAt: string
}

export interface CustomerRequest {
  name: string
  phone?: string
}

export interface JobCardSummary {
  id: number
  reference: string
  branchCode: string | null
  branchName: string | null
  categoryName: string | null
  isClaim: boolean
  businessStatusName: string | null
  dbmId: string | null
  invoiceNo: string | null
  invoiceAmount: number | null
  received: number
  balance: number
  workflowStatus: string | null
  canRecordPayment: boolean
  receiveDocumentId: number | null
  receiveDocumentSettled: boolean
  createdAt: string
}

export interface TimelineEntry {
  date: string
  description: string
  lineId: string
  mode: string | null
  amount: number
}

export interface CustomerHistory {
  customerId: number
  customerName: string
  phone: string | null
  vehicles: VehicleRef[]
  jobCardCount: number
  totalInvoiced: number
  totalReceived: number
  totalOutstanding: number
  jobCards: JobCardSummary[]
  timeline: TimelineEntry[]
}

export const customersApi = {
  search(q: string) {
    return api.get<Customer[]>('/api/v1/customers', { params: q ? { q } : undefined })
  },
  get(id: number) {
    return api.get<Customer>(`/api/v1/customers/${id}`)
  },
  history(id: number) {
    return api.get<CustomerHistory>(`/api/v1/customers/${id}/history`)
  },
  create(data: CustomerRequest) {
    return api.post<Customer>('/api/v1/customers', data)
  },
  update(id: number, data: CustomerRequest) {
    return api.patch<Customer>(`/api/v1/customers/${id}`, data)
  },
}

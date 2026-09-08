import api from './axios'

export interface Followup {
  id: number
  receiveDocumentId: number
  documentNo: string | null
  customerName: string
  customerPhone: string | null
  branchId: number
  branchCode: string
  dueDate: string
  promiseNote: string | null
  status: 'OPEN' | 'PROMISED' | 'CLOSED'
  overdue: boolean
  daysOverdue: number
  pendingAmount: number
  remindedCount: number
  lastRemindedAt: string | null
  createdBy: number
  createdAt: string
}

export interface Defaulter {
  customerId: number
  customerName: string
  customerPhone: string | null
  totalOutstanding: number
  openFollowups: number
  overdueFollowups: number
  oldestOverdueDays: number | null
}

export const followupsApi = {
  list(overdueOnly = false) {
    return api.get<Followup[]>('/api/v1/followups', { params: overdueOnly ? { overdueOnly: true } : undefined })
  },
  defaulters() {
    return api.get<Defaulter[]>('/api/v1/followups/defaulters')
  },
  open(data: { receiveDocumentId: number; dueDate: string; promiseNote?: string }) {
    return api.post<Followup>('/api/v1/followups', data)
  },
  close(id: number) {
    return api.post<Followup>(`/api/v1/followups/${id}/close`)
  },
}

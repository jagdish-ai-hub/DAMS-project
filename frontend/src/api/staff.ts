import api from './axios'

export interface StaffMember {
  id: number
  name: string
  phone: string | null
  active: boolean
  outstanding: number
}

export interface StaffEntry {
  id: number
  staffId: number
  kind: 'ADVANCE' | 'RECOVERY'
  amount: number
  txnDate: string
  note: string | null
  createdBy: number
  createdAt: string
}

export const staffApi = {
  members() {
    return api.get<StaffMember[]>('/api/v1/staff')
  },
  add(data: { name: string; phone?: string }) {
    return api.post<StaffMember>('/api/v1/staff', data)
  },
  deactivate(id: number) {
    return api.post<StaffMember>(`/api/v1/staff/${id}/deactivate`)
  },
  entries(id: number) {
    return api.get<StaffEntry[]>(`/api/v1/staff/${id}/entries`)
  },
  record(id: number, data: { kind: string; amount: number; txnDate: string; note?: string }) {
    return api.post<StaffEntry>(`/api/v1/staff/${id}/entries`, data)
  },
}

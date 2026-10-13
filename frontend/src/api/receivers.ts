import api from './axios'

export interface Receiver {
  id: number
  name: string
  phone: string | null
  active: boolean
  createdAt: string
  /** rev 76 — the expense categories this vendor supplies. */
  categoryIds: number[]
}

export interface ReceiverRequest {
  name: string
  phone?: string
  active?: boolean
  /** Replaces the mapping; leave undefined to keep it as it is. */
  categoryIds?: number[]
}

export const receiversApi = {
  list() {
    return api.get<Receiver[]>('/api/v1/receivers')
  },
  /** Expense-form vendor search (rev 76): active vendors by name or phone, those mapped to `categoryId` first. */
  search(q: string, categoryId?: number | null) {
    return api.get<Receiver[]>('/api/v1/receivers/search', { params: { q, categoryId: categoryId ?? undefined } })
  },
  create(data: ReceiverRequest) {
    return api.post<Receiver>('/api/v1/receivers', data)
  },
  update(id: number, data: ReceiverRequest) {
    return api.patch<Receiver>(`/api/v1/receivers/${id}`, data)
  },
}

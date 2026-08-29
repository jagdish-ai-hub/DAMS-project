import api from './axios'

export interface Receiver {
  id: number
  name: string
  phone: string | null
  active: boolean
  createdAt: string
}

export interface ReceiverRequest {
  name: string
  phone?: string
  active?: boolean
}

export const receiversApi = {
  list() {
    return api.get<Receiver[]>('/api/v1/receivers')
  },
  create(data: ReceiverRequest) {
    return api.post<Receiver>('/api/v1/receivers', data)
  },
  update(id: number, data: ReceiverRequest) {
    return api.patch<Receiver>(`/api/v1/receivers/${id}`, data)
  },
}

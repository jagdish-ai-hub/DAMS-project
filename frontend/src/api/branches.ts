import api from './axios'

export interface Branch {
  id: number
  name: string
  code: string
  active: boolean
  assignedUserCount: number
  createdAt: string
}

export interface BranchRequest {
  name: string
  code: string
  active?: boolean
}

export const branchesApi = {
  list() {
    return api.get<Branch[]>('/api/v1/branches')
  },
  create(data: BranchRequest) {
    return api.post<Branch>('/api/v1/branches', data)
  },
  update(id: number, data: BranchRequest) {
    return api.patch<Branch>(`/api/v1/branches/${id}`, data)
  },
}

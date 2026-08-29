import api from './axios'
import type { Role } from '../auth/AuthContext'

export interface TeamUser {
  id: number
  name: string
  email: string
  role: Role
  active: boolean
  invitePending: boolean
  homeBranchId: number | null
  branchIds: number[] | null
  branchAccessLabel: string
  createdAt: string
  inviteLink?: string
}

export interface UserRequest {
  name: string
  email: string
  role: Role
  homeBranchId?: number | null
  branchIds?: number[]
  active?: boolean
}

export const usersApi = {
  list() {
    return api.get<TeamUser[]>('/api/v1/users')
  },
  create(data: UserRequest) {
    return api.post<TeamUser>('/api/v1/users', data)
  },
  update(id: number, data: UserRequest) {
    return api.patch<TeamUser>(`/api/v1/users/${id}`, data)
  },
}

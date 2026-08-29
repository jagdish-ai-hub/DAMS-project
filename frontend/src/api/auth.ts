import api from './axios'
import type { Role } from '../auth/AuthContext'

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  role: Role
  orgId: number | null
  homeBranchId: number | null
  name: string
}

export interface AcceptInviteRequest {
  token: string
  password: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export const authApi = {
  login(data: LoginRequest) {
    return api.post<LoginResponse>('/api/v1/auth/login', data)
  },

  acceptInvite(data: AcceptInviteRequest) {
    return api.post<LoginResponse>('/api/v1/auth/accept-invite', data)
  },

  changePassword(data: ChangePasswordRequest) {
    return api.post<void>('/api/v1/auth/change-password', data)
  },
}

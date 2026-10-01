import api from './axios'
import type { Role } from '../auth/AuthContext'

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  /** The ACTING role. */
  role: Role
  orgId: number | null
  homeBranchId: number | null
  name: string
  /** The user's own role (plan.md rev 55). */
  primaryRole: Role
  /** The one branch a switched session is scoped to; null when in their own role. */
  actingBranchId: number | null
  /** Show the Switch role button. */
  canSwitchRole: boolean
}

export interface AcceptInviteRequest {
  token: string
  password: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

/** One branch in the Switch role picker and the roles the caller may take there. */
export interface SwitchBranchOption {
  branchId: number
  code: string
  name: string
  roles: Role[]
}

export interface SwitchOptions {
  primaryRole: Role
  actingRole: Role
  actingBranchId: number | null
  branches: SwitchBranchOption[]
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

  switchOptions() {
    return api.get<SwitchOptions>('/api/v1/auth/switch-options')
  },

  /** Naming the user's own role switches back (branchId then ignored). */
  switchRole(role: Role, branchId?: number | null) {
    return api.post<LoginResponse>('/api/v1/auth/switch-role', { role, branchId: branchId ?? null })
  },
}

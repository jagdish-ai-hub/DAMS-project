import api from './axios'
import type { Role } from '../auth/AuthContext'

/**
 * An extra role a user may switch into (plan.md rev 55). FINANCE_MANAGER is org-wide, so its
 * `branchIds` is empty; ACCOUNTANT / CASHIER list every branch the role is granted at.
 */
export interface RoleGrant {
  role: Role
  branchIds: number[]
}

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
  /** Extra switchable roles; absent when none. */
  roleGrants?: RoleGrant[]
  /** e.g. "Cashier (OOR), Finance Manager"; absent when none. */
  extraRolesLabel?: string
  createdAt: string
  inviteLink?: string
}

export interface UserRequest {
  name: string
  email: string
  role: Role
  homeBranchId?: number | null
  branchIds?: number[]
  /** Replaces the user's extra roles wholesale; ignored for an Owner. */
  roleGrants?: RoleGrant[]
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

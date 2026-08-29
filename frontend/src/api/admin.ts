import api from './axios'

export interface Organization {
  id: number
  name: string
  multiBranchCashierAccess: boolean
  active: boolean
  createdAt: string
}

export interface CreateOrgRequest {
  orgName: string
  ownerName: string
  ownerEmail: string
}

export interface OrgCreationResult {
  orgId: number
  orgName: string
  inviteToken: string
  inviteLink: string
}

export const adminApi = {
  listOrganizations() {
    return api.get<Organization[]>('/api/v1/admin/organizations')
  },

  createOrganization(data: CreateOrgRequest) {
    return api.post<OrgCreationResult>('/api/v1/admin/organizations', data)
  },

  getOrganization(id: number) {
    return api.get<Organization>(`/api/v1/admin/organizations/${id}`)
  },

  toggleActive(id: number, active: boolean) {
    return api.patch<Organization>(`/api/v1/admin/organizations/${id}`, { active })
  },

  deleteOrganization(id: number) {
    return api.delete<void>(`/api/v1/admin/organizations/${id}`)
  },
}

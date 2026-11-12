import api from './axios'

export interface Organization {
  id: number
  name: string
  multiBranchCashierAccess: boolean
  /** rev 91 — the Spare Parts module switch, off by default. */
  sparePartsEnabled: boolean
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

  setSparePartsEnabled(id: number, sparePartsEnabled: boolean) {
    return api.patch<Organization>(`/api/v1/admin/organizations/${id}`, { sparePartsEnabled })
  },

  deleteOrganization(id: number) {
    return api.delete<void>(`/api/v1/admin/organizations/${id}`)
  },
}

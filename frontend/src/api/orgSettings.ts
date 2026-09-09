import api from './axios'

export interface OrgSettings {
  id: number
  name: string
  multiBranchCashierAccess: boolean
  active: boolean
  createdAt: string
}

export interface OrgSettingsRequest {
  name?: string
  multiBranchCashierAccess?: boolean
}

export const orgSettingsApi = {
  get() {
    return api.get<OrgSettings>('/api/v1/organization')
  },
  update(data: OrgSettingsRequest) {
    return api.patch<OrgSettings>('/api/v1/organization', data)
  },
}

import api from './axios'

export interface ClaimAction {
  id: number
  jobCardId: number
  jobCardReference: string
  action: string
  ownerUserId: number | null
  ownerName: string | null
  dueDate: string
  overdue: boolean
  doneAt: string | null
  createdBy: number
  createdAt: string
}

export const claimActionsApi = {
  open() {
    return api.get<ClaimAction[]>('/api/v1/claim-actions')
  },
  forJobCard(jobCardId: number) {
    return api.get<ClaimAction[]>(`/api/v1/claim-actions/job-card/${jobCardId}`)
  },
  create(data: { jobCardId: number; action: string; ownerUserId?: number | null; dueDate: string }) {
    return api.post<ClaimAction>('/api/v1/claim-actions', data)
  },
  complete(id: number) {
    return api.post<ClaimAction>(`/api/v1/claim-actions/${id}/complete`)
  },
}

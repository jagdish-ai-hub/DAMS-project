import api from './axios'
import type { WorkflowStatus } from './receipts'

export interface MyEntry {
  id: number
  kind: 'RECEIPT'
  documentNo: string | null
  workflowStatus: WorkflowStatus
  settled: boolean
  jobCardId: number
  jobCardReference: string
  customerName: string | null
  totalReceived: number
  pendingAmount: number
  lineCount: number
  today: boolean
  queried: boolean
  createdAt: string
  submittedAt: string | null
}

export const myEntriesApi = {
  list() {
    return api.get<MyEntry[]>('/api/v1/my-entries')
  },
}

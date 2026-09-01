import api from './axios'

export type MyEntryKind = 'RECEIPT' | 'EXPENSE' | 'CASH'

export interface MyEntry {
  id: number
  kind: MyEntryKind
  documentNo: string | null
  workflowStatus: string
  settled: boolean
  jobCardId: number | null
  jobCardReference: string | null
  partyName: string | null        // customer (receipt) or receiver (expense)
  total: number
  pendingAmount: number | null    // null for an expense
  lineCount: number
  overLimit: boolean              // expenses only
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

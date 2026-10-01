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
  /** Expenses only (rev 53) — FM pre-approval of an over-limit expense. */
  preApprovalStatus: 'PENDING' | 'APPROVED' | 'QUERIED' | null
  preApprovalCovers: boolean
}

/** One message in a Cashier home box (rev 57). */
export interface InboxItem {
  id: number
  kind: MyEntryKind
  documentNo: string | null
  title: string
  total: number
  /** QUERIED | REJECTED | PRE_PENDING | PRE_APPROVED | PRE_QUERIED */
  state: 'QUERIED' | 'REJECTED' | 'PRE_PENDING' | 'PRE_APPROVED' | 'PRE_QUERIED'
  /** Still waiting on the cashier — counted in the badge. */
  needsAction: boolean
  fromName: string | null
  fromRole: string | null
  note: string | null
  at: string | null
}

export interface CashierInbox {
  queries: InboxItem[]
  approvals: InboxItem[]
  queriesToAct: number
  approvalsToAct: number
}

export const myEntriesApi = {
  inbox() {
    return api.get<CashierInbox>('/api/v1/my-entries/inbox')
  },
  list() {
    return api.get<MyEntry[]>('/api/v1/my-entries')
  },
}

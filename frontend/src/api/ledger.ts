import api from './axios'
import type { JobCardSummary, TimelineEntry } from './customers'

export interface CustomerStatement {
  customerId: number
  customerName: string
  customerPhone: string | null
  generatedOn: string
  generatedBy: string
  totalInvoiced: number
  totalReceived: number
  balanceDue: number
  jobCards: JobCardSummary[]
  payments: TimelineEntry[]
}

export const ledgerApi = {
  statement(customerId: number) {
    return api.get<CustomerStatement>(`/api/v1/ledger/customers/${customerId}/statement`)
  },
}

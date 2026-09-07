import api from './axios'

export type MatchField = 'Name' | 'Phone' | 'Vehicle' | 'Job Card' | 'Invoice' | 'Receipt' | 'Expense'

export interface SearchHit {
  customerId: number
  customerName: string
  phone: string | null
  vehicles: string[]
  jobCardCount: number
  totalInvoiced: number
  totalOutstanding: number
  matchField: MatchField
  branchCodes: string[]
}

export interface SearchResult {
  query: string
  hits: SearchHit[]
}

export const searchApi = {
  query(q: string) {
    return api.get<SearchResult>('/api/v1/search', { params: { q } })
  },
}

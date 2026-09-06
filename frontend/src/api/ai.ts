import api from './axios'

export interface AiAnswer {
  answer: string
  citedDocs: string[]
  requestId: string | null
}

export interface AiBrief {
  period: string
  scope: string
  collections: number
  expenses: number
  net: number
  cashInHand: number
  pendingReview: number
  bullets: string[]
}

export interface BenchmarkNarrative {
  lines: string[]
  headline: string
}

export interface AnomalyItem {
  kind: string
  severity: 'info' | 'watch' | 'urgent'
  message: string
  branchCode: string | null
  documentNo: string | null
}

export interface RiskScore {
  type: string
  id: number
  documentNo: string | null
  branchCode: string | null
  score: number
  reasons: string[]
}

export interface QueryRoot {
  cause: string
  count: number
  suggestion: string
}

export interface ClaimInsight {
  documentNo: string
  branchCode: string
  customerName: string
  amount: number
  ageDays: number
  bucket: string
  draftFollowUp: string
}

export interface CashAdvice {
  branchCode: string
  advice: string
  severity: 'info' | 'watch' | 'urgent'
}

export interface CloseChecklistRow {
  branchCode: string
  cashDaysClosed: boolean
  cashNote: string
  pendingReview: number
  openClaims: number
  readyToClose: boolean
}

export interface ReceiverDuplicate {
  firstId: number
  firstName: string
  secondId: number
  secondName: string
  reason: string
}

export interface MastersHealthItem {
  list: string
  name: string
  issue: string
  suggestion: string
}

export interface LimitAdvice {
  subCategory: string
  limitAmount: number | null
  breaches: number
  suggestion: string
}

export interface SmartHit {
  customerId: number
  customerName: string
  phone: string | null
  totalOutstanding: number
  matchField: string
  whyRank: string
}

export interface SmartSearchResponse {
  query: string
  normalized: string
  intent: string
  hits: SmartHit[]
}

/**
 * Owner/Admin AI assistant client (FEAT-09..FEAT-21). Same conventions as the
 * other api modules — typed wrappers over the axios instance, JWT attached
 * automatically, X-Request-ID surfaced as _requestId on errors.
 */
export const aiApi = {
  ask(question: string, branchId?: number) {
    return api.post<AiAnswer>('/api/v1/ai/ask', { question, branchId: branchId ?? null })
  },
  brief(period: 'today' | 'mtd' = 'mtd', branchId?: number) {
    return api.get<AiBrief>('/api/v1/ai/brief', { params: { period, branchId } })
  },
  benchmark() {
    return api.get<BenchmarkNarrative>('/api/v1/ai/benchmark')
  },
  anomalies(branchId?: number) {
    return api.get<AnomalyItem[]>('/api/v1/ai/anomalies', { params: { branchId } })
  },
  risk(queue: 'receipt' | 'expense' = 'receipt', branchId?: number) {
    return api.get<RiskScore[]>('/api/v1/ai/risk', { params: { queue, branchId } })
  },
  queryRoots(branchId?: number) {
    return api.get<QueryRoot[]>('/api/v1/ai/queries/roots', { params: { branchId } })
  },
  claimInsights(branchId?: number) {
    return api.get<ClaimInsight[]>('/api/v1/ai/claims/insights', { params: { branchId } })
  },
  cashAdvice(branchId?: number) {
    return api.get<CashAdvice[]>('/api/v1/ai/cash/advice', { params: { branchId } })
  },
  closeChecklist(branchId?: number) {
    return api.get<CloseChecklistRow[]>('/api/v1/ai/close/checklist', { params: { branchId } })
  },
  receiverDuplicates() {
    return api.get<ReceiverDuplicate[]>('/api/v1/ai/receivers/duplicates')
  },
  mastersHealth() {
    return api.get<MastersHealthItem[]>('/api/v1/ai/masters/health')
  },
  limitAdvice() {
    return api.get<LimitAdvice[]>('/api/v1/ai/limits/advice')
  },
  smartSearch(q: string) {
    return api.get<SmartSearchResponse>('/api/v1/ai/search', { params: { q } })
  },
}

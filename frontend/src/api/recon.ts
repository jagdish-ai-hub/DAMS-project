import api from './axios'

export interface ReconBatch {
  id: number
  filename: string
  lineCount: number
  resolvedCount: number
  uploadedBy: number
  uploadedAt: string
}

export interface ReconLine {
  id: number
  txnDate: string
  utr: string | null
  amount: number
  narration: string | null
  matchedSettlementLineId: number | null
  matchedDocumentNo: string | null
  matchKind: string | null
  ignored: boolean
  resolved: boolean
}

export const reconApi = {
  batches() {
    return api.get<ReconBatch[]>('/api/v1/recon/batches')
  },
  lines(batchId: number) {
    return api.get<ReconLine[]>(`/api/v1/recon/batches/${batchId}/lines`)
  },
  upload(file: File) {
    const form = new FormData()
    form.append('file', file)
    return api.post<ReconBatch>('/api/v1/recon/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  confirm(lineId: number, settlementLineId: number) {
    return api.post<ReconLine>(`/api/v1/recon/lines/${lineId}/confirm`, null, {
      params: { settlementLineId },
    })
  },
  ignore(lineId: number, ignored = true) {
    return api.post<ReconLine>(`/api/v1/recon/lines/${lineId}/ignore`, null, {
      params: { ignored },
    })
  },
}

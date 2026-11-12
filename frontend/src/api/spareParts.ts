import api from './axios'

/** Spare Parts module API (docs/modules/spare-parts.md). Shapes mirror SparePartsDtos on the server. */

export interface SpareBranch {
  id: number
  code: string
  name: string
}

/** What the signed-in user may do. `enabled=false` means the module is off (or the caller is Super Admin). */
export interface SpareAccess {
  enabled: boolean
  canView: boolean
  canSeeDashboard: boolean
  owner: boolean
  role: string | null
  actBranchIds: number[]
  permissions: SparePermissionKey[]
  branches: SpareBranch[]
}

export type SparePermissionKey =
  | 'BILL_PART'
  | 'RAISE_REQUEST'
  | 'SEND_RECEIVE'
  | 'UPLOAD_ADD'
  | 'CORRECT_QTY'
  | 'EXPORT'

export interface StockCell {
  branchId: number
  quantity: number
  bin: string | null
}

export interface StockRow {
  partId: number
  partCode: string
  description: string
  unitPrice: number | null
  cells: StockCell[]
  totalQuantity: number
  inTransit: number
  totalValue: number
}

export interface StockPage {
  rows: StockRow[]
  truncated: boolean
}

export type ImportMode = 'ADD' | 'SET'

/** A row as sent back to confirm: numbers stay text, the server re-validates everything. */
export interface ImportRowIn {
  rowNumber: number
  branchCode: string
  partCode: string
  description: string
  bin: string
  quantity: string
  unitPrice: string
  billedQty: string
  jobCard: string
}

export interface ImportRowOut extends ImportRowIn {
  currentQty: number | null
  resultQty: number | null
  newPart: boolean
  error: string | null
}

export interface ImportPreview {
  fileName: string
  mode: ImportMode
  rows: ImportRowOut[]
  total: number
  ok: number
  errors: number
  newParts: number
}

export interface ImportResult {
  importId: number
  posted: number
  rejected: number
  newParts: number
  rejectedRows: ImportRowOut[]
}

export interface ActionResult {
  partId: number
  quantityAfter: number
  movementId: number | null
}

export type MovementType =
  | 'UPLOAD_ADD'
  | 'UPLOAD_SET'
  | 'MANUAL_ADD'
  | 'CORRECTION'
  | 'BILLED'
  | 'TRANSFER_OUT'
  | 'TRANSFER_IN'

export interface MovementRow {
  id: number
  createdAt: string
  branchId: number
  branchCode: string
  partId: number
  partCode: string
  description: string
  type: MovementType
  qtyChange: number
  qtyAfter: number
  reference: string | null
  vehicleNo: string | null
  note: string | null
  userName: string
  actingRole: string | null
}

export type TransferStatus = 'REQUESTED' | 'SENT' | 'RECEIVED' | 'DECLINED' | 'CANCELLED'

export interface TransferRow {
  id: number
  requestNo: string
  status: TransferStatus
  fromBranchId: number
  fromBranchCode: string
  toBranchId: number
  toBranchCode: string
  partId: number
  partCode: string
  description: string
  qtyRequested: number
  qtySent: number | null
  jobCardNo: string | null
  note: string | null
  declineReason: string | null
  requestedByName: string | null
  requestedAt: string
  sentByName: string | null
  sentAt: string | null
  receivedByName: string | null
  receivedAt: string | null
  canSend: boolean
  canReceive: boolean
  canDecline: boolean
  canCancel: boolean
}

export interface PermissionGrid {
  permissions: { key: SparePermissionKey; label: string }[]
  roles: { role: 'INVENTORY_MANAGER' | 'STORE_KEEPER'; label: string; values: Record<string, boolean> }[]
}

export interface BranchTotal {
  branchId: number
  code: string
  name: string
  partsHeld: number
  units: number
  value: number
  billsThisMonth: number
  unitsBilledThisMonth: number
}

export interface SpareDashboard {
  branches: BranchTotal[]
  partCount: number
  totalUnits: number
  totalValue: number
  requestCounts: Record<TransferStatus, number>
  recent: MovementRow[]
}

const BASE = '/api/v1/spare-parts'

export const sparePartsApi = {
  access() {
    return api.get<SpareAccess>(`${BASE}/access`)
  },

  stock(params: { q?: string; branchId?: number | null; inStockOnly?: boolean }) {
    return api.get<StockPage>(`${BASE}/stock`, {
      params: { q: params.q || undefined, branchId: params.branchId ?? undefined, inStockOnly: params.inStockOnly || undefined },
    })
  },

  movements(params: { branchId?: number | null; type?: MovementType | ''; q?: string; reference?: string; limit?: number }) {
    return api.get<MovementRow[]>(`${BASE}/movements`, {
      params: {
        branchId: params.branchId ?? undefined,
        type: params.type || undefined,
        q: params.q || undefined,
        reference: params.reference || undefined,
        limit: params.limit,
      },
    })
  },

  previewImport(file: File, mode: ImportMode) {
    const form = new FormData()
    form.append('file', file)
    form.append('mode', mode)
    return api.post<ImportPreview>(`${BASE}/imports/preview`, form)
  },

  confirmImport(fileName: string, mode: ImportMode, rows: ImportRowIn[]) {
    return api.post<ImportResult>(`${BASE}/imports`, { fileName, mode, rows })
  },

  addPart(body: { branchId: number; partCode: string; description: string; bin?: string; unitPrice?: number | null; quantity: number }) {
    return api.post<ActionResult>(`${BASE}/parts`, body)
  },

  correct(body: { branchId: number; partId: number; newQuantity: number; reason: string }) {
    return api.post<ActionResult>(`${BASE}/stock/correct`, body)
  },

  bill(body: { branchId: number; partId: number; quantity: number; jobCardNo: string; vehicleNo?: string; note?: string }) {
    return api.post<ActionResult>(`${BASE}/bills`, body)
  },

  transfers(params: { status?: TransferStatus | ''; branchId?: number | null } = {}) {
    return api.get<TransferRow[]>(`${BASE}/transfers`, {
      params: { status: params.status || undefined, branchId: params.branchId ?? undefined },
    })
  },

  createTransfer(body: { fromBranchId: number; toBranchId: number; partId: number; quantity: number; jobCardNo?: string; note?: string }) {
    return api.post<TransferRow>(`${BASE}/transfers`, body)
  },

  sendTransfer(id: number, quantity?: number) {
    return api.post<TransferRow>(`${BASE}/transfers/${id}/send`, quantity ? { quantity } : {})
  },

  receiveTransfer(id: number) {
    return api.post<TransferRow>(`${BASE}/transfers/${id}/receive`)
  },

  declineTransfer(id: number, reason: string) {
    return api.post<TransferRow>(`${BASE}/transfers/${id}/decline`, { reason })
  },

  cancelTransfer(id: number, reason: string) {
    return api.post<TransferRow>(`${BASE}/transfers/${id}/cancel`, { reason })
  },

  dashboard() {
    return api.get<SpareDashboard>(`${BASE}/dashboard`)
  },

  permissions() {
    return api.get<PermissionGrid>(`${BASE}/permissions`)
  },

  updatePermissions(grid: Record<string, Record<string, boolean>>) {
    return api.put<PermissionGrid>(`${BASE}/permissions`, { grid })
  },

  /** Downloads the stock of every branch as an Excel-friendly CSV in the owner's sheet layout. */
  async downloadStock() {
    let response
    try {
      response = await api.get(`${BASE}/export/stock`, { responseType: 'blob' })
    } catch (e) {
      // A blob response hides the server's JSON error: unwrap it so the caller can show it.
      const data = (e as { response?: { data?: unknown } })?.response?.data
      if (data instanceof Blob) {
        try {
          const parsed = JSON.parse(await data.text()) as { message?: string }
          if (parsed.message) throw new Error(parsed.message)
        } catch (inner) {
          if (inner instanceof Error && inner.message && !(inner instanceof SyntaxError)) throw inner
        }
      }
      throw e
    }
    const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8;' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', 'spare-parts-stock.csv')
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  },
}

/** The server's own message for a failed call, or the fallback. */
export function apiMessage(err: unknown, fallback: string): string {
  if (err instanceof Error && !(err as { response?: unknown }).response && err.message && err.name !== 'AxiosError') {
    return err.message
  }
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

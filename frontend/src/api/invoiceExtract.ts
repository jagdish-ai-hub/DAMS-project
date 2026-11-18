import api from './axios'

/** One spares / lube / labour line read from an Eicher dealer PDF (rev 98 test tool). Unused fields are absent. */
export interface ExtractItem {
  page?: number
  sno?: string
  code?: string
  hsnSac?: string
  gstRate?: number
  description?: string
  qty?: number
  uom?: string
  manHr?: number
  ratePerHr?: number
  /** Net unit price (tax invoice), gross (tax-invoice labour) or rate (warranty claim). */
  unitAmount?: number
  discount?: number
  handling?: number
  taxable?: number
  cgst?: number
  sgst?: number
  igst?: number
  kfc?: number
  tds?: number
  total?: number
}

export interface InvoiceExtract {
  fileName: string
  documentType: 'TAX_INVOICE' | 'WARRANTY_CLAIM'
  documentLabel: string
  header: { label: string; value: string }[]
  spares: ExtractItem[]
  lube: ExtractItem[]
  labour: ExtractItem[]
  freeItems: { sno?: number; itemCode?: string; description?: string; quantity?: number }[]
  totals: { label: string; value: number }[]
  hsnSummary: { hsn: string; taxable?: number; total?: number }[]
  /** Every figure recomputed and compared with what the document prints itself. */
  checks: { name: string; ok: boolean; detail?: string }[]
  problems: string[]
  checksPassed: number
  checksTotal: number
}

export const invoiceExtractApi = {
  /** Read a PDF and return what was found. Saves nothing on the server. */
  extract(file: File) {
    const form = new FormData()
    form.append('file', file)
    return api.post<InvoiceExtract>('/api/v1/invoice-extract', form, { headers: { 'Content-Type': 'multipart/form-data' } })
  },
}

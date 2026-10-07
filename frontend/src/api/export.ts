import api from './axios'

export const exportApi = {
  downloadReceipts: async (branchId?: number | '', from?: string, to?: string) => {
    const params = new URLSearchParams()
    if (branchId) params.append('branchId', String(branchId))
    if (from) params.append('from', from)
    if (to) params.append('to', to)
    const response = await api.get(`/api/v1/export/receipts?${params.toString()}`, {
      responseType: 'blob',
    })
    const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8;' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `receipts-export-${from || 'recent'}-to-${to || 'today'}.csv`)
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  },

  /** Exactly the given receipts — the Accountant's Direct Approve list export. */
  downloadReceiptsByIds: async (ids: number[]) => {
    const response = await api.get(`/api/v1/export/receipts/by-id?ids=${ids.join(',')}`, {
      responseType: 'blob',
    })
    const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8;' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `receipts-export-selected-${ids.length}.csv`)
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  },

  /**
   * The Accountant's "Pending & closed" window, exactly as filtered (rev 67): `ids` in on-screen
   * order; the CSV has one row per settlement / expense line. POST so a long list can't overflow
   * the URL.
   */
  downloadReviewList: async (type: 'receipt' | 'expense' | 'cash', ids: number[], filename: string) => {
    let response
    try {
      response = await api.post('/api/v1/export/review-list', { type, ids }, { responseType: 'blob' })
    } catch (e) {
      // A blob response hides the server's JSON error message — unwrap it so the caller can show it.
      const data = (e as { response?: { data?: unknown } })?.response?.data
      if (data instanceof Blob) {
        try {
          (e as { response: { data: unknown } }).response.data = JSON.parse(await data.text())
        } catch { /* not JSON — leave as is */ }
      }
      throw e
    }
    const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8;' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', filename)
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  },

  downloadExpenses: async (branchId?: number | '', from?: string, to?: string) => {
    const params = new URLSearchParams()
    if (branchId) params.append('branchId', String(branchId))
    if (from) params.append('from', from)
    if (to) params.append('to', to)
    const response = await api.get(`/api/v1/export/expenses?${params.toString()}`, {
      responseType: 'blob',
    })
    const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8;' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `expenses-export-${from || 'recent'}-to-${to || 'today'}.csv`)
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  },
}

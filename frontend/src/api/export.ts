import api from './axios'

export const exportApi = {
  downloadReceipts: async (branchId?: number | '', from?: string, to?: string) => {
    const params = new URLSearchParams()
    if (branchId) params.append('branchId', String(branchId))
    if (from) params.append('from', from)
    if (to) params.append('to', to)
    const response = await api.get(`/export/receipts?${params.toString()}`, {
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

  downloadExpenses: async (branchId?: number | '', from?: string, to?: string) => {
    const params = new URLSearchParams()
    if (branchId) params.append('branchId', String(branchId))
    if (from) params.append('from', from)
    if (to) params.append('to', to)
    const response = await api.get(`/export/expenses?${params.toString()}`, {
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

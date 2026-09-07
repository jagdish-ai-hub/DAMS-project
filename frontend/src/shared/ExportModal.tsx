import { useState, useEffect } from 'react'
import { branchesApi, type Branch } from '../api/branches'
import { exportApi } from '../api/export'
import { Modal, Field, ErrorBanner, inputStyle, primaryBtn, ghostBtn, istToday, Spinner } from '../shell/ui'
import { Download, FileSpreadsheet, CheckCircle2 } from 'lucide-react'

interface Props {
  defaultType?: 'receipts' | 'expenses'
  onClose: () => void
}

export default function ExportModal({ defaultType = 'receipts', onClose }: Props) {
  const thirtyDaysAgo = () => {
    const d = new Date(istToday())
    d.setDate(d.getDate() - 30)
    return d.toISOString().slice(0, 10)
  }

  const [type, setType] = useState<'receipts' | 'expenses'>(defaultType)
  const [branches, setBranches] = useState<Branch[]>([])
  const [branchId, setBranchId] = useState<number | ''>('')
  const [from, setFrom] = useState(thirtyDaysAgo())
  const [to, setTo] = useState(istToday())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [done, setDone] = useState(false)

  useEffect(() => {
    branchesApi.list()
      .then(({ data }) => {
        setBranches(data)
      })
      .catch(() => {})
  }, [])

  const handleExport = async () => {
    setBusy(true)
    setError('')
    setDone(false)
    try {
      if (type === 'receipts') {
        await exportApi.downloadReceipts(branchId, from, to)
      } else {
        await exportApi.downloadExpenses(branchId, from, to)
      }
      setDone(true)
      setTimeout(() => setDone(false), 4000)
    } catch (e: any) {
      setError(e?.response?.data?.message || 'Could not export ledger CSV. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      title="Export to Tally / Excel"
      subtitle="Download verified transactions in accounting-ready RFC 4180 CSV"
      onClose={onClose}
      footer={
        <>
          <button type="button" onClick={onClose} style={{ ...ghostBtn, minHeight: 38 }} disabled={busy}>
            Close
          </button>
          <button
            type="button"
            onClick={handleExport}
            style={{ ...primaryBtn(busy), minHeight: 38, display: 'inline-flex', alignItems: 'center', gap: 6 }}
            disabled={busy}
          >
            {busy ? <><Spinner /> Exporting…</> : <><Download className="w-4 h-4" /> Download CSV</>}
          </button>
        </>
      }
    >
      <ErrorBanner message={error} />
      {done && (
        <div className="flex items-center gap-2 p-3 bg-green-50 dark:bg-green-950/40 text-green-700 dark:text-green-300 border border-green-200 dark:border-green-800 rounded-lg text-xs font-medium mb-3">
          <CheckCircle2 className="w-4 h-4 text-green-600 flex-shrink-0" />
          <span>Export file downloaded successfully! Open in Microsoft Excel or import into Tally.</span>
        </div>
      )}

      {/* Type Toggle */}
      <Field label="Ledger Type">
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => setType('receipts')}
            className={`flex-1 py-2 px-3 text-xs font-bold rounded-lg border transition-all flex items-center justify-center gap-2 ${
              type === 'receipts'
                ? 'bg-[var(--purple,#6B3FA0)] text-white border-transparent shadow-sm'
                : 'bg-[var(--surface)] text-[var(--muted)] border-[var(--line)] hover:text-[var(--text)]'
            }`}
          >
            <FileSpreadsheet className="w-4 h-4" />
            <span>Receipts Ledger</span>
          </button>
          <button
            type="button"
            onClick={() => setType('expenses')}
            className={`flex-1 py-2 px-3 text-xs font-bold rounded-lg border transition-all flex items-center justify-center gap-2 ${
              type === 'expenses'
                ? 'bg-[var(--purple,#6B3FA0)] text-white border-transparent shadow-sm'
                : 'bg-[var(--surface)] text-[var(--muted)] border-[var(--line)] hover:text-[var(--text)]'
            }`}
          >
            <FileSpreadsheet className="w-4 h-4" />
            <span>Expenses Ledger</span>
          </button>
        </div>
      </Field>

      {/* Branch Selection */}
      <Field label="Branch Scope">
        <select
          value={branchId}
          onChange={(e) => setBranchId(e.target.value === '' ? '' : Number(e.target.value))}
          style={inputStyle}
        >
          <option value="">All Accessible Branches</option>
          {branches.map((b) => (
            <option key={b.id} value={b.id}>
              {b.name} ({b.code})
            </option>
          ))}
        </select>
      </Field>

      {/* Date Range */}
      <div className="grid grid-cols-2 gap-3">
        <Field label="From Date">
          <input
            type="date"
            value={from}
            onChange={(e) => setFrom(e.target.value)}
            style={inputStyle}
          />
        </Field>
        <Field label="To Date">
          <input
            type="date"
            value={to}
            onChange={(e) => setTo(e.target.value)}
            style={inputStyle}
          />
        </Field>
      </div>

      <div className="text-[11px] text-[var(--faint)] mt-2 leading-relaxed bg-[var(--bg)] p-3 rounded-lg border border-[var(--line)]">
        💡 <strong>Tally Note:</strong> CSV exports contain UTF-8 Byte Order Mark (BOM) headers, preserving customer names, Indian Rupee currency formats, and vehicle registration numbers without character corruption.
      </div>
    </Modal>
  )
}

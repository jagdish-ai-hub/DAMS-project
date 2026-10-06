import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { reviewApi, type ReviewQueueItem } from '../api/review'
import { expensesApi, type ExpenseDocument } from '../api/expenses'
import { branchesApi, type Branch } from '../api/branches'
import { mastersApi, type MasterRow } from '../api/masters'
import { RecordCard, EXPENSE_STATUS_EDITABLE, apiError, wfTone } from '../review/reviewShared'
import { card, Badge, ErrorBanner, ghostBtn, inputStyle, Skeleton, inr, fmtDate } from '../shell/ui'

/**
 * The Owner's Expenses page (AGENT.md, rev 60) — every non-draft expense in every branch, all
 * workflow states, for oversight. The one thing the Owner can do here is change an expense's
 * business status (the same states the Accountant and FM can); verify / approve / close and line
 * overrides stay with them, so this page offers none of those.
 */

const WORKFLOW_STATES = ['SUBMITTED', 'QUERIED', 'FM_QUERIED', 'VERIFIED', 'APPROVED', 'CLOSED', 'REJECTED'] as const

export default function OwnerExpensesPage() {
  const [items, setItems] = useState<ReviewQueueItem[] | null>(null)
  const [branches, setBranches] = useState<Branch[]>([])
  const [error, setError] = useState('')
  const [flash, setFlash] = useState('')

  const [search, setSearch] = useState('')
  const [branchId, setBranchId] = useState<number | ''>('')
  const [workflow, setWorkflow] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')

  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [doc, setDoc] = useState<ExpenseDocument | null>(null)
  const [statusOptions, setStatusOptions] = useState<MasterRow[]>([])

  useEffect(() => {
    reviewApi.ownerExpenses()
      .then(({ data }) => setItems(data))
      .catch((e) => setError(apiError(e, 'Could not load the expenses.')))
    branchesApi.list().then(({ data }) => setBranches(data)).catch(() => {})
  }, [])

  useEffect(() => {
    if (selectedId == null) { setDoc(null); return }
    let live = true
    setDoc(null)
    expensesApi.get(selectedId)
      .then(({ data }) => { if (live) setDoc(data) })
      .catch((e) => { if (live) setError(apiError(e, 'Could not load that expense.')) })
    return () => { live = false }
  }, [selectedId])

  const editable = doc != null && EXPENSE_STATUS_EDITABLE.has(doc.workflowStatus)
  const currentStatusId = doc?.businessStatusId
  useEffect(() => {
    setStatusOptions([])
    if (!editable) return
    let live = true
    mastersApi.list('expense-statuses')
      .then(({ data }) => live && setStatusOptions(data.filter((s) => s.active || s.id === currentStatusId)))
      .catch((e) => live && setError(apiError(e, 'Could not load the status list.')))
    return () => { live = false }
  }, [editable, currentStatusId])

  const visible = useMemo(() => {
    const q = search.trim().toLowerCase()
    return (items ?? []).filter((it) => {
      if (branchId !== '' && it.branchId !== branchId) return false
      if (workflow && it.workflowStatus !== workflow) return false
      const day = it.submittedAt ? it.submittedAt.slice(0, 10) : ''
      if (from && day < from) return false
      if (to && day > to) return false
      if (q && !`${it.documentNo ?? ''} ${it.partyName} ${it.categoryName}`.toLowerCase().includes(q)) return false
      return true
    })
  }, [items, search, branchId, workflow, from, to])

  const total = useMemo(() => visible.reduce((sum, it) => sum + it.amount, 0), [visible])

  async function changeStatus(statusId: number) {
    if (!doc) return
    setError('')
    setFlash('')
    try {
      const { data } = await reviewApi.changeExpenseStatus(doc.id, statusId)
      setDoc(data)
      setFlash(`${data.documentNo ?? 'Expense'} — status updated`)
    } catch (e) {
      setError(apiError(e, 'The status could not be changed.'))
    }
  }

  if (selectedId != null) {
    return (
      <div style={{ maxWidth: 900, margin: '0 auto' }}>
        <button type="button" onClick={() => { setSelectedId(null); setFlash(''); setError('') }}
          style={{ ...ghostBtn, minHeight: 36, marginBottom: 12, fontWeight: 700 }}>
          ← Back to Expenses
        </button>
        <ErrorBanner message={error} />
        {flash && (
          <div style={{ background: 'var(--green-bg)', color: 'var(--green)', borderRadius: 8, padding: '8px 12px', fontSize: '0.82rem', marginBottom: 12 }}>
            {flash}
          </div>
        )}
        {doc == null && !error && <Skeleton height={220} radius={10} />}
        {doc && (
          <>
            <RecordCard
              doc={doc}
              canOverride={false}
              busy={false}
              onError={setError}
              onOverride={async () => {}}
              statusOptions={editable && statusOptions.length ? statusOptions : undefined}
              onStatusChange={editable ? changeStatus : undefined}
            />
            <div style={{ fontSize: '0.78rem', color: 'var(--muted)' }}>
              Oversight view — verifying, approving and closing are done by the Accountant and Finance Manager.
              {editable
                ? ' You can change the business status above.'
                : ' The status can only be changed while the expense is submitted, verified, approved or Finance-queried.'}
            </div>
          </>
        )}
      </div>
    )
  }

  return (
    <div style={{ maxWidth: 1000, margin: '0 auto' }}>
      <h1 style={{ fontSize: '1.3rem', color: 'var(--navy)', marginBottom: 4 }}>Expenses</h1>
      <div style={{ fontSize: '0.85rem', color: 'var(--muted)', marginBottom: 16 }}>
        Every submitted expense across all branches, newest first. Open one to see its lines and history or to change its status.
      </div>

      <div style={{ ...card, display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'flex-end', marginBottom: 16 }}>
        <div style={{ flex: '2 1 200px', minWidth: 160 }}>
          <Field label="Search">
            <input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Doc #, receiver or category"
              style={{ ...inputStyle, width: '100%', minHeight: 38 }} />
          </Field>
        </div>
        <div style={{ flex: '1 1 150px', minWidth: 140 }}>
          <Field label="Branch">
            <select value={branchId} onChange={(e) => setBranchId(e.target.value === '' ? '' : Number(e.target.value))}
              style={{ ...inputStyle, width: '100%', minHeight: 38 }}>
              <option value="">All branches</option>
              {branches.map((b) => <option key={b.id} value={b.id}>{b.code} — {b.name}</option>)}
            </select>
          </Field>
        </div>
        <div style={{ flex: '1 1 150px', minWidth: 140 }}>
          <Field label="State">
            <select value={workflow} onChange={(e) => setWorkflow(e.target.value)} style={{ ...inputStyle, width: '100%', minHeight: 38 }}>
              <option value="">All states</option>
              {WORKFLOW_STATES.map((s) => <option key={s} value={s}>{s}</option>)}
            </select>
          </Field>
        </div>
        <div style={{ flex: '1 1 140px', minWidth: 130 }}>
          <Field label="From">
            <input type="date" value={from} max={to || undefined} onChange={(e) => setFrom(e.target.value)} style={{ ...inputStyle, width: '100%', minHeight: 38 }} />
          </Field>
        </div>
        <div style={{ flex: '1 1 140px', minWidth: 130 }}>
          <Field label="To">
            <input type="date" value={to} min={from || undefined} onChange={(e) => setTo(e.target.value)} style={{ ...inputStyle, width: '100%', minHeight: 38 }} />
          </Field>
        </div>
      </div>

      <ErrorBanner message={error} />

      <div style={{ fontSize: '0.8rem', color: 'var(--muted)', marginBottom: 8 }}>
        {items == null ? 'Loading…' : `${visible.length} expense${visible.length === 1 ? '' : 's'} · ${inr(total)}`}
      </div>

      <div style={{ ...card, padding: 0, overflowX: 'auto', WebkitOverflowScrolling: 'touch' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 720 }}>
          <thead>
            <tr>
              {['Doc #', 'Branch', 'Receiver', 'Category', 'Amount', 'Submitted', 'State'].map((h) => (
                <th key={h} style={{
                  fontSize: '0.66rem', textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--faint)',
                  textAlign: 'left', padding: '9px 12px', borderBottom: '1.5px solid var(--line)', background: 'var(--bg)',
                }}>
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {items == null && !error && Array.from({ length: 6 }).map((_, i) => (
              <tr key={i}><td colSpan={7} style={{ padding: '10px 12px' }}><Skeleton height={22} /></td></tr>
            ))}
            {items != null && visible.length === 0 && (
              <tr><td colSpan={7} style={{ padding: 18, color: 'var(--faint)', fontSize: '0.84rem' }}>No expenses match.</td></tr>
            )}
            {visible.map((it) => (
              <tr key={it.id} onClick={() => setSelectedId(it.id)} style={{ cursor: 'pointer' }}>
                <td style={{ ...cell, fontFamily: 'Consolas, monospace', fontSize: '0.78rem', fontWeight: 700, color: 'var(--navy2)' }}>{it.documentNo ?? '—'}</td>
                <td style={cell}>{it.branchCode}</td>
                <td style={cell}>{it.partyName}</td>
                <td style={cell}>{it.categoryName}</td>
                <td style={{ ...cell, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>
                  {inr(it.amount)}{it.overLimit && <span style={{ color: 'var(--amber)', marginLeft: 6, fontSize: '0.7rem' }}>over limit</span>}
                </td>
                <td style={cell}>{it.submittedAt ? fmtDate(it.submittedAt) : '—'}</td>
                <td style={cell}><Badge tone={wfTone(it.workflowStatus)}>{it.workflowStatus}</Badge></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

const cell = { padding: '10px 12px', borderTop: '1px solid var(--line)', fontSize: '0.83rem', verticalAlign: 'top' as const }

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: '0.72rem', fontWeight: 600, color: 'var(--muted)' }}>
      {label}
      {children}
    </label>
  )
}

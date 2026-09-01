import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { mastersApi, type MasterRow } from '../api/masters'
import { customersApi } from '../api/customers'
import {
  expensesApi,
  type CreateExpenseRequest,
  type DocumentHistoryEntry,
  type ExpenseDocument,
} from '../api/expenses'
import { card, ErrorBanner, inr, primaryBtn, ghostBtn, inputStyle, Spinner, istToday } from '../shell/ui'
import AttachmentsPanel, { type LineTarget } from './AttachmentsPanel'

/** The most recent accountant question / rejection reason, for the fix-and-resubmit banner. */
function queryNote(history: DocumentHistoryEntry[]): string | null {
  for (let i = history.length - 1; i >= 0; i--) {
    const h = history[i]
    if ((h.action === 'Queried' || h.action === 'Rejected') && h.note) return h.note
  }
  return null
}

/**
 * Expense Entry (intial ui prototypes/cashier-home.html). One continuous flow: the header
 * names the receiver (created inline, deduped by name) and optionally tags a job card; the
 * lines are the payments made. Save Draft keeps it editable with no number; Submit assigns
 * the gap-free DAMS-Expenses-ID.
 *
 * "Date of Inception" from the mockup is intentionally dropped — each line carries its own
 * transaction_date and created_at covers "when entered" (matches the receive side).
 *
 * ?customerId= pre-fills the receiver page context and offers that customer's job cards;
 * ?jobCardId= pre-selects one; ?editDoc= loads a DRAFT/QUERIED document for fix-and-resubmit.
 */

type LineRow = {
  transactionDate: string
  subCategoryId: number | ''
  amount: string
  expenseModeId: number | ''
  bankId: number | ''
  transactionRef: string
  remark: string
}

const freshLine = (): LineRow => ({
  transactionDate: istToday(), subCategoryId: '', amount: '', expenseModeId: '', bankId: '', transactionRef: '', remark: '',
})

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

type JobCardOpt = { id: number; reference: string; categoryName: string | null }

export default function NewExpensePage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const editDocId = params.get('editDoc') ? Number(params.get('editDoc')) : null
  const prefillCustomerId = params.get('customerId') ? Number(params.get('customerId')) : null
  const prefillJobCardId = params.get('jobCardId') ? Number(params.get('jobCardId')) : null

  const [categories, setCategories] = useState<MasterRow[]>([])
  const [subCats, setSubCats] = useState<MasterRow[]>([])
  const [modes, setModes] = useState<MasterRow[]>([])
  const [banks, setBanks] = useState<MasterRow[]>([])
  const [statuses, setStatuses] = useState<MasterRow[]>([])
  const [jobCardOpts, setJobCardOpts] = useState<JobCardOpt[]>([])

  // header
  const [receiverName, setReceiverName] = useState('')
  const [jobCardId, setJobCardId] = useState<number | ''>(prefillJobCardId ?? '')
  const [expenseCategoryId, setExpenseCategoryId] = useState<number | ''>('')
  const [businessStatusId, setBusinessStatusId] = useState<number | ''>('')

  const [lines, setLines] = useState<LineRow[]>([freshLine()])
  const [loadedDoc, setLoadedDoc] = useState<ExpenseDocument | null>(null)

  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  // masters
  useEffect(() => {
    let live = true
    Promise.all([
      mastersApi.list('expense-categories'),
      mastersApi.list('expense-modes'),
      mastersApi.list('expense-statuses'),
      mastersApi.list('banks'),
    ])
      .then(([c, m, s, bk]) => {
        if (!live) return
        setCategories(c.data.filter((x) => x.active))
        setModes(m.data.filter((x) => x.active))
        setStatuses(s.data.filter((x) => x.active))
        setBanks(bk.data.filter((x) => x.active))
        if (!editDocId) {
          setExpenseCategoryId(c.data.find((x) => x.active)?.id ?? '')
          setBusinessStatusId(s.data.find((x) => x.active)?.id ?? '')
        }
      })
      .catch((e) => live && setError(apiError(e, 'Could not load the form options.')))
    return () => {
      live = false
    }
  }, [editDocId])

  // sub-categories cascade from the chosen category
  useEffect(() => {
    if (expenseCategoryId === '') {
      setSubCats([])
      return
    }
    let live = true
    mastersApi.list('expense-sub-categories', Number(expenseCategoryId))
      .then(({ data }) => {
        if (!live) return
        const active = data.filter((x) => x.active)
        setSubCats(active)
        const first = active[0]?.id ?? ''
        // keep a line's sub-category only if it's still valid under this category
        setLines((prev) => prev.map((l) => (active.some((s) => s.id === l.subCategoryId) ? l : { ...l, subCategoryId: first })))
      })
      .catch((e) => live && setError(apiError(e, 'Could not load sub-categories.')))
    return () => {
      live = false
    }
  }, [expenseCategoryId])

  // default the first line's payment mode once modes arrive
  useEffect(() => {
    const firstMode = modes.find((m) => m.active)?.id ?? ''
    setLines((prev) => prev.map((l) => (l.expenseModeId === '' ? { ...l, expenseModeId: firstMode } : l)))
  }, [modes])

  // customer context → offer that customer's job cards
  useEffect(() => {
    if (!prefillCustomerId) return
    customersApi.history(prefillCustomerId)
      .then(({ data }) => {
        setJobCardOpts(data.jobCards.map((j) => ({ id: j.id, reference: j.reference, categoryName: j.categoryName })))
        if (!editDocId) {
          setNotice(`New expense in ${data.customerName}'s context — pick the job card it belongs to, or leave it as branch overhead.`)
        }
      })
      .catch(() => {})
  }, [prefillCustomerId, editDocId])

  // edit / fix-and-resubmit load
  useEffect(() => {
    if (!editDocId) return
    expensesApi.get(editDocId)
      .then(({ data }) => {
        setLoadedDoc(data)
        setReceiverName(data.receiverName ?? '')
        setJobCardId(data.jobCardId ?? '')
        setExpenseCategoryId(data.expenseCategoryId)
        setBusinessStatusId(data.businessStatusId)
        if (data.jobCardId && data.jobCardReference) {
          setJobCardOpts((prev) => (prev.some((o) => o.id === data.jobCardId)
            ? prev
            : [...prev, { id: data.jobCardId!, reference: data.jobCardReference!, categoryName: null }]))
        }
        setLines(
          data.lines.length
            ? data.lines.map((l) => ({
                transactionDate: l.transactionDate,
                subCategoryId: l.subCategoryId,
                amount: String(l.amount),
                expenseModeId: l.expenseModeId,
                bankId: l.bankId ?? '',
                transactionRef: l.transactionRef ?? '',
                remark: l.remark ?? '',
              }))
            : [freshLine()],
        )
        setNotice(
          data.workflowStatus === 'QUERIED'
            ? `This entry was queried — fix it and resubmit. ${data.documentNo ?? ''}`
            : `Editing ${data.documentNo ?? 'draft'}.`,
        )
      })
      .catch((e) => setError(apiError(e, 'Could not load this document.')))
  }, [editDocId])

  const total = useMemo(() => lines.reduce((a, l) => a + (Number(l.amount) || 0), 0), [lines])

  // Documents can be tagged to the whole expense or to one saved expense line.
  const attachLineTargets: LineTarget[] = useMemo(
    () => (loadedDoc?.lines ?? []).map((l, i) => ({
      lineNo: l.lineNo,
      label: `Line ${i + 1} · ${modes.find((m) => m.id === l.expenseModeId)?.name ?? 'item'} · ${inr(l.amount)}`,
    })),
    [loadedDoc, modes],
  )
  const attachFrozen = loadedDoc?.workflowStatus === 'CLOSED' || loadedDoc?.workflowStatus === 'REJECTED'
  const modeById = (id: number | '') => modes.find((m) => m.id === id)
  const subById = (id: number | '') => subCats.find((s) => s.id === id)

  const limitWarnings = useMemo(
    () =>
      lines
        .map((l) => {
          const sc = subCats.find((s) => s.id === l.subCategoryId)
          const amt = Number(l.amount) || 0
          if (sc && sc.limitAmount != null && amt > sc.limitAmount) {
            return `${sc.name} (${inr(amt)}) is above its ${inr(sc.limitAmount)} limit — will need Finance Manager approval.`
          }
          return null
        })
        .filter((x): x is string => x != null),
    [lines, subCats],
  )

  function setLine(i: number, patch: Partial<LineRow>) {
    setLines((prev) => prev.map((l, idx) => (idx === i ? { ...l, ...patch } : l)))
  }
  function addLine() {
    setLines((prev) => [...prev, { ...freshLine(), subCategoryId: subCats[0]?.id ?? '', expenseModeId: modes.find((m) => m.active)?.id ?? '' }])
  }
  function removeLine(i: number) {
    setLines((prev) => (prev.length > 1 ? prev.filter((_, idx) => idx !== i) : prev))
  }

  function buildLines() {
    return lines
      .filter((l) => Number(l.amount) > 0 && l.subCategoryId !== '' && l.expenseModeId !== '')
      .map((l) => ({
        transactionDate: l.transactionDate,
        subCategoryId: Number(l.subCategoryId),
        expenseModeId: Number(l.expenseModeId),
        amount: Number(l.amount),
        bankId: l.bankId === '' ? null : Number(l.bankId),
        transactionRef: l.transactionRef || undefined,
        remark: l.remark || undefined,
      }))
  }

  function validate(requireLine: boolean): string | null {
    if (!receiverName.trim()) return 'Receiver Name is required'
    if (expenseCategoryId === '' || businessStatusId === '') return 'Category and status are required'
    if (requireLine && buildLines().length === 0) return 'Add at least one expense row with an amount'
    for (const l of lines) {
      if (Number(l.amount) > 0) {
        const m = modeById(l.expenseModeId)
        if (m?.requiresBank && l.bankId === '') return `${m.name} needs a bank`
        if (m?.requiresRef && !l.transactionRef.trim()) return `${m.name} needs a transaction reference`
      }
    }
    return null
  }

  function buildBody(submit: boolean): CreateExpenseRequest {
    return {
      jobCardId: jobCardId === '' ? undefined : Number(jobCardId),
      receiverName: receiverName.trim(),
      expenseCategoryId: Number(expenseCategoryId),
      businessStatusId: Number(businessStatusId),
      lines: buildLines(),
      submit,
    }
  }

  async function saveNew(submit: boolean) {
    const problem = validate(submit)
    if (problem) {
      setError(problem)
      return
    }
    setBusy(true)
    setError('')
    try {
      const { data } = await expensesApi.create(buildBody(submit))
      finish(submit, data)
    } catch (e) {
      setError(apiError(e, 'Could not save the expense.'))
    } finally {
      setBusy(false)
    }
  }

  /**
   * Save the form as a draft so the Documents panel has an id to attach to. Returns the new
   * expense id, or null if it could not be saved (the error is already shown). Used only by
   * AttachmentsPanel; the normal Save Draft button still navigates home.
   */
  async function ensureDraft(): Promise<number | null> {
    if (loadedDoc) return loadedDoc.id
    const problem = validate(false)
    if (problem) {
      setError(problem)
      return null
    }
    setBusy(true)
    setError('')
    try {
      const { data } = await expensesApi.create(buildBody(false))
      setLoadedDoc(data)
      setNotice(`Saved as draft ${data.documentNo ?? `#${data.id}`}. Add documents below, then Submit when ready.`)
      navigate(`/app/new-expense?editDoc=${data.id}`, { replace: true })
      return data.id
    } catch (e) {
      setError(apiError(e, 'Could not save the draft.'))
      return null
    } finally {
      setBusy(false)
    }
  }

  async function resubmitEdited() {
    if (!loadedDoc) return
    const problem = validate(true)
    if (problem) {
      setError(problem)
      return
    }
    setBusy(true)
    setError('')
    try {
      await expensesApi.patch(loadedDoc.id, {
        jobCardId: jobCardId === '' ? undefined : Number(jobCardId),
        receiverName: receiverName.trim(),
        expenseCategoryId: Number(expenseCategoryId),
        businessStatusId: Number(businessStatusId),
      })
      const { data } = await expensesApi.resubmit(loadedDoc.id)
      finish(true, data)
    } catch (e) {
      setError(apiError(e, 'Could not resubmit.'))
    } finally {
      setBusy(false)
    }
  }

  async function transferToClaim() {
    if (!loadedDoc) return
    setBusy(true)
    setError('')
    try {
      const { data } = await expensesApi.transferToClaim(loadedDoc.id)
      navigate(`/app?flash=${encodeURIComponent(`${data.documentNo ?? `#${data.id}`} moved to claim`)}`)
    } catch (e) {
      setError(apiError(e, 'Could not transfer to claim.'))
    } finally {
      setBusy(false)
    }
  }

  function finish(submitted: boolean, doc: ExpenseDocument) {
    const ref = doc.documentNo ?? `draft #${doc.id}`
    navigate(`/app?flash=${encodeURIComponent(submitted ? `${ref} submitted for checking` : `${ref} saved as draft`)}`)
  }

  const inEditMode = editDocId != null
  const showTransferButton =
    inEditMode &&
    loadedDoc != null &&
    loadedDoc.claimEligible &&
    !loadedDoc.businessStatusTriggersClaim &&
    loadedDoc.workflowStatus !== 'REJECTED' &&
    loadedDoc.workflowStatus !== 'CLOSED'

  return (
    <div style={{ maxWidth: 940, margin: '0 auto' }}>
      <button
        type="button"
        onClick={() => navigate(-1)}
        style={{ background: 'none', border: 'none', color: 'var(--navy)', fontSize: '0.82rem', fontWeight: 600, cursor: 'pointer', padding: '4px 0', marginBottom: 12 }}
      >
        ← Back
      </button>

      {notice && (
        <div className="dams-anim-notice" style={{
          background: 'var(--navy3)', border: '1px solid #C9D8F2', color: 'var(--navy)',
          borderRadius: 9, padding: '10px 14px', fontSize: '0.82rem', marginBottom: 14,
        }}>
          {notice}
        </div>
      )}
      <ErrorBanner message={error} />

      {loadedDoc?.workflowStatus === 'QUERIED' && queryNote(loadedDoc.history) && (
        <div style={{ background: 'var(--amber-bg)', border: '1px solid #EAD3AE', color: 'var(--amber)', borderRadius: 8, padding: '10px 13px', fontSize: '0.82rem', marginBottom: 12 }}>
          <strong>Query from the accountant:</strong> {queryNote(loadedDoc.history)}
        </div>
      )}

      <section style={{ ...card, padding: 0, marginTop: 12 }}>
        <div style={{ padding: '14px 18px', borderBottom: '1px solid var(--line)', display: 'flex', alignItems: 'center', gap: 10 }}>
          <h3 style={{ fontSize: '0.94rem', fontWeight: 700 }}>Expense Entry</h3>
          <span style={{ fontSize: '0.76rem', color: 'var(--muted)' }}>petty cash — one record per vendor / cause</span>
          <span style={{ flex: 1 }} />
          <span style={{
            fontSize: '0.68rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.03em',
            padding: '2px 9px', borderRadius: 999,
            background: loadedDoc?.workflowStatus === 'QUERIED' ? 'var(--amber-bg)' : 'var(--gray-bg)',
            color: loadedDoc?.workflowStatus === 'QUERIED' ? 'var(--amber)' : 'var(--gray)',
          }}>
            {loadedDoc?.workflowStatus ?? 'DRAFT'}
          </span>
        </div>

        <div style={{ padding: '16px 18px' }}>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0 32px' }}>
            {/* left column */}
            <div>
              <Row label="Receiver Name" req>
                <input
                  value={receiverName}
                  onChange={(e) => setReceiverName(e.target.value)}
                  placeholder="Vendor / payee name"
                  style={inputStyle}
                />
              </Row>
              <Row label="Job Card">
                <select
                  value={jobCardId}
                  onChange={(e) => setJobCardId(e.target.value === '' ? '' : Number(e.target.value))}
                  style={inputStyle}
                >
                  <option value="">— none (branch overhead) —</option>
                  {jobCardOpts.map((o) => (
                    <option key={o.id} value={o.id}>
                      {o.reference}{o.categoryName ? ` · ${o.categoryName}` : ''}
                    </option>
                  ))}
                </select>
              </Row>
              <Row label="Expenses Category">
                <select value={expenseCategoryId} onChange={(e) => setExpenseCategoryId(Number(e.target.value))} style={inputStyle}>
                  {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </Row>
            </div>

            {/* right column */}
            <div>
              <Row label="Total Expenses">
                <span style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums', color: 'var(--red)' }}>{inr(total)}</span>
              </Row>
              <Row label="Status">
                <select value={businessStatusId} onChange={(e) => setBusinessStatusId(Number(e.target.value))} style={inputStyle}>
                  {statuses.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
              </Row>
              <Row label="DAMS-Expenses-ID">
                <input readOnly value={loadedDoc?.documentNo ?? 'assigned on submit'} style={{ ...inputStyle, background: 'var(--bg)', color: 'var(--muted)', fontFamily: 'Consolas, monospace' }} />
              </Row>

              {/* Documents live in the space under the ID field, in this column. */}
              <AttachmentsPanel
                docId={loadedDoc?.id ?? editDocId}
                noun="expense"
                frozen={!!attachFrozen}
                lineTargets={attachLineTargets}
                api={expensesApi}
                ensureDraft={ensureDraft}
              />
            </div>
          </div>

          {/* expense lines */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, margin: '22px 0 10px' }}>
            <h4 style={{ fontSize: '0.84rem', fontWeight: 700, color: 'var(--navy)' }}>Expense Lines</h4>
            <span style={{ fontSize: '0.74rem', color: 'var(--muted)' }}>one row per payment made to this receiver</span>
          </div>

          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 780 }}>
              <thead>
                <tr>
                  {['Date', 'Sub-category', 'Amount', 'Payment Mode', 'Bank Name', 'Transaction ID', 'Remark', ''].map((h) => (
                    <th key={h} style={{
                      fontSize: '0.68rem', textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--faint)',
                      textAlign: 'left', padding: '7px 6px', borderBottom: '1.5px solid var(--line)', background: '#FAFBFC',
                    }}>
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {lines.map((l, i) => {
                  const m = modeById(l.expenseModeId)
                  const sc = subById(l.subCategoryId)
                  const over = sc?.limitAmount != null && (Number(l.amount) || 0) > sc.limitAmount
                  return (
                    <tr key={i}>
                      <td style={cellStyle}>
                        <input type="date" value={l.transactionDate} onChange={(e) => setLine(i, { transactionDate: e.target.value })} style={cellInput} />
                      </td>
                      <td style={cellStyle}>
                        <select value={l.subCategoryId} onChange={(e) => setLine(i, { subCategoryId: Number(e.target.value) })} style={cellInput}>
                          {subCats.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                        </select>
                      </td>
                      <td style={cellStyle}>
                        <input
                          type="number"
                          value={l.amount}
                          onChange={(e) => setLine(i, { amount: e.target.value })}
                          style={{ ...cellInput, textAlign: 'right', ...(over ? { borderColor: 'var(--amber)', color: 'var(--amber)' } : {}) }}
                        />
                      </td>
                      <td style={cellStyle}>
                        <select value={l.expenseModeId} onChange={(e) => setLine(i, { expenseModeId: Number(e.target.value) })} style={cellInput}>
                          {modes.map((mm) => <option key={mm.id} value={mm.id}>{mm.name}</option>)}
                        </select>
                      </td>
                      <td style={cellStyle}>
                        <select value={l.bankId} disabled={!m?.requiresBank} onChange={(e) => setLine(i, { bankId: e.target.value === '' ? '' : Number(e.target.value) })} style={cellInput}>
                          <option value="">{m?.requiresBank ? 'Select…' : '—'}</option>
                          {banks.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
                        </select>
                      </td>
                      <td style={cellStyle}>
                        <input value={l.transactionRef} disabled={!m?.requiresRef} onChange={(e) => setLine(i, { transactionRef: e.target.value })} placeholder={m?.requiresRef ? 'UPI / bank ref' : '—'} style={cellInput} />
                      </td>
                      <td style={cellStyle}>
                        <input value={l.remark} onChange={(e) => setLine(i, { remark: e.target.value })} style={cellInput} />
                      </td>
                      <td style={cellStyle}>
                        {lines.length > 1 && (
                          <button type="button" onClick={() => removeLine(i)} style={{ border: 'none', background: 'none', color: 'var(--red)', fontWeight: 700, fontSize: '0.95rem', cursor: 'pointer' }}>×</button>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
          <button
            type="button"
            onClick={addLine}
            style={{ border: '1.5px dashed var(--line)', background: 'var(--surface)', borderRadius: 7, padding: '8px 14px', fontSize: '0.8rem', fontWeight: 600, color: 'var(--navy2)', marginTop: 8, cursor: 'pointer' }}
          >
            ＋ Add expense row
          </button>

          {limitWarnings.length > 0 && (
            <div style={{ marginTop: 10, display: 'flex', flexDirection: 'column', gap: 6 }}>
              {limitWarnings.map((w, i) => (
                <div key={i} style={{
                  background: 'var(--amber-bg)', color: 'var(--amber)', border: '1px solid #F1D9A8',
                  borderRadius: 7, padding: '7px 12px', fontSize: '0.78rem', fontWeight: 600,
                }}>
                  ⚠ {w}
                </div>
              ))}
            </div>
          )}

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 28, padding: '12px 6px 4px', borderTop: '2px solid var(--line)', marginTop: 12 }}>
            <Foot k="Total Expenses" v={inr(total)} />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, paddingTop: 16, marginTop: 8, borderTop: '1px solid var(--line)' }}>
            <button type="button" onClick={() => navigate(-1)} style={ghostBtn} disabled={busy}>Cancel</button>
            {showTransferButton && (
              <button type="button" onClick={transferToClaim} style={ghostBtn} disabled={busy}>Transfer to Claim</button>
            )}
            {inEditMode ? (
              <button type="button" onClick={resubmitEdited} style={primaryBtn(busy)} disabled={busy}>
                {busy ? <><Spinner /> Resubmitting…</> : 'Resubmit'}
              </button>
            ) : (
              <>
                <button type="button" onClick={() => saveNew(false)} style={ghostBtn} disabled={busy}>Save Draft</button>
                <button type="button" onClick={() => saveNew(true)} style={primaryBtn(busy)} disabled={busy}>
                  {busy ? <><Spinner /> Submitting…</> : 'Submit'}
                </button>
              </>
            )}
          </div>
        </div>
      </section>
    </div>
  )
}

const cellStyle = { padding: 6, borderBottom: '1px solid var(--line)', verticalAlign: 'middle' as const }
const cellInput = { border: '1.5px solid var(--line)', borderRadius: 6, padding: '6px 7px', width: '100%', fontSize: '0.82rem' }

function Row({ label, req, children }: { label: string; req?: boolean; children: React.ReactNode }) {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: '140px 1fr', alignItems: 'center', padding: '8px 0', borderBottom: '1px solid var(--line)', gap: 10 }}>
      <label style={{ fontSize: '0.78rem', fontWeight: 600, color: 'var(--muted)' }}>
        {label} {req && <span style={{ color: 'var(--red)' }}>*</span>}
      </label>
      {children}
    </div>
  )
}

function Foot({ k, v }: { k: string; v: string }) {
  return (
    <div style={{ textAlign: 'right' }}>
      <div style={{ fontSize: '0.72rem', color: 'var(--muted)', textTransform: 'uppercase', letterSpacing: '0.03em' }}>{k}</div>
      <div style={{ fontSize: '1.05rem', fontWeight: 700, fontVariantNumeric: 'tabular-nums', color: 'var(--red)' }}>{v}</div>
    </div>
  )
}

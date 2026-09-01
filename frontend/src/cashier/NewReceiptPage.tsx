import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { mastersApi, type MasterRow } from '../api/masters'
import { customersApi } from '../api/customers'
import { jobCardsApi } from '../api/jobCards'
import { receiptsApi, type CreateReceiptRequest, type DocumentHistoryEntry, type ReceiveDocument } from '../api/receipts'
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
 * Receive Entry (intial ui prototypes/cashier-home.html). One continuous flow: the header
 * creates/points at a job card, the lines are the settlement payments. Save Draft keeps it
 * editable with no number; Submit assigns the gap-free DAMS-Receive-ID.
 *
 * When opened with ?editDoc= (a QUERIED document) it loads that document for fix-and-resubmit.
 */

type LineRow = {
  transactionDate: string
  settlementModeId: number | ''
  amount: string
  bankId: number | ''
  transactionRef: string
  remark: string
}

const freshLine = (modeId: number | ''): LineRow => ({
  transactionDate: istToday(), settlementModeId: modeId, amount: '', bankId: '', transactionRef: '', remark: '',
})

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

export default function NewReceiptPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const editDocId = params.get('editDoc') ? Number(params.get('editDoc')) : null
  const prefillCustomerId = params.get('customerId') ? Number(params.get('customerId')) : null

  const [categories, setCategories] = useState<MasterRow[]>([])
  const [statuses, setStatuses] = useState<MasterRow[]>([])
  const [modes, setModes] = useState<MasterRow[]>([])
  const [banks, setBanks] = useState<MasterRow[]>([])

  // header
  const [customerId, setCustomerId] = useState<number | null>(prefillCustomerId)
  const [customerName, setCustomerName] = useState('')
  const [vehicleNo, setVehicleNo] = useState('')
  const [dbmId, setDbmId] = useState('')
  const [invoiceNo, setInvoiceNo] = useState('')
  const [invoiceAmount, setInvoiceAmount] = useState('')
  const [categoryId, setCategoryId] = useState<number | ''>('')
  const [businessStatusId, setBusinessStatusId] = useState<number | ''>('')
  const [b2b, setB2b] = useState(false)
  const [gstNo, setGstNo] = useState('')

  const [lines, setLines] = useState<LineRow[]>([freshLine('')])
  const [loadedDoc, setLoadedDoc] = useState<ReceiveDocument | null>(null)

  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  // masters + optional prefill / edit-load
  useEffect(() => {
    let live = true
    Promise.all([
      mastersApi.list('receive-categories'),
      mastersApi.list('receive-statuses'),
      mastersApi.list('settlement-modes'),
      mastersApi.list('banks'),
    ])
      .then(([c, s, m, bk]) => {
        if (!live) return
        setCategories(c.data.filter((x) => x.active))
        setStatuses(s.data.filter((x) => x.active))
        setModes(m.data.filter((x) => x.active))
        setBanks(bk.data.filter((x) => x.active))
        const firstMode = m.data.find((x) => x.active)?.id ?? ''
        setLines((prev) => prev.map((l) => (l.settlementModeId === '' ? { ...l, settlementModeId: firstMode } : l)))
        if (!editDocId) {
          setCategoryId(c.data.find((x) => x.active)?.id ?? '')
          setBusinessStatusId(s.data.find((x) => x.active)?.id ?? '')
        }
      })
      .catch((e) => live && setError(apiError(e, 'Could not load the form options.')))
    return () => {
      live = false
    }
  }, [editDocId])

  useEffect(() => {
    if (prefillCustomerId && !editDocId) {
      customersApi.get(prefillCustomerId)
        .then(({ data }) => {
          setCustomerName(data.name)
          setVehicleNo(data.vehicles[0]?.vehicleNo ?? '')
          setNotice(`New receipt for ${data.name} — customer pre-filled from their record.`)
        })
        .catch(() => {})
    }
  }, [prefillCustomerId, editDocId])

  useEffect(() => {
    if (!editDocId) return
    receiptsApi.get(editDocId)
      .then(({ data }) => {
        setLoadedDoc(data)
        setCustomerId(data.customerId)
        setCustomerName(data.customerName ?? '')
        setVehicleNo(data.vehicleNo ?? '')
        setDbmId(data.dbmId ?? '')
        setInvoiceNo(data.invoiceNo ?? '')
        setInvoiceAmount(data.invoiceAmount != null ? String(data.invoiceAmount) : '')
        setCategoryId(data.categoryId)
        setBusinessStatusId(data.businessStatusId)
        setB2b(data.b2b)
        setGstNo(data.gstNo ?? '')
        setLines(
          data.lines.length
            ? data.lines.map((l) => ({
                transactionDate: l.transactionDate,
                settlementModeId: l.settlementModeId,
                amount: String(l.amount),
                bankId: l.bankId ?? '',
                transactionRef: l.transactionRef ?? '',
                remark: l.remark ?? '',
              }))
            : [freshLine('')],
        )
        setNotice(
          data.workflowStatus === 'QUERIED'
            ? `This entry was queried — fix it and resubmit. ${data.documentNo ?? ''}`
            : `Editing ${data.documentNo ?? 'draft'}.`,
        )
      })
      .catch((e) => setError(apiError(e, 'Could not load this document.')))
  }, [editDocId])

  const totalReceived = useMemo(
    () => lines.reduce((a, l) => a + (Number(l.amount) || 0), 0),
    [lines],
  )
  const pending = useMemo(() => {
    const inv = Number(invoiceAmount) || 0
    return inv ? Math.max(0, inv - totalReceived) : 0
  }, [invoiceAmount, totalReceived])

  // Documents can be tagged to the whole receipt or to one saved settlement line.
  const attachLineTargets: LineTarget[] = useMemo(
    () => (loadedDoc?.lines ?? []).map((l, i) => ({
      lineNo: l.lineNo,
      label: `Line ${i + 1} · ${modes.find((m) => m.id === l.settlementModeId)?.name ?? 'payment'} · ${inr(l.amount)}`,
    })),
    [loadedDoc, modes],
  )
  const attachFrozen = loadedDoc != null
    && (loadedDoc.settled || loadedDoc.workflowStatus === 'REJECTED')

  const modeById = (id: number | '') => modes.find((m) => m.id === id)

  function setLine(i: number, patch: Partial<LineRow>) {
    setLines((prev) => prev.map((l, idx) => (idx === i ? { ...l, ...patch } : l)))
  }
  function addLine() {
    setLines((prev) => [...prev, freshLine(modes.find((m) => m.active)?.id ?? '')])
  }
  function removeLine(i: number) {
    setLines((prev) => (prev.length > 1 ? prev.filter((_, idx) => idx !== i) : prev))
  }

  function buildLines() {
    return lines
      .filter((l) => Number(l.amount) > 0 && l.settlementModeId !== '')
      .map((l) => ({
        transactionDate: l.transactionDate,
        settlementModeId: Number(l.settlementModeId),
        amount: Number(l.amount),
        bankId: l.bankId === '' ? null : Number(l.bankId),
        transactionRef: l.transactionRef || undefined,
        remark: l.remark || undefined,
      }))
  }

  function validate(requireLine: boolean): string | null {
    if (!editDocId && !customerId && !customerName.trim()) return 'Customer name is required'
    if (b2b && !gstNo.trim()) return 'GST# is mandatory for B2B'
    if (categoryId === '' || businessStatusId === '') return 'Category and status are required'
    if (requireLine && buildLines().length === 0) return 'Add at least one settlement row with an amount'
    for (const l of lines) {
      if (Number(l.amount) > 0) {
        const m = modeById(l.settlementModeId)
        if (m?.requiresBank && l.bankId === '') return `${m.name} needs a bank`
      }
    }
    return null
  }

  function buildBody(submit: boolean): CreateReceiptRequest {
    return {
      customerId: customerId ?? undefined,
      customerName: customerId ? undefined : customerName.trim(),
      vehicleNo: vehicleNo.trim() || undefined,
      dbmId: dbmId.trim() || undefined,
      invoiceNo: invoiceNo.trim() || undefined,
      invoiceAmount: invoiceAmount ? Number(invoiceAmount) : undefined,
      b2b,
      gstNo: b2b ? gstNo.trim() : undefined,
      categoryId: Number(categoryId),
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
      const { data } = await receiptsApi.create(buildBody(submit))
      finish(submit, data)
    } catch (e) {
      setError(apiError(e, 'Could not save the receipt.'))
    } finally {
      setBusy(false)
    }
  }

  /**
   * Save the form as a draft so the Documents panel has an id to attach to. Returns the new
   * receipt id, or null if it could not be saved (validation, server error) — the error is
   * already shown. Used only by AttachmentsPanel; a normal Save Draft still navigates home.
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
      const { data } = await receiptsApi.create(buildBody(false))
      setLoadedDoc(data)
      setNotice(`Saved as draft ${data.documentNo ?? `#${data.id}`}. Add documents below, then Submit when ready.`)
      navigate(`/app/new-receipt?editDoc=${data.id}`, { replace: true })
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
      // header fields live on the job card
      await jobCardsApi.patch(loadedDoc.jobCardId, {
        invoiceNo: invoiceNo.trim(),
        invoiceAmount: invoiceAmount ? Number(invoiceAmount) : undefined,
        dbmId: dbmId.trim(),
        b2b,
        gstNo: b2b ? gstNo.trim() : '',
        categoryId: Number(categoryId),
        businessStatusId: Number(businessStatusId),
      })
      const { data } = await receiptsApi.resubmit(loadedDoc.id)
      finish(true, data)
    } catch (e) {
      setError(apiError(e, 'Could not resubmit.'))
    } finally {
      setBusy(false)
    }
  }

  function finish(submitted: boolean, doc: ReceiveDocument) {
    const ref = doc.documentNo ?? `draft #${doc.id}`
    navigate(`/app?flash=${encodeURIComponent(submitted ? `${ref} submitted for checking` : `${ref} saved as draft`)}`)
  }

  const inEditMode = editDocId != null

  return (
    <div style={{ maxWidth: 900, margin: '0 auto' }}>
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
          <h3 style={{ fontSize: '0.94rem', fontWeight: 700 }}>Receive Entry</h3>
          <span style={{ fontSize: '0.76rem', color: 'var(--muted)' }}>customer receipt — one record per job / cause</span>
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
              <Row label="Customer Name" req>
                {customerId && inEditMode ? (
                  <input readOnly value={customerName} style={{ ...inputStyle, background: 'var(--bg)' }} />
                ) : (
                  <input
                    value={customerName}
                    onChange={(e) => { setCustomerName(e.target.value); setCustomerId(null) }}
                    placeholder="Type a customer name"
                    style={inputStyle}
                  />
                )}
              </Row>
              <Row label="Vehicle #">
                <input value={vehicleNo} onChange={(e) => setVehicleNo(e.target.value)} placeholder="OD05CA4177" style={inputStyle} />
              </Row>
              <Row label="Job Card / DBM">
                <input value={dbmId} onChange={(e) => setDbmId(e.target.value)} placeholder="4009941587" style={inputStyle} />
              </Row>
              <Row label="Invoice #">
                <input value={invoiceNo} onChange={(e) => setInvoiceNo(e.target.value)} placeholder="7731122600388" style={inputStyle} />
              </Row>
              <Row label="Tran. Category">
                <select value={categoryId} onChange={(e) => setCategoryId(Number(e.target.value))} style={inputStyle}>
                  {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                </select>
              </Row>
              <Row label="Invoice Amount">
                <input type="number" value={invoiceAmount} onChange={(e) => setInvoiceAmount(e.target.value)} placeholder="Total amount" style={inputStyle} />
              </Row>
            </div>

            {/* right column */}
            <div>
              <Row label="Pending Amount">
                <span style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums', color: pending > 0 ? 'var(--amber)' : 'var(--faint)' }}>
                  {invoiceAmount ? inr(pending) : 'Zero (no invoice yet)'}
                </span>
              </Row>
              <Row label="Status">
                <select value={businessStatusId} onChange={(e) => setBusinessStatusId(Number(e.target.value))} style={inputStyle}>
                  {statuses.map((s) => <option key={s.id} value={s.id}>{s.name}</option>)}
                </select>
              </Row>
              <Row label="Transaction Type">
                <div style={{ display: 'flex', border: '1.5px solid var(--line)', borderRadius: 7, overflow: 'hidden', width: 'fit-content' }}>
                  {(['B2C', 'B2B'] as const).map((t) => {
                    const on = (t === 'B2B') === b2b
                    return (
                      <button
                        key={t}
                        type="button"
                        onClick={() => setB2b(t === 'B2B')}
                        style={{
                          border: 'none', padding: '6px 16px', fontSize: '0.8rem', fontWeight: 600,
                          background: on ? 'var(--navy2)' : 'var(--surface)', color: on ? '#fff' : 'var(--muted)', cursor: 'pointer',
                        }}
                      >
                        {t}
                      </button>
                    )
                  })}
                </div>
              </Row>
              {b2b && (
                <Row label="GST#" req>
                  <input value={gstNo} onChange={(e) => setGstNo(e.target.value)} placeholder="21ABCDE1234F1Z5" style={inputStyle} />
                </Row>
              )}
              <Row label="DAMS-Receive-ID">
                <input readOnly value={loadedDoc?.documentNo ?? 'assigned on submit'} style={{ ...inputStyle, background: 'var(--bg)', color: 'var(--muted)', fontFamily: 'Consolas, monospace' }} />
              </Row>

              {/* Documents live in the space under the ID field, in this column. */}
              <AttachmentsPanel
                docId={loadedDoc?.id ?? editDocId}
                noun="receipt"
                frozen={attachFrozen}
                lineTargets={attachLineTargets}
                api={receiptsApi}
                ensureDraft={ensureDraft}
              />
            </div>
          </div>

          {/* settlement lines */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 10, margin: '22px 0 10px' }}>
            <h4 style={{ fontSize: '0.84rem', fontWeight: 700, color: 'var(--navy)' }}>Settlement Lines</h4>
            <span style={{ fontSize: '0.74rem', color: 'var(--muted)' }}>one row per payment received against this job / cause</span>
          </div>

          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 720 }}>
              <thead>
                <tr>
                  {['Date', 'Settlement Mode', 'Receive Amount', 'Bank Name', 'Transaction ID', 'Remarks', ''].map((h) => (
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
                  const m = modeById(l.settlementModeId)
                  return (
                    <tr key={i}>
                      <td style={cellStyle}>
                        <input type="date" value={l.transactionDate} onChange={(e) => setLine(i, { transactionDate: e.target.value })} style={cellInput} />
                      </td>
                      <td style={cellStyle}>
                        <select value={l.settlementModeId} onChange={(e) => setLine(i, { settlementModeId: Number(e.target.value) })} style={cellInput}>
                          {modes.map((mm) => <option key={mm.id} value={mm.id}>{mm.name}</option>)}
                        </select>
                      </td>
                      <td style={cellStyle}>
                        <input type="number" value={l.amount} onChange={(e) => setLine(i, { amount: e.target.value })} style={{ ...cellInput, textAlign: 'right' }} />
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
            ＋ Add settlement row
          </button>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 28, padding: '12px 6px 4px', borderTop: '2px solid var(--line)', marginTop: 12 }}>
            <Foot k="Total Received" v={inr(totalReceived)} tone="green" />
            <Foot k="Pending" v={invoiceAmount ? inr(pending) : '—'} tone={pending > 0 ? 'amber' : undefined} />
          </div>

          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, paddingTop: 16, marginTop: 8, borderTop: '1px solid var(--line)' }}>
            <button type="button" onClick={() => navigate(-1)} style={ghostBtn} disabled={busy}>Cancel</button>
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

function Foot({ k, v, tone }: { k: string; v: string; tone?: 'green' | 'amber' }) {
  const color = tone === 'green' ? 'var(--green)' : tone === 'amber' ? 'var(--amber)' : 'var(--ink)'
  return (
    <div style={{ textAlign: 'right' }}>
      <div style={{ fontSize: '0.72rem', color: 'var(--muted)', textTransform: 'uppercase', letterSpacing: '0.03em' }}>{k}</div>
      <div style={{ fontSize: '1.05rem', fontWeight: 700, fontVariantNumeric: 'tabular-nums', color }}>{v}</div>
    </div>
  )
}

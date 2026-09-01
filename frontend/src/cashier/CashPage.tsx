import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { mastersApi, type MasterRow } from '../api/masters'
import {
  cashApi,
  type CashDirection,
  type CashDocument,
  type CashDrawer,
  type DocumentHistoryEntry,
} from '../api/cash'
import { card, ErrorBanner, inr, primaryBtn, ghostBtn, inputStyle, Modal, Badge, Skeleton, SkeletonRows, fmtDate, istToday } from '../shell/ui'

/**
 * Cash page (AGENT.md decision #1 — no HTML mockup). One dedicated per-branch, per-day
 * screen: the live drawer position and its breakdown, the day's IN/OUT movements, and the
 * end-of-day close. Closing locks the date.
 */

const todayStr = istToday

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

/** The most recent reviewer question / rejection reason, for the fix-and-resubmit banner. */
function queryNote(history: DocumentHistoryEntry[]): string | null {
  for (let i = history.length - 1; i >= 0; i--) {
    const h = history[i]
    if ((h.action === 'Queried' || h.action === 'Rejected') && h.note) return h.note
  }
  return null
}

export default function CashPage() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const editDocId = params.get('editDoc') ? Number(params.get('editDoc')) : null

  const [date, setDate] = useState(todayStr())
  const [drawer, setDrawer] = useState<CashDrawer | null>(null)
  const [banks, setBanks] = useState<MasterRow[]>([])
  const [error, setError] = useState('')
  const [reloadTick, setReloadTick] = useState(0)

  const [movementModal, setMovementModal] = useState<{ direction: CashDirection; editDoc?: CashDocument } | null>(null)
  const [closeModal, setCloseModal] = useState(false)

  useEffect(() => {
    mastersApi.list('banks').then(({ data }) => setBanks(data.filter((b) => b.active))).catch(() => {})
  }, [])

  useEffect(() => {
    let live = true
    setError('')
    cashApi.drawer(date)
      .then(({ data }) => { if (live) setDrawer(data) })
      .catch((e) => { if (live) setError(apiError(e, 'Could not load the drawer.')) })
    return () => { live = false }
  }, [date, reloadTick])

  // ?editDoc= — open the movement form for a queried / draft entry
  useEffect(() => {
    if (!editDocId) return
    cashApi.get(editDocId)
      .then(({ data }) => {
        setDate(data.transactionDate)
        setMovementModal({ direction: data.direction, editDoc: data })
        params.delete('editDoc')
        setParams(params, { replace: true })
      })
      .catch((e) => setError(apiError(e, 'Could not load that movement.')))
  }, [editDocId, params, setParams])

  function refresh() {
    setReloadTick((n) => n + 1)
  }

  return (
    <div style={{ maxWidth: 860, margin: '0 auto' }}>
      <button
        type="button"
        onClick={() => navigate('/app')}
        style={{ background: 'none', border: 'none', color: 'var(--navy)', fontSize: '0.82rem', fontWeight: 600, cursor: 'pointer', padding: '4px 0', marginBottom: 12 }}
      >
        ← Home
      </button>

      <div style={{ display: 'flex', alignItems: 'baseline', gap: 12, flexWrap: 'wrap', marginBottom: 4 }}>
        <h1 style={{ fontSize: '1.3rem', color: 'var(--navy)' }}>
          Cash{drawer?.branchName ? ` — ${drawer.branchName}` : ''}
        </h1>
        <input
          type="date"
          value={date}
          max={todayStr()}
          onChange={(e) => setDate(e.target.value)}
          style={{ ...inputStyle, width: 'auto', padding: '6px 9px', fontSize: '0.82rem' }}
        />
      </div>
      <div style={{ fontSize: '0.85rem', color: 'var(--muted)', marginBottom: 16 }}>
        Internal bank ↔ drawer movements and the day's cash position. Closing the day locks the date.
      </div>

      <ErrorBanner message={error} />
      {drawer == null && !error && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <Skeleton height={120} radius={10} />
          <SkeletonRows rows={4} height={48} />
        </div>
      )}

      {drawer && (
        <>
          <DrawerCard drawer={drawer} />

          {drawer.closed ? (
            <ClosedBanner drawer={drawer} />
          ) : (
            <div style={{ display: 'flex', gap: 10, margin: '14px 0 18px', flexWrap: 'wrap' }}>
              <button type="button" onClick={() => setMovementModal({ direction: 'IN' })}
                style={{ ...primaryBtn(), background: 'var(--green)' }}>
                ＋ Cash In
              </button>
              <button type="button" onClick={() => setMovementModal({ direction: 'OUT' })}
                style={{ ...primaryBtn(), background: 'var(--red)' }}>
                − Cash Out
              </button>
              <span style={{ flex: 1 }} />
              <button type="button" onClick={() => setCloseModal(true)} style={primaryBtn()}>
                Close Day
              </button>
            </div>
          )}

          <MovementsTable
            movements={drawer.movements}
            locked={drawer.closed}
            onEdit={(m) => setMovementModal({ direction: m.direction, editDoc: m })}
          />
        </>
      )}

      {movementModal && drawer && (
        <MovementModal
          date={date}
          branches={banks}
          direction={movementModal.direction}
          editDoc={movementModal.editDoc}
          onClose={() => setMovementModal(null)}
          onDone={() => { setMovementModal(null); refresh() }}
        />
      )}
      {closeModal && drawer && (
        <CloseDayModal
          drawer={drawer}
          onClose={() => setCloseModal(false)}
          onDone={() => { setCloseModal(false); refresh() }}
        />
      )}
    </div>
  )
}

// ───────────────────────────── Drawer summary ─────────────────────────────

function DrawerCard({ drawer }: { drawer: CashDrawer }) {
  return (
    <section style={{ ...card, padding: 0 }}>
      <div style={{ padding: '14px 18px', borderBottom: '1px solid var(--line)' }}>
        <h3 style={{ fontSize: '0.94rem', fontWeight: 700 }}>Drawer position</h3>
        <span style={{ fontSize: '0.76rem', color: 'var(--muted)' }}>
          opening + cash receipts + cash in − cash expenses − cash out
        </span>
      </div>
      <div style={{ padding: '8px 18px 14px' }}>
        <Line k="Opening" v={inr(drawer.opening)} note={!drawer.openingSet ? 'not set — ask your Accountant to set the branch opening' : undefined} />
        <Line k="＋ Cash receipts (cash mode)" v={inr(drawer.cashReceipts)} tone="green" />
        <Line k="＋ Cash In from bank" v={inr(drawer.cashIn)} tone="green" />
        <Line k="− Cash expenses (cash mode)" v={inr(drawer.cashExpenses)} tone="red" />
        <Line k="− Cash Out to bank" v={inr(drawer.cashOut)} tone="red" />
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', padding: '12px 0 2px', borderTop: '2px solid var(--line)', marginTop: 6 }}>
          <span style={{ fontSize: '0.86rem', fontWeight: 700 }}>Computed position</span>
          <span style={{ fontSize: '1.35rem', fontWeight: 800, fontVariantNumeric: 'tabular-nums', color: 'var(--navy)' }}>
            {inr(drawer.computedPosition)}
          </span>
        </div>
      </div>
    </section>
  )
}

function Line({ k, v, tone, note }: { k: string; v: string; tone?: 'green' | 'red'; note?: string }) {
  const color = tone === 'green' ? 'var(--green)' : tone === 'red' ? 'var(--red)' : 'var(--ink)'
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', padding: '7px 0', borderBottom: '1px dashed var(--line)' }}>
      <span style={{ fontSize: '0.82rem', color: 'var(--muted)' }}>
        {k}
        {note && <span style={{ display: 'block', fontSize: '0.72rem', color: 'var(--amber)' }}>{note}</span>}
      </span>
      <span style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums', color }}>{v}</span>
    </div>
  )
}

function ClosedBanner({ drawer }: { drawer: CashDrawer }) {
  const c = drawer.close!
  return (
    <div style={{
      background: 'var(--gray-bg)', border: '1px solid var(--line)', borderRadius: 9,
      padding: '12px 16px', fontSize: '0.82rem', margin: '14px 0 18px',
    }}>
      <strong>Day closed.</strong> Counted {inr(c.countedAmount)} vs computed {inr(c.computedClosing)} ·{' '}
      variance <span style={{ color: c.variance === 0 ? 'var(--green)' : 'var(--amber)', fontWeight: 700 }}>{inr(c.variance)}</span>
      {c.varianceRemark && <> · “{c.varianceRemark}”</>} · by {c.closedByName ?? '—'}
      <div style={{ fontSize: '0.74rem', color: 'var(--faint)', marginTop: 3 }}>
        This date is locked — no new cash movements or backdated cash payments.
      </div>
    </div>
  )
}

// ───────────────────────────── Movements table ─────────────────────────────

function MovementsTable(props: {
  movements: CashDocument[]
  locked: boolean
  onEdit: (m: CashDocument) => void
}) {
  return (
    <section style={{ ...card, padding: 0 }}>
      <div style={{ padding: '14px 18px', borderBottom: '1px solid var(--line)' }}>
        <h3 style={{ fontSize: '0.94rem', fontWeight: 700 }}>Movements</h3>
      </div>
      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 640 }}>
          <thead>
            <tr>
              {['Doc #', 'Direction', 'Amount', 'Bank', 'Transaction ID', 'Status', ''].map((h) => (
                <th key={h} style={{
                  fontSize: '0.68rem', textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--faint)',
                  textAlign: 'left', padding: '8px 12px', borderBottom: '1.5px solid var(--line)', background: '#FAFBFC',
                }}>
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {props.movements.length === 0 && (
              <tr><td colSpan={7} style={{ padding: 18, color: 'var(--faint)', fontSize: '0.84rem' }}>No cash movements on this day.</td></tr>
            )}
            {props.movements.map((m) => {
              const editable = !props.locked && (m.workflowStatus === 'DRAFT' || m.workflowStatus === 'QUERIED')
              return (
                <tr key={m.id}>
                  <td style={cell}><span style={{ fontFamily: 'Consolas, monospace', fontSize: '0.8rem', fontWeight: 700, color: 'var(--navy2)' }}>{m.documentNo ?? 'draft'}</span></td>
                  <td style={cell}>
                    <span style={{
                      fontSize: '0.7rem', fontWeight: 800, padding: '2px 7px', borderRadius: 5,
                      background: m.direction === 'IN' ? 'var(--green-bg)' : 'var(--red-bg)',
                      color: m.direction === 'IN' ? 'var(--green)' : 'var(--red)',
                    }}>
                      {m.direction}
                    </span>
                  </td>
                  <td style={{ ...cell, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{inr(m.amount)}</td>
                  <td style={cell}>{m.bankName ?? '—'}</td>
                  <td style={cell}>{m.transactionRef ?? '—'}</td>
                  <td style={cell}>
                    <Badge tone={m.workflowStatus === 'QUERIED' ? 'amber' : m.workflowStatus === 'REJECTED' ? 'red' : m.workflowStatus === 'APPROVED' ? 'green' : 'gray'}>
                      {m.workflowStatus}
                    </Badge>
                  </td>
                  <td style={cell}>
                    {editable && (
                      <button type="button" onClick={() => props.onEdit(m)} style={ghostBtn}>
                        {m.workflowStatus === 'QUERIED' ? 'Fix & Resubmit' : 'Edit'}
                      </button>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </section>
  )
}

const cell = { padding: '10px 12px', borderTop: '1px solid var(--line)', fontSize: '0.84rem', verticalAlign: 'middle' as const }

// ───────────────────────────── Movement modal ─────────────────────────────

function MovementModal(props: {
  date: string
  branches: MasterRow[]
  direction: CashDirection
  editDoc?: CashDocument
  onClose: () => void
  onDone: () => void
}) {
  const edit = props.editDoc
  const [direction, setDirection] = useState<CashDirection>(edit?.direction ?? props.direction)
  const [transactionDate, setTransactionDate] = useState(edit?.transactionDate ?? props.date)
  const [amount, setAmount] = useState(edit ? String(edit.amount) : '')
  const [bankId, setBankId] = useState<number | ''>(edit?.bankId ?? '')
  const [transactionRef, setTransactionRef] = useState(edit?.transactionRef ?? '')
  const [remark, setRemark] = useState(edit?.remark ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const queried = edit?.workflowStatus === 'QUERIED'

  function body() {
    return {
      direction,
      transactionDate,
      amount: Number(amount),
      bankId: bankId === '' ? null : Number(bankId),
      transactionRef: transactionRef.trim() || undefined,
      remark: remark.trim() || undefined,
    }
  }

  function validate(): string | null {
    if (!(Number(amount) > 0)) return 'Enter an amount greater than 0'
    return null
  }

  async function save(submit: boolean) {
    const problem = validate()
    if (problem) { setError(problem); return }
    setBusy(true)
    setError('')
    try {
      if (edit) {
        await cashApi.patch(edit.id, body())
        if (queried) await cashApi.resubmit(edit.id)
        else if (submit) await cashApi.submit(edit.id)
      } else {
        await cashApi.create({ ...body(), submit })
      }
      props.onDone()
    } catch (e) {
      setError(apiError(e, 'Could not save the movement.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal
      title={edit ? `Edit ${edit.documentNo ?? 'draft movement'}` : direction === 'IN' ? 'Cash In from bank' : 'Cash Out to bank'}
      subtitle={edit ? undefined : 'internal money movement — no customer or job card'}
      onClose={props.onClose}
    >
      <ErrorBanner message={error} />

      {queried && queryNote(edit?.history ?? []) && (
        <div style={{ background: 'var(--amber-bg)', border: '1px solid #EAD3AE', color: 'var(--amber)', borderRadius: 8, padding: '10px 13px', fontSize: '0.82rem', marginBottom: 12 }}>
          <strong>Query from the reviewer:</strong> {queryNote(edit?.history ?? [])}
        </div>
      )}

      <div style={{ display: 'flex', border: '1.5px solid var(--line)', borderRadius: 7, overflow: 'hidden', width: 'fit-content' }}>
        {(['IN', 'OUT'] as const).map((d) => (
          <button key={d} type="button" onClick={() => setDirection(d)}
            style={{
              border: 'none', padding: '6px 18px', fontSize: '0.8rem', fontWeight: 700, cursor: 'pointer',
              background: direction === d ? (d === 'IN' ? 'var(--green)' : 'var(--red)') : 'var(--surface)',
              color: direction === d ? '#fff' : 'var(--muted)',
            }}>
            {d === 'IN' ? 'IN from bank' : 'OUT to bank'}
          </button>
        ))}
      </div>

      <label style={fieldLabel}>Date
        <input type="date" value={transactionDate} max={todayStr()} onChange={(e) => setTransactionDate(e.target.value)} style={inputStyle} />
      </label>
      <label style={fieldLabel}>Amount
        <input type="number" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="0" style={{ ...inputStyle, textAlign: 'right' }} />
      </label>
      <label style={fieldLabel}>Bank
        <select value={bankId} onChange={(e) => setBankId(e.target.value === '' ? '' : Number(e.target.value))} style={inputStyle}>
          <option value="">— none —</option>
          {props.branches.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}
        </select>
      </label>
      <label style={fieldLabel}>Transaction ID
        <input value={transactionRef} onChange={(e) => setTransactionRef(e.target.value)} placeholder="NEFT / deposit slip no." style={inputStyle} />
      </label>
      <label style={fieldLabel}>Remark
        <input value={remark} onChange={(e) => setRemark(e.target.value)} style={inputStyle} />
      </label>

      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, paddingTop: 4 }}>
        <button type="button" onClick={props.onClose} style={ghostBtn} disabled={busy}>Cancel</button>
        {queried ? (
          <button type="button" onClick={() => save(true)} style={primaryBtn(busy)} disabled={busy}>Save & Resubmit</button>
        ) : (
          <>
            <button type="button" onClick={() => save(false)} style={ghostBtn} disabled={busy}>Save Draft</button>
            <button type="button" onClick={() => save(true)} style={primaryBtn(busy)} disabled={busy}>Submit</button>
          </>
        )}
      </div>
    </Modal>
  )
}

// ───────────────────────────── Close-day modal ─────────────────────────────

function CloseDayModal(props: { drawer: CashDrawer; onClose: () => void; onDone: () => void }) {
  const computed = props.drawer.computedPosition
  const [counted, setCounted] = useState('')
  const [remark, setRemark] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const variance = useMemo(() => (counted === '' ? 0 : Number(counted) - computed), [counted, computed])
  const needRemark = variance !== 0

  async function confirm() {
    if (counted === '' || Number(counted) < 0) { setError('Enter the counted cash amount'); return }
    if (needRemark && !remark.trim()) { setError('A variance remark is required when the counted amount differs'); return }
    setBusy(true)
    setError('')
    try {
      await cashApi.closeDay({ closeDate: props.drawer.date, countedAmount: Number(counted), varianceRemark: remark.trim() || undefined })
      props.onDone()
    } catch (e) {
      setError(apiError(e, 'Could not close the day.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Modal title="Close the day" subtitle={`${props.drawer.branchName ?? ''} · ${fmtDate(props.drawer.date)}`} onClose={props.onClose}>
      <ErrorBanner message={error} />
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.84rem', padding: '4px 0' }}>
        <span style={{ color: 'var(--muted)' }}>Computed position</span>
        <span style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{inr(computed)}</span>
      </div>
      <label style={fieldLabel}>Physically counted cash
        <input type="number" value={counted} onChange={(e) => setCounted(e.target.value)} placeholder="0" style={{ ...inputStyle, textAlign: 'right' }} />
      </label>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.84rem', padding: '4px 0' }}>
        <span style={{ color: 'var(--muted)' }}>Variance (counted − computed)</span>
        <span style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums', color: variance === 0 ? 'var(--green)' : 'var(--amber)' }}>
          {inr(variance)}
        </span>
      </div>
      <label style={fieldLabel}>
        Variance remark {needRemark && <span style={{ color: 'var(--red)' }}>*</span>}
        <input value={remark} onChange={(e) => setRemark(e.target.value)} disabled={!needRemark}
          placeholder={needRemark ? 'Why does the count differ?' : 'not needed — count matches'} style={inputStyle} />
      </label>
      <div style={{ fontSize: '0.74rem', color: 'var(--faint)' }}>
        Closing locks {fmtDate(props.drawer.date)} — no new or backdated cash entries for this branch after this.
      </div>
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 10, paddingTop: 4 }}>
        <button type="button" onClick={props.onClose} style={ghostBtn} disabled={busy}>Cancel</button>
        <button type="button" onClick={confirm} style={primaryBtn(busy)} disabled={busy}>Close Day</button>
      </div>
    </Modal>
  )
}

const fieldLabel = { display: 'flex', flexDirection: 'column' as const, gap: 5, fontSize: '0.78rem', fontWeight: 600, color: 'var(--muted)' }

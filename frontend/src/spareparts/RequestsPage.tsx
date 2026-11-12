import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { apiMessage, sparePartsApi, type TransferRow, type TransferStatus } from '../api/spareParts'
import { ErrorBanner, Field, Modal, SkeletonRows, Spinner, card, fmtDateTime, ghostBtn, inputStyle, primaryBtn } from '../shell/ui'
import RequestForm from './RequestForm'
import { useSpareAccess } from './SpareAccessContext'
import { FlashBanner, Hint, TransferBadge, mono, useFlash } from './spareUi'

type Filter = 'ALL' | 'WAITING' | 'ROAD' | 'DONE' | 'CLOSED'

const FILTERS: { key: Filter; label: string; match: (s: TransferStatus) => boolean }[] = [
  { key: 'ALL', label: 'All', match: () => true },
  { key: 'WAITING', label: 'Waiting to be sent', match: (s) => s === 'REQUESTED' },
  { key: 'ROAD', label: 'On the road', match: (s) => s === 'SENT' },
  { key: 'DONE', label: 'Received', match: (s) => s === 'RECEIVED' },
  { key: 'CLOSED', label: 'Declined / cancelled', match: (s) => s === 'DECLINED' || s === 'CANCELLED' },
]

type Action = { kind: 'send' | 'decline' | 'cancel'; row: TransferRow }

/**
 * Branch-to-branch requests. Asking changes no stock. The supplying branch marks the part Sent (its stock goes down and the part
 * is "on the road"); the requesting branch marks it Received (its stock goes up). Each row offers only what the signed-in person
 * may do next — the server decided that, this screen just shows it.
 */
export default function RequestsPage() {
  const { access, can } = useSpareAccess()
  const myBranches = access.branches.filter((b) => access.actBranchIds.includes(b.id))
  const [rows, setRows] = useState<TransferRow[] | null>(null)
  const [filter, setFilter] = useState<Filter>('ALL')
  const [error, setError] = useState('')
  const [action, setAction] = useState<Action | null>(null)
  const [newOpen, setNewOpen] = useState(false)
  const [busyId, setBusyId] = useState<number | null>(null)
  const flash = useFlash()

  const load = useCallback(async () => {
    try {
      const { data } = await sparePartsApi.transfers()
      setRows(data)
      setError('')
    } catch (e) {
      setError(apiMessage(e, 'Could not load the requests.'))
    }
  }, [])

  useEffect(() => { load() }, [load])

  const counts = useMemo(() => {
    const out: Record<Filter, number> = { ALL: 0, WAITING: 0, ROAD: 0, DONE: 0, CLOSED: 0 }
    for (const r of rows ?? []) {
      for (const f of FILTERS) if (f.match(r.status)) out[f.key]++
    }
    return out
  }, [rows])

  const shown = (rows ?? []).filter((r) => FILTERS.find((f) => f.key === filter)!.match(r.status))

  async function receive(r: TransferRow) {
    setBusyId(r.id)
    setError('')
    try {
      await sparePartsApi.receiveTransfer(r.id)
      flash.show(`${r.requestNo} received: ${r.qtySent} × ${r.partCode} added to ${r.toBranchCode}.`)
      load()
    } catch (e) {
      setError(apiMessage(e, 'Could not mark this received.'))
    } finally {
      setBusyId(null)
    }
  }

  function acted(message: string) {
    setAction(null)
    setNewOpen(false)
    flash.show(message)
    load()
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <FlashBanner message={flash.message} />

      <section style={{ ...card, padding: '12px 14px', display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
        {FILTERS.map((f) => {
          const on = f.key === filter
          return (
            <button
              key={f.key}
              type="button"
              onClick={() => setFilter(f.key)}
              aria-pressed={on}
              style={{
                border: `1px solid ${on ? 'var(--navy2)' : 'var(--line)'}`, background: on ? 'var(--navy3)' : '#fff',
                color: on ? 'var(--navy)' : 'var(--ink)', borderRadius: 999, padding: '6px 13px', fontSize: '0.8rem',
                fontWeight: 600, cursor: 'pointer', minHeight: 36,
              }}
            >
              {f.label} <span style={{ color: 'var(--muted)', fontWeight: 700 }}>{counts[f.key]}</span>
            </button>
          )
        })}
        {can('RAISE_REQUEST') && myBranches.length > 0 && (
          <button type="button" onClick={() => setNewOpen(true)} style={{ ...primaryBtn(), minHeight: 38, marginLeft: 'auto' }}>+ New request</button>
        )}
      </section>

      <ErrorBanner message={error} />

      {rows == null ? (
        <SkeletonRows rows={3} height={96} />
      ) : shown.length === 0 ? (
        <section style={{ ...card, color: 'var(--muted)', fontSize: '0.85rem' }}>
          {rows.length === 0 ? 'No branch has asked another branch for a part yet.' : 'No request in this group.'}
        </section>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {shown.map((r) => (
            <RequestCard key={r.id} r={r} busy={busyId === r.id}
              onSend={() => setAction({ kind: 'send', row: r })}
              onReceive={() => receive(r)}
              onDecline={() => setAction({ kind: 'decline', row: r })}
              onCancel={() => setAction({ kind: 'cancel', row: r })} />
          ))}
        </div>
      )}

      {newOpen && (
        <Modal title="Ask another branch for a part" onClose={() => setNewOpen(false)} maxWidth={560}>
          <RequestForm myBranches={myBranches} allBranches={access.branches} onDone={acted} onCancel={() => setNewOpen(false)} />
        </Modal>
      )}
      {action && <ActionModal action={action} onClose={() => setAction(null)} onDone={acted} />}
    </div>
  )
}

function RequestCard(props: { r: TransferRow; busy: boolean; onSend: () => void; onReceive: () => void; onDecline: () => void; onCancel: () => void }) {
  const { r } = props
  return (
    <article style={{ ...card, padding: '14px 16px', display: 'flex', flexDirection: 'column', gap: 10 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
        <span style={{ ...mono, fontWeight: 700, color: 'var(--muted)' }}>{r.requestNo}</span>
        <TransferBadge status={r.status} />
        <span style={{ marginLeft: 'auto', fontSize: '0.76rem', color: 'var(--muted)' }}>{fmtDateTime(r.requestedAt)}</span>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: 14, flexWrap: 'wrap' }}>
        <div style={{ flex: '1 1 240px', minWidth: 0 }}>
          <div style={{ ...mono, fontWeight: 700, color: 'var(--navy)' }}>{r.partCode}</div>
          <div style={{ fontSize: '0.82rem', color: 'var(--ink)' }}>{r.description}</div>
        </div>
        {/* who sends to whom: the arrow is the whole story of the request */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }} aria-label={`${r.fromBranchCode} to ${r.toBranchCode}`}>
          <span style={{ ...mono, fontWeight: 700, background: 'var(--bg)', border: '1px solid var(--line)', borderRadius: 6, padding: '3px 8px' }}>{r.fromBranchCode}</span>
          <span style={{ color: 'var(--faint)' }}>→</span>
          <span style={{ ...mono, fontWeight: 700, background: 'var(--navy3)', border: '1px solid #CFDDF6', borderRadius: 6, padding: '3px 8px', color: 'var(--navy)' }}>{r.toBranchCode}</span>
        </div>
        <div style={{ textAlign: 'right', minWidth: 70 }}>
          <div style={{ fontSize: '1.15rem', fontWeight: 700, lineHeight: 1 }}>{r.qtySent ?? r.qtyRequested}</div>
          <div style={{ fontSize: '0.7rem', color: 'var(--muted)' }}>
            {r.qtySent != null && r.qtySent !== r.qtyRequested ? `of ${r.qtyRequested} asked` : r.qtySent != null ? 'sent' : 'asked for'}
          </div>
        </div>
      </div>

      <div style={{ fontSize: '0.76rem', color: 'var(--muted)', display: 'flex', flexDirection: 'column', gap: 2 }}>
        <span>Asked by {r.requestedByName ?? 'unknown'}{r.jobCardNo && <> · job card <span style={mono}>{r.jobCardNo}</span></>}</span>
        {r.sentAt && <span>Sent by {r.sentByName} · {fmtDateTime(r.sentAt)}</span>}
        {r.receivedAt && <span>Received by {r.receivedByName} · {fmtDateTime(r.receivedAt)}</span>}
        {r.note && <span>Note: {r.note}</span>}
        {r.declineReason && <span style={{ color: 'var(--red)' }}>{r.status === 'DECLINED' ? 'Declined' : 'Cancelled'}: {r.declineReason}</span>}
      </div>

      {(r.canSend || r.canReceive || r.canDecline || r.canCancel) && (
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
          {r.canDecline && <button type="button" style={{ ...ghostBtn, minHeight: 38 }} onClick={props.onDecline}>Decline</button>}
          {r.canCancel && <button type="button" style={{ ...ghostBtn, minHeight: 38 }} onClick={props.onCancel}>Cancel request</button>}
          {r.canSend && <button type="button" style={{ ...primaryBtn(), minHeight: 38 }} onClick={props.onSend}>Mark as sent</button>}
          {r.canReceive && (
            <button type="button" disabled={props.busy} style={{ ...primaryBtn(props.busy), minHeight: 38, display: 'inline-flex', alignItems: 'center', gap: 8 }} onClick={props.onReceive}>
              {props.busy && <Spinner />} Mark as received
            </button>
          )}
        </div>
      )}
    </article>
  )
}

/** Send (with the quantity), decline or cancel (with a reason). */
function ActionModal(props: { action: Action; onClose: () => void; onDone: (message: string) => void }) {
  const { kind, row } = props.action
  const [quantity, setQuantity] = useState(String(row.qtyRequested))
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setSaving(true)
    try {
      if (kind === 'send') {
        const q = Number(quantity)
        if (!Number.isInteger(q) || q < 1 || q > row.qtyRequested) {
          setError(`Send a whole number between 1 and ${row.qtyRequested}.`)
          setSaving(false)
          return
        }
        await sparePartsApi.sendTransfer(row.id, q)
        props.onDone(`${row.requestNo} sent: ${q} × ${row.partCode} left ${row.fromBranchCode} and is on the road to ${row.toBranchCode}.`)
      } else if (kind === 'decline') {
        await sparePartsApi.declineTransfer(row.id, reason.trim())
        props.onDone(`${row.requestNo} declined.`)
      } else {
        await sparePartsApi.cancelTransfer(row.id, reason.trim())
        props.onDone(`${row.requestNo} cancelled.`)
      }
    } catch (err) {
      setError(apiMessage(err, 'Could not do that.'))
    } finally {
      setSaving(false)
    }
  }

  const title = kind === 'send' ? 'Mark as sent' : kind === 'decline' ? 'Decline this request' : 'Cancel this request'
  return (
    <Modal title={title} subtitle={`${row.requestNo} · ${row.partCode} · ${row.fromBranchCode} → ${row.toBranchCode}`} onClose={props.onClose} maxWidth={460}>
      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        {kind === 'send' ? (
          <>
            <Hint>
              Sending takes the part out of <strong>{row.fromBranchCode}</strong>’s stock now. It shows as “on the road” until {row.toBranchCode} marks it received.
            </Hint>
            <Field label="Quantity sent" hint={`${row.qtyRequested} were asked for. You may send fewer.`}>
              <input type="number" min={1} max={row.qtyRequested} step={1} value={quantity} onChange={(e) => setQuantity(e.target.value)} required style={inputStyle} />
            </Field>
          </>
        ) : (
          <Field label="Reason" hint="Required. The other branch will see it.">
            <input value={reason} onChange={(e) => setReason(e.target.value)} required maxLength={500} autoFocus style={inputStyle} />
          </Field>
        )}
        <ErrorBanner message={error} />
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 9 }}>
          <button type="button" style={{ ...ghostBtn, minHeight: 38 }} onClick={props.onClose}>Back</button>
          <button type="submit" disabled={saving} style={{ ...primaryBtn(saving), minHeight: 38, display: 'inline-flex', alignItems: 'center', gap: 8 }}>
            {saving && <Spinner />} {kind === 'send' ? 'Mark as sent' : kind === 'decline' ? 'Decline' : 'Cancel request'}
          </button>
        </div>
      </form>
    </Modal>
  )
}

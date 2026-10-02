import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { customersApi, type Customer, type VehicleRef } from '../api/customers'
import { vehiclesApi, type Vehicle } from '../api/vehicles'
import { jobCardsApi, type JobCardSearchHit } from '../api/jobCards'
import { inputStyle } from '../shell/ui'

/**
 * Branch-scoped pickers shared by the Expense, Receipt and job-card screens (AGENT.md
 * "Linking", rev 56). Each one is a text box with a live dropdown: an existing record is
 * picked (its id is reported), and whatever is typed beyond that is treated as a NEW record
 * that the server creates on save. The server applies the branch scope, so a cashier only
 * ever sees their own branch's customers and job cards.
 */

/** Same normalisation as the server: uppercase, letters and digits only. */
export const normaliseVehicleNo = (raw: string) => raw.toUpperCase().replace(/[^A-Z0-9]/g, '')

type Opt = { key: string | number; label: string; sub?: string; pick: () => void }

const dropdown: CSSProperties = {
  position: 'absolute', zIndex: 30, left: 0, right: 0, top: 'calc(100% + 4px)',
  background: 'var(--card, #fff)', border: '1.5px solid var(--line)', borderRadius: 8,
  boxShadow: '0 8px 24px rgba(0,0,0,.12)', maxHeight: 260, overflowY: 'auto',
}
const optionStyle: CSSProperties = {
  display: 'block', width: '100%', textAlign: 'left', padding: '8px 11px', background: 'transparent',
  border: 0, borderBottom: '1px solid var(--line)', cursor: 'pointer', fontSize: '0.84rem',
}
const hintStyle: CSSProperties = { fontSize: '0.72rem', marginTop: 4, color: 'var(--muted)' }

/**
 * Text box + debounced dropdown. `load` is called with the current text (and again when
 * `refetchKey` changes) whenever the box is focused; stale responses are dropped.
 */
function Combo(props: {
  text: string
  onText: (t: string) => void
  load: (q: string) => Promise<Opt[]>
  refetchKey?: string | number | null
  placeholder?: string
  disabled?: boolean
  readOnly?: boolean
  footer?: (q: string) => ReactNode
  ariaLabel?: string
}) {
  const [open, setOpen] = useState(false)
  const [opts, setOpts] = useState<Opt[]>([])
  const [loading, setLoading] = useState(false)
  const seq = useRef(0)
  const loadRef = useRef(props.load)
  loadRef.current = props.load

  useEffect(() => {
    if (!open || props.disabled || props.readOnly) return
    const mine = ++seq.current
    setLoading(true)
    const t = setTimeout(async () => {
      try {
        const rows = await loadRef.current(props.text)
        if (mine === seq.current) setOpts(rows)
      } catch {
        if (mine === seq.current) setOpts([])
      } finally {
        if (mine === seq.current) setLoading(false)
      }
    }, 200)
    return () => clearTimeout(t)
  }, [props.text, open, props.refetchKey, props.disabled, props.readOnly])

  const locked = props.disabled || props.readOnly
  return (
    <div style={{ position: 'relative' }}>
      <input
        aria-label={props.ariaLabel}
        value={props.text}
        readOnly={props.readOnly}
        disabled={props.disabled}
        placeholder={props.placeholder}
        autoComplete="off"
        onChange={(e) => { props.onText(e.target.value); setOpen(true) }}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onKeyDown={(e) => { if (e.key === 'Escape') setOpen(false) }}
        style={locked ? { ...inputStyle, background: 'var(--bg)' } : inputStyle}
      />
      {open && !locked && (opts.length > 0 || loading || props.footer) && (
        // onMouseDown + preventDefault keeps the input focused, so a click lands before blur closes the list.
        <div style={dropdown} onMouseDown={(e) => e.preventDefault()}>
          {opts.map((o) => (
            <button key={o.key} type="button" style={optionStyle}
              onClick={() => { o.pick(); setOpen(false) }}>
              <div style={{ fontWeight: 600 }}>{o.label}</div>
              {o.sub && <div style={{ fontSize: '0.74rem', color: 'var(--muted)' }}>{o.sub}</div>}
            </button>
          ))}
          {loading && opts.length === 0 && (
            <div style={{ padding: '8px 11px', fontSize: '0.8rem', color: 'var(--muted)' }}>Searching…</div>
          )}
          {props.footer?.(props.text)}
        </div>
      )}
    </div>
  )
}

/* ------------------------------------------------------------------ customer */

export function CustomerCombobox(props: {
  name: string
  customerId: number | null
  /** Called on every keystroke (id = null → new/unknown) and on pick (id = the customer's). */
  onChange: (name: string, customerId: number | null, picked?: Customer) => void
  /** Locked by a chosen job card / existing link. */
  locked?: boolean
  placeholder?: string
}) {
  const typed = props.name.trim()
  return (
    <div>
      <Combo
        ariaLabel="Customer name"
        text={props.name}
        readOnly={props.locked}
        placeholder={props.placeholder ?? 'Search or type a customer name'}
        onText={(t) => props.onChange(t, null)}
        load={async (q) => {
          const { data } = await customersApi.search(q.trim())
          return data.map((c) => ({
            key: c.id,
            label: c.name,
            sub: [c.phone, c.vehicles.map((v) => v.vehicleNo).join(', ')].filter(Boolean).join(' · ') || undefined,
            pick: () => props.onChange(c.name, c.id, c),
          }))
        }}
      />
      {typed && props.customerId == null && !props.locked && (
        <div style={{ ...hintStyle, color: 'var(--amber)' }}>New customer — will be added when you save.</div>
      )}
      {props.customerId != null && (
        <div style={hintStyle}>Existing customer linked.</div>
      )}
    </div>
  )
}

/* ------------------------------------------------------------------ vehicle */

export function VehicleCombobox(props: {
  vehicleNo: string
  vehicleId: number | null
  /** The customer that owns / will own the vehicle; null while none is known yet. */
  customerId: number | null
  onChange: (vehicleNo: string, vehicleId: number | null) => void
  /** An existing vehicle was picked or typed that belongs to a (possibly different) customer. */
  onOwnerFound?: (vehicle: Vehicle) => void
  locked?: boolean
  /** Shown when a typed number will become a new vehicle. */
  newHint?: string
  /** Hide the "new vehicle" hint (e.g. an edited document whose existing vehicle id is not known here). */
  suppressHint?: boolean
}) {
  const typed = normaliseVehicleNo(props.vehicleNo)
  return (
    <div>
      <Combo
        ariaLabel="Vehicle number"
        text={props.vehicleNo}
        readOnly={props.locked}
        refetchKey={props.customerId}
        placeholder={props.customerId ? "Pick this customer's vehicle or type a new number" : 'Type a vehicle number (e.g. OD05CA4177)'}
        onText={(t) => props.onChange(t, null)}
        load={async (q): Promise<Opt[]> => {
          if (props.customerId != null) {
            const { data } = await customersApi.vehicles(props.customerId, q.trim())
            return data.map((v: VehicleRef) => ({
              key: v.id, label: v.vehicleNo, pick: () => props.onChange(v.vehicleNo, v.id),
            }))
          }
          // No customer yet: an exact number that already exists brings its owner along.
          const n = normaliseVehicleNo(q)
          if (n.length < 4) return []
          try {
            const { data: v } = await vehiclesApi.byNumber(n)
            return [{
              key: v.id, label: v.vehicleNo, sub: v.customerName ? `Registered to ${v.customerName}` : undefined,
              pick: () => { props.onChange(v.vehicleNo, v.id); props.onOwnerFound?.(v) },
            }]
          } catch {
            return [] // 404 = a new number
          }
        }}
      />
      {typed && props.vehicleId == null && !props.locked && !props.suppressHint && (
        <div style={{ ...hintStyle, color: 'var(--amber)' }}>
          {props.newHint ?? (props.customerId
            ? 'New vehicle — will be added to this customer when you save.'
            : 'New vehicle number — kept on the job card until a customer is linked.')}
        </div>
      )}
    </div>
  )
}

/* ------------------------------------------------------------------ job card */

/** The Ooriba ID is the DAMS-Receive-ID; a job card with no numbered receipt yet falls back to its reference. */
export const jobCardHitLabel = (h: JobCardSearchHit) =>
  [h.receiveDocumentNos?.[0] ?? h.reference, h.customerName ?? 'no customer yet', h.vehicleNo].filter(Boolean).join(' · ')

/** Second line: whatever the first line did not already show. */
const jobCardHitSub = (h: JobCardSearchHit) =>
  [
    h.receiveDocumentNos?.length ? `Job card ${h.reference}` : null,
    h.receiveDocumentNos && h.receiveDocumentNos.length > 1 ? `Also ${h.receiveDocumentNos.slice(1).join(', ')}` : null,
    h.dbmId ? `DBM ${h.dbmId}` : null,
    h.invoiceNo ? `Invoice ${h.invoiceNo}` : null,
  ].filter(Boolean).join(' · ') || undefined

export function JobCardSearch(props: {
  selected: JobCardSearchHit | null
  /** Narrow the search to one customer / vehicle when they are already chosen. */
  customerId?: number | null
  vehicleId?: number | null
  onPick: (hit: JobCardSearchHit | null) => void
  disabled?: boolean
  /** Renders a trailing "＋ New job card" row in the dropdown. */
  onNew?: () => void
}) {
  const [text, setText] = useState('')
  if (props.selected) {
    return (
      <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
        <div style={{ ...inputStyle, background: 'var(--bg)', flex: 1, fontWeight: 600 }}>
          {jobCardHitLabel(props.selected)}
          {props.selected.dbmId ? <span style={{ color: 'var(--muted)', fontWeight: 400 }}> · DBM {props.selected.dbmId}</span> : null}
        </div>
        {!props.disabled && (
          <button type="button" onClick={() => { props.onPick(null); setText('') }}
            style={{ border: '1.5px solid var(--line)', borderRadius: 8, background: 'transparent', padding: '8px 10px', cursor: 'pointer' }}>
            Clear
          </button>
        )}
      </div>
    )
  }
  return (
    <Combo
      ariaLabel="Job card"
      text={text}
      onText={setText}
      disabled={props.disabled}
      refetchKey={`${props.customerId ?? ''}-${props.vehicleId ?? ''}`}
      placeholder="Search by Ooriba ID (DAMS-Receive-ID), customer, vehicle no, DBM ID or invoice"
      load={async (q) => {
        const { data } = await jobCardsApi.search({
          q: q.trim() || undefined,
          customerId: props.customerId ?? undefined,
          vehicleId: props.vehicleId ?? undefined,
        })
        return data.map((h) => ({
          key: h.id,
          label: jobCardHitLabel(h),
          sub: jobCardHitSub(h),
          pick: () => { props.onPick(h); setText('') },
        }))
      }}
      footer={props.onNew ? () => (
        <button type="button" style={{ ...optionStyle, color: 'var(--navy, #1d3f8f)', fontWeight: 700 }}
          onClick={() => props.onNew?.()}>
          ＋ New job card
        </button>
      ) : undefined}
    />
  )
}

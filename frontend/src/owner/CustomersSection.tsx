import { useEffect, useState, type FormEvent } from 'react'
import { customersApi, type Customer } from '../api/customers'
import { vehiclesApi } from '../api/vehicles'
import {
  Badge, ErrorBanner, Field, Modal, TextInput,
  card, cardTitle, ghostBtn, inputStyle, primaryBtn, td, th,
} from '../shell/ui'

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

/** Same normalisation as the server: uppercase, letters and digits only. */
const normaliseVehicleNo = (raw: string) => raw.toUpperCase().replace(/[^A-Z0-9]/g, '')
const digitsOf = (s: string) => s.replace(/\D/g, '')

/**
 * Customers master (rev 77, Owner). The whole organisation's customers with their mobile,
 * vehicles and address, searchable by any of them. Address is optional and no form asks for
 * it yet — it is kept here only. Vehicles are never deleted (their history is kept); the
 * Owner can correct a number or add one.
 */
export default function CustomersSection() {
  const [rows, setRows] = useState<Customer[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  const [modal, setModal] = useState<{ editing: Customer | null } | null>(null)

  async function load() {
    setLoading(true)
    setError('')
    try {
      const { data } = await customersApi.master()
      setRows(data)
    } catch (e) {
      setError(apiError(e, 'Could not load customers.'))
    } finally {
      setLoading(false)
    }
  }
  useEffect(() => { load() }, [])

  const needle = query.trim().toLowerCase()
  const needleDigits = digitsOf(query)
  const needleVehicle = normaliseVehicleNo(query)
  const shown = needle === '' ? rows : rows.filter((c) =>
    c.name.toLowerCase().includes(needle)
    || (c.address ?? '').toLowerCase().includes(needle)
    || (needleDigits.length > 0 && digitsOf(c.phone ?? '').includes(needleDigits))
    || (needleVehicle.length > 0 && c.vehicles.some((v) => v.vehicleNo.includes(needleVehicle))))

  return (
    <section style={card} aria-label="Customers">
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 14, flexWrap: 'wrap' }}>
        <h2 style={{ ...cardTitle, marginBottom: 0, flex: 1 }}>Customers</h2>
        <button style={{ ...primaryBtn(), minHeight: 36 }} onClick={() => setModal({ editing: null })}>+ Add</button>
      </div>
      <ErrorBanner message={error} />
      <input
        type="search"
        aria-label="Search customers"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Search by name, mobile, vehicle number or address"
        style={{ ...inputStyle, marginBottom: 12, maxWidth: 420 }}
      />
      <div style={{ overflowX: 'auto', WebkitOverflowScrolling: 'touch' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 720, fontSize: '0.84rem' }}>
          <thead>
            <tr>
              <th style={th}>Name</th><th style={th}>Mobile</th><th style={th}>Vehicles</th>
              <th style={th}>Address</th><th style={th}></th>
            </tr>
          </thead>
          <tbody>
            {shown.map((c) => (
              <tr key={c.id}>
                <td style={td}>{c.name}</td>
                <td style={td}>{c.phone ?? '—'}</td>
                <td style={td}>
                  {c.vehicles.length === 0
                    ? '—'
                    : c.vehicles.map((v) => (
                      <Badge key={v.id}>{v.vehicleNo}</Badge>
                    ))}
                </td>
                <td style={td}>{c.address ?? '—'}</td>
                <td style={{ ...td, textAlign: 'right' }}>
                  <button style={{ ...ghostBtn, minHeight: 36, padding: '4px 12px' }} onClick={() => setModal({ editing: c })}>Edit</button>
                </td>
              </tr>
            ))}
            {!loading && shown.length === 0 && (
              <tr><td style={td} colSpan={5}>{rows.length === 0 ? 'Nothing here yet.' : 'No customer matches your search.'}</td></tr>
            )}
          </tbody>
        </table>
      </div>
      {modal && (
        <CustomerModal
          editing={modal.editing}
          allCustomers={rows}
          onClose={() => setModal(null)}
          onSaved={() => { setModal(null); load() }}
        />
      )}
    </section>
  )
}

type VehicleDraft = { id: number | null; original: string; value: string; moveTo?: { id: number; name: string } | null }

function CustomerModal(props: { editing: Customer | null; allCustomers: Customer[]; onClose: () => void; onSaved: () => void }) {
  const { editing } = props
  const [name, setName] = useState(editing?.name ?? '')
  const [phone, setPhone] = useState(editing?.phone ?? '')
  const [address, setAddress] = useState(editing?.address ?? '')
  const [vehicles, setVehicles] = useState<VehicleDraft[]>(
    (editing?.vehicles ?? []).map((v) => ({ id: v.id, original: v.vehicleNo, value: v.vehicleNo })))
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  // which vehicle row has its "move to another customer" search open, and what was typed
  const [moving, setMoving] = useState<number | null>(null)
  const [moveQuery, setMoveQuery] = useState('')
  const [moveDigits, moveText] = [digitsOf(moveQuery), moveQuery.trim().toLowerCase()]
  const moveMatches = moveText === '' ? [] : props.allCustomers
    .filter((c) => c.id !== editing?.id)
    .filter((c) => c.name.toLowerCase().includes(moveText) || (moveDigits.length > 0 && digitsOf(c.phone ?? '').includes(moveDigits)))
    .slice(0, 6)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError('')
    setSaving(true)
    try {
      const body = { name: name.trim(), phone: phone.trim() === '' ? undefined : phone.trim(), address: address.trim() }
      const customerId = editing == null ? (await customersApi.create(body)).data.id : (await customersApi.update(editing.id, body)).data.id
      for (const v of vehicles) {
        const next = normaliseVehicleNo(v.value)
        if (next === '') continue
        if (v.id == null) {
          await vehiclesApi.create({ customerId, vehicleNo: next })
        } else {
          if (next !== v.original) await vehiclesApi.rename(v.id, next)
          if (v.moveTo) await vehiclesApi.move(v.id, v.moveTo.id)
        }
      }
      props.onSaved()
    } catch (err) {
      setError(apiError(err, 'Could not save this customer.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title={editing == null ? 'Add customer' : 'Edit customer'} onClose={props.onClose} maxWidth={520}>
      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <ErrorBanner message={error} />
        <Field label="Name">
          <TextInput value={name} onChange={setName} placeholder="Customer name" required maxLength={160} />
        </Field>
        <Field label="Mobile (optional)">
          <TextInput value={phone} onChange={setPhone} placeholder="Mobile number" maxLength={32} />
        </Field>
        <Field label="Address (optional)" hint="Not asked on any form yet — kept here for now.">
          <textarea
            value={address}
            onChange={(e) => setAddress(e.target.value)}
            maxLength={500}
            rows={3}
            placeholder="Street, town, PIN"
            style={{ ...inputStyle, resize: 'vertical', fontFamily: 'inherit' }}
          />
        </Field>
        <div>
          <div style={{ fontSize: '0.76rem', fontWeight: 600, color: 'var(--muted)', marginBottom: 5 }}>Vehicles</div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {vehicles.map((v, i) => (
              <div key={v.id ?? `new-${i}`}>
                <div style={{ display: 'flex', gap: 6 }}>
                  <input
                    aria-label={v.id == null ? 'New vehicle number' : `Vehicle ${v.original}`}
                    value={v.value}
                    maxLength={24}
                    placeholder="Vehicle number, e.g. OD05CA4177"
                    onChange={(e) => setVehicles((cur) => cur.map((x, j) => (j === i ? { ...x, value: e.target.value } : x)))}
                    style={{ ...inputStyle, flex: 1 }}
                  />
                  {v.id != null && !v.moveTo && (
                    <button
                      type="button"
                      aria-label={`Move ${v.original} to another customer`}
                      style={{ ...ghostBtn, minHeight: 36, whiteSpace: 'nowrap' }}
                      onClick={() => { setMoving(moving === i ? null : i); setMoveQuery('') }}
                    >
                      Move…
                    </button>
                  )}
                </div>
                {v.moveTo && (
                  <div style={{ fontSize: '0.78rem', color: 'var(--amber)', marginTop: 4 }}>
                    Will move to <strong>{v.moveTo.name}</strong> when you save. Past records stay with {editing?.name}.{' '}
                    <button type="button" style={{ ...ghostBtn, minHeight: 28, padding: '2px 8px' }}
                      onClick={() => setVehicles((cur) => cur.map((x, j) => (j === i ? { ...x, moveTo: null } : x)))}>
                      Undo
                    </button>
                  </div>
                )}
                {moving === i && (
                  <div style={{ marginTop: 6 }}>
                    <input
                      autoFocus
                      aria-label="Search customer to move to"
                      value={moveQuery}
                      onChange={(e) => setMoveQuery(e.target.value)}
                      placeholder="Search the new owner by name or mobile"
                      style={inputStyle}
                    />
                    {moveMatches.map((c) => (
                      <button
                        key={c.id}
                        type="button"
                        style={{ ...ghostBtn, display: 'block', width: '100%', textAlign: 'left', marginTop: 4, minHeight: 34 }}
                        onClick={() => {
                          setVehicles((cur) => cur.map((x, j) => (j === i ? { ...x, moveTo: { id: c.id, name: c.name } } : x)))
                          setMoving(null)
                        }}
                      >
                        {c.name}{c.phone ? ` · ${c.phone}` : ''}
                      </button>
                    ))}
                    {moveText !== '' && moveMatches.length === 0 && (
                      <div style={{ fontSize: '0.78rem', color: 'var(--muted)', marginTop: 4 }}>No other customer matches.</div>
                    )}
                  </div>
                )}
              </div>
            ))}
            {vehicles.length === 0 && <span style={{ fontSize: '0.8rem', color: 'var(--muted)' }}>No vehicles yet.</span>}
          </div>
          <button
            type="button"
            style={{ ...ghostBtn, minHeight: 34, marginTop: 8 }}
            onClick={() => setVehicles((cur) => [...cur, { id: null, original: '', value: '' }])}
          >
            + Add vehicle
          </button>
          <div style={{ fontSize: '0.72rem', color: 'var(--faint)', marginTop: 4 }}>
            A vehicle number belongs to one customer only. Vehicles are never deleted — their history is kept.
          </div>
        </div>
        <button type="submit" disabled={saving || name.trim() === ''} style={{ ...primaryBtn(saving || name.trim() === ''), minHeight: 38 }}>
          {saving ? 'Saving…' : editing == null ? 'Add customer' : 'Save changes'}
        </button>
      </form>
    </Modal>
  )
}

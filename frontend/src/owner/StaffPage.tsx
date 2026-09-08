import { useEffect, useState } from 'react'
import { staffApi, type StaffEntry, type StaffMember } from '../api/staff'
import { card, ErrorBanner, inr, Badge, ghostBtn, primaryBtn, inputStyle, th, td, fmtDate } from '../shell/ui'
import HelpButton from '../help/HelpButton'

/**
 * Staff advances (FEAT-44): advances out, recoveries in, outstanding derived
 * per staff. Entries are never edited or deleted — a wrong entry is fixed by
 * an opposing entry, like cash. A recovery can never exceed the outstanding.
 */
export default function StaffPage() {
  const [members, setMembers] = useState<StaffMember[] | null>(null)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [entries, setEntries] = useState<StaffEntry[] | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [kind, setKind] = useState('ADVANCE')
  const [amount, setAmount] = useState('')
  const [txnDate, setTxnDate] = useState(() => new Date().toISOString().slice(0, 10))
  const [note, setNote] = useState('')

  async function load(select?: number) {
    setError('')
    try {
      const { data } = await staffApi.members()
      setMembers(data)
      const pick = select ?? selectedId ?? data[0]?.id ?? null
      setSelectedId(pick)
      if (pick != null) {
        const { data: e } = await staffApi.entries(pick)
        setEntries(e)
      } else setEntries([])
    } catch (e) {
      setError(apiError(e, 'Could not load staff advances.'))
    }
  }

  useEffect(() => { load() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  async function addMember() {
    if (!name.trim()) return
    setBusy(true)
    setError('')
    try {
      const { data } = await staffApi.add({ name: name.trim(), phone: phone || undefined })
      setName('')
      setPhone('')
      await load(data.id)
    } catch (e) {
      setError(apiError(e, 'Could not add that staff member.'))
    } finally {
      setBusy(false)
    }
  }

  async function record() {
    if (selectedId == null || !amount) return
    setBusy(true)
    setError('')
    try {
      await staffApi.record(selectedId, { kind, amount: Number(amount), txnDate, note: note || undefined })
      setAmount('')
      setNote('')
      await load(selectedId)
    } catch (e) {
      setError(apiError(e, 'Could not record that entry.'))
    } finally {
      setBusy(false)
    }
  }

  async function deactivate(id: number) {
    setError('')
    try {
      await staffApi.deactivate(id)
      await load()
    } catch (e) {
      setError(apiError(e, 'Could not deactivate.'))
    }
  }

  const selected = members?.find((m) => m.id === selectedId)

  return (
    <div style={{ maxWidth: 1050, margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
        <h1 style={{ fontSize: '1.3rem', color: 'var(--navy)', margin: 0 }}>Staff advances</h1>
        <HelpButton slug="staff-advances" />
      </div>
      <p style={{ fontSize: '0.82rem', color: 'var(--muted)', marginTop: 0 }}>
        Given vs recovered per staff — outstanding is derived, never typed, so it can't drift.
      </p>
      <ErrorBanner message={error} />

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(280px, 340px) 1fr', gap: 14 }}>
        <div style={card}>
          <div style={{ fontWeight: 700, padding: '10px 10px 6px' }}>Staff</div>
          {(members ?? []).map((m) => (
            <button
              key={m.id}
              type="button"
              onClick={() => { setSelectedId(m.id); staffApi.entries(m.id).then(({ data }) => setEntries(data)).catch((e) => setError(apiError(e, 'Could not load entries.'))) }}
              style={{
                display: 'block', width: '100%', textAlign: 'left', border: 'none', cursor: 'pointer',
                background: m.id === selectedId ? 'var(--navy3)' : 'transparent',
                borderLeft: `3px solid ${m.id === selectedId ? 'var(--navy)' : 'transparent'}`,
                padding: '10px 12px', borderBottom: '1px solid var(--line)',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <span style={{ fontWeight: 700 }}>{m.name}</span>
                {!m.active && <Badge tone="gray">inactive</Badge>}
                <span style={{ marginLeft: 'auto', fontWeight: 800, color: m.outstanding > 0 ? 'var(--amber)' : 'var(--faint)' }}>
                  {inr(m.outstanding)}
                </span>
              </div>
              {m.phone && <div style={{ fontSize: '0.74rem', color: 'var(--faint)' }}>{m.phone}</div>}
            </button>
          ))}
          <div style={{ padding: 10, display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            <input value={name} onChange={(e) => setName(e.target.value)} placeholder="New staff name" style={{ ...inputStyle, flex: 1, minWidth: 120 }} />
            <input value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="Phone" style={{ ...inputStyle, width: 120 }} />
            <button type="button" onClick={addMember} disabled={busy || !name.trim()} style={primaryBtn(busy)}>Add</button>
          </div>
        </div>

        <div style={card}>
          <div style={{ fontWeight: 700, padding: '10px 10px 6px', display: 'flex', alignItems: 'center', gap: 8 }}>
            {selected ? `Ledger — ${selected.name}` : 'Ledger'}
            {selected && !selected.active && <Badge tone="gray">inactive</Badge>}
            {selected?.active && (
              <button type="button" onClick={() => deactivate(selected.id)} style={{ ...ghostBtn, marginLeft: 'auto' }}>Deactivate</button>
            )}
          </div>
          {selected?.active && (
            <div style={{ padding: '0 10px 10px', display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              <select value={kind} onChange={(e) => setKind(e.target.value)} style={inputStyle}>
                <option value="ADVANCE">Advance out</option>
                <option value="RECOVERY">Recovery in</option>
              </select>
              <input value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Amount" inputMode="decimal" style={{ ...inputStyle, width: 120 }} />
              <input value={txnDate} onChange={(e) => setTxnDate(e.target.value)} type="date" style={inputStyle} />
              <input value={note} onChange={(e) => setNote(e.target.value)} placeholder="Note" style={{ ...inputStyle, flex: 1, minWidth: 140 }} />
              <button type="button" onClick={record} disabled={busy || !amount} style={primaryBtn(busy)}>Record</button>
            </div>
          )}
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead><tr><th style={th}>Date</th><th style={th}>Kind</th><th style={th}>Amount</th><th style={th}>Note</th></tr></thead>
            <tbody>
              {(entries ?? []).map((e) => (
                <tr key={e.id}>
                  <td style={{ ...td, whiteSpace: 'nowrap' }}>{fmtDate(e.txnDate)}</td>
                  <td style={td}><Badge tone={e.kind === 'ADVANCE' ? 'amber' : 'green'}>{e.kind === 'ADVANCE' ? 'Advance out' : 'Recovery in'}</Badge></td>
                  <td style={{ ...td, fontWeight: 700 }}>{inr(e.amount)}</td>
                  <td style={{ ...td, fontSize: '0.8rem' }}>{e.note ?? '—'}</td>
                </tr>
              ))}
              {entries?.length === 0 && <tr><td colSpan={4} style={{ ...td, color: 'var(--faint)' }}>No entries yet.</td></tr>}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

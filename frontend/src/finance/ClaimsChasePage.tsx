import { useEffect, useState } from 'react'
import { claimActionsApi, type ClaimAction } from '../api/claimActions'
import { card, ErrorBanner, Badge, ghostBtn, primaryBtn, inputStyle, th, td, fmtDate } from '../shell/ui'
import HelpButton from '../help/HelpButton'

/**
 * Claim chase (FEAT-38): every open claim's next step with an owner and a
 * date. Aging buckets show old claims; this page makes sure none die from
 * neglect. Completing is explicit — history is kept for write-off time.
 */
export default function ClaimsChasePage() {
  const [items, setItems] = useState<ClaimAction[] | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [overdueOnly, setOverdueOnly] = useState(false)

  const [jobCardId, setJobCardId] = useState('')
  const [action, setAction] = useState('')
  const [dueDate, setDueDate] = useState('')

  async function load() {
    setError('')
    try {
      const { data } = await claimActionsApi.open()
      setItems(data)
    } catch (e) {
      setError(apiError(e, 'Could not load claim actions.'))
    }
  }

  useEffect(() => { load() }, [])

  async function create() {
    if (!jobCardId || !action.trim() || !dueDate) return
    setBusy(true)
    setError('')
    try {
      await claimActionsApi.create({ jobCardId: Number(jobCardId), action: action.trim(), dueDate })
      setJobCardId('')
      setAction('')
      setDueDate('')
      await load()
    } catch (e) {
      setError(apiError(e, 'Could not record the action.'))
    } finally {
      setBusy(false)
    }
  }

  async function complete(id: number) {
    setError('')
    try {
      await claimActionsApi.complete(id)
      await load()
    } catch (e) {
      setError(apiError(e, 'Could not complete that action.'))
    }
  }

  const visible = (items ?? []).filter((a) => !overdueOnly || a.overdue)

  return (
    <div style={{ maxWidth: 1000, margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
        <h1 style={{ fontSize: '1.3rem', color: 'var(--navy)', margin: 0 }}>Claims chase</h1>
        <HelpButton slug="chasing-claims" />
      </div>
      <p style={{ fontSize: '0.82rem', color: 'var(--muted)', marginTop: 0 }}>
        The next step on every open claim — owner, date, done. Claims die from neglect, not age.
      </p>
      <ErrorBanner message={error} />

      <div style={{ ...card, marginBottom: 14, padding: 14 }}>
        <div style={{ fontWeight: 700, marginBottom: 8 }}>Record next step</div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
          <input value={jobCardId} onChange={(e) => setJobCardId(e.target.value)} placeholder="Job card id" inputMode="numeric" style={{ ...inputStyle, width: 120 }} />
          <input value={action} onChange={(e) => setAction(e.target.value)} placeholder="e.g. Call Eicher for LR copy" style={{ ...inputStyle, flex: 1, minWidth: 220 }} />
          <input value={dueDate} onChange={(e) => setDueDate(e.target.value)} type="date" style={inputStyle} />
          <button type="button" onClick={create} disabled={busy || !jobCardId || !action.trim() || !dueDate} style={primaryBtn(busy)}>Save</button>
        </div>
      </div>

      <div style={card}>
        <div style={{ display: 'flex', alignItems: 'center', padding: '10px 10px 0' }}>
          <span style={{ fontWeight: 700 }}>Open actions</span>
          <label style={{ marginLeft: 'auto', fontSize: '0.8rem', display: 'flex', alignItems: 'center', gap: 6 }}>
            <input type="checkbox" checked={overdueOnly} onChange={(e) => setOverdueOnly(e.target.checked)} />
            Overdue only
          </label>
        </div>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead><tr><th style={th}>Claim</th><th style={th}>Next step</th><th style={th}>Owner</th><th style={th}>Due</th><th style={th}></th></tr></thead>
          <tbody>
            {visible.map((a) => (
              <tr key={a.id}>
                <td style={td}>{a.jobCardReference}</td>
                <td style={{ ...td, fontSize: '0.85rem' }}>{a.action}</td>
                <td style={td}>{a.ownerName ?? '—'}</td>
                <td style={td}>{fmtDate(a.dueDate)} {a.overdue && <div><Badge tone="red">overdue</Badge></div>}</td>
                <td style={td}><button type="button" onClick={() => complete(a.id)} style={ghostBtn}>Done</button></td>
              </tr>
            ))}
            {visible.length === 0 && <tr><td colSpan={5} style={{ ...td, color: 'var(--faint)' }}>No open actions — every claim has its next step covered, or none recorded.</td></tr>}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

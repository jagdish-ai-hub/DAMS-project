import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { estimatesApi, type Estimate } from '../api/estimates'
import { useAuth } from '../auth/useAuth'
import { card, ErrorBanner, inr, Badge, ghostBtn, primaryBtn, inputStyle, fmtDateTime } from '../shell/ui'
import HelpButton from '../help/HelpButton'

/**
 * Estimates (FEAT-48): the number the customer agreed to, before the work.
 * The counter drafts line quotes; the FM/Owner approves (big numbers need a
 * name behind them); billing shows variance vs the approved figure.
 * Re-quoting supersedes — history survives.
 */
export default function EstimatesPage() {
  const { user } = useAuth()
  const [params] = useSearchParams()
  const jobCardId = params.get('jobCardId') ? Number(params.get('jobCardId')) : null
  const canDecide = user?.role === 'FINANCE_MANAGER' || user?.role === 'OWNER'

  const [items, setItems] = useState<Estimate[] | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [lines, setLines] = useState<{ description: string; amount: string }[]>([{ description: '', amount: '' }])
  const [note, setNote] = useState('')

  async function load() {
    if (jobCardId == null) return
    setError('')
    try {
      const { data } = await estimatesApi.forJobCard(jobCardId)
      setItems(data)
    } catch (e) {
      setError(apiError(e, 'Could not load estimates.'))
    }
  }

  useEffect(() => { load() }, [jobCardId]) // eslint-disable-line react-hooks/exhaustive-deps

  async function create() {
    if (jobCardId == null) return
    const clean = lines
      .filter((l) => l.description.trim() && l.amount)
      .map((l) => ({ description: l.description.trim(), amount: Number(l.amount) }))
    if (clean.length === 0) return
    setBusy(true)
    setError('')
    try {
      await estimatesApi.create({ jobCardId, lines: clean })
      setLines([{ description: '', amount: '' }])
      await load()
    } catch (e) {
      setError(apiError(e, 'Could not save the estimate.'))
    } finally {
      setBusy(false)
    }
  }

  async function decide(id: number, approve: boolean) {
    setError('')
    try {
      if (approve) await estimatesApi.approve(id, note || undefined)
      else {
        if (!note.trim()) {
          setError('A rejection needs a reason — the counter must know what to re-quote.')
          return
        }
        await estimatesApi.reject(id, note)
      }
      setNote('')
      await load()
    } catch (e) {
      setError(apiError(e, 'Could not record the decision.'))
    }
  }

  if (jobCardId == null) {
    return (
      <div style={{ maxWidth: 720, margin: '0 auto' }}>
        <h1 style={{ fontSize: '1.3rem', color: 'var(--navy)' }}>Estimates</h1>
        <p style={{ color: 'var(--muted)' }}>Open a job card's history and choose Quote — estimates live on the job, not on their own.</p>
      </div>
    )
  }

  return (
    <div style={{ maxWidth: 860, margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
        <h1 style={{ fontSize: '1.3rem', color: 'var(--navy)', margin: 0 }}>Estimate — job #{jobCardId}</h1>
        <HelpButton slug="quoting-a-job" />
      </div>
      <p style={{ fontSize: '0.82rem', color: 'var(--muted)', marginTop: 0 }}>
        Quote before work starts. The final bill shows variance vs the approved figure.
      </p>
      <ErrorBanner message={error} />

      <div style={{ ...card, marginBottom: 14, padding: 14 }}>
        <div style={{ fontWeight: 700, marginBottom: 8 }}>New quote (supersedes any live one)</div>
        {lines.map((l, i) => (
          <div key={i} style={{ display: 'flex', gap: 8, marginBottom: 6 }}>
            <input
              value={l.description}
              onChange={(e) => setLines((prev) => prev.map((x, j) => (j === i ? { ...x, description: e.target.value } : x)))}
              placeholder="e.g. Clutch plate + labour"
              style={{ ...inputStyle, flex: 1 }}
            />
            <input
              value={l.amount}
              onChange={(e) => setLines((prev) => prev.map((x, j) => (j === i ? { ...x, amount: e.target.value } : x)))}
              placeholder="Amount"
              inputMode="decimal"
              style={{ ...inputStyle, width: 130 }}
            />
          </div>
        ))}
        <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
          <button type="button" onClick={() => setLines((prev) => [...prev, { description: '', amount: '' }])} style={ghostBtn}>+ Line</button>
          <button type="button" onClick={create} disabled={busy} style={primaryBtn(busy)}>Save quote</button>
        </div>
      </div>

      {(items ?? []).map((e) => (
        <div key={e.id} style={{ ...card, marginBottom: 12, padding: 14 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
            <Badge tone={e.status === 'APPROVED' ? 'green' : e.status === 'REJECTED' ? 'red' : e.status === 'SUPERSEDED' ? 'gray' : 'amber'}>
              {e.status}
            </Badge>
            <strong>{inr(e.total)}</strong>
            {e.invoiceAmount != null && e.varianceVsInvoice != null && (
              <span style={{ fontSize: '0.8rem', color: e.varianceVsInvoice > 0 ? 'var(--red)' : 'var(--green)' }}>
                billed {inr(e.invoiceAmount)} ({e.varianceVsInvoice > 0 ? '+' : ''}{inr(e.varianceVsInvoice)} vs quote)
              </span>
            )}
            <span style={{ marginLeft: 'auto', fontSize: '0.72rem', color: 'var(--faint)' }}>{fmtDateTime(e.createdAt)}</span>
          </div>
          <table style={{ width: '100%', borderCollapse: 'collapse', marginBottom: 8 }}>
            <tbody>
              {e.lines.map((l) => (
                <tr key={l.lineNo}>
                  <td style={{ padding: '4px 0', fontSize: '0.66rem', color: 'var(--faint)', width: 30 }}>L{l.lineNo}</td>
                  <td style={{ padding: '4px 0', fontSize: '0.85rem' }}>{l.description}</td>
                  <td style={{ padding: '4px 0', fontSize: '0.85rem', textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>{inr(l.amount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {e.decisionNote && <div style={{ fontSize: '0.78rem', color: 'var(--muted)', marginBottom: 8 }}>Note: {e.decisionNote}</div>}
          {canDecide && e.status === 'DRAFT' && (
            <div style={{ display: 'flex', gap: 8 }}>
              <input value={note} onChange={(ev) => setNote(ev.target.value)} placeholder="Decision note (required to reject)" style={{ ...inputStyle, flex: 1 }} />
              <button type="button" onClick={() => decide(e.id, true)} style={primaryBtn(false)}>Approve</button>
              <button type="button" onClick={() => decide(e.id, false)} style={ghostBtn}>Reject</button>
            </div>
          )}
        </div>
      ))}
      {items?.length === 0 && <p style={{ color: 'var(--faint)' }}>No quotes yet on this job.</p>}
    </div>
  )
}

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

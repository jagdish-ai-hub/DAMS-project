import { useCallback, useEffect, useState } from 'react'
import { followupsApi, type Defaulter, type Followup } from '../api/followups'
import { messagesApi } from '../api/messaging'
import { card, ErrorBanner, inr, Badge, ghostBtn, primaryBtn, inputStyle, th, td, fmtDate } from '../shell/ui'
import HelpButton from '../help/HelpButton'

/**
 * Receivables (FEAT-35/46): live follow-ups with due dates and promises, plus
 * the defaulter view ranked by exposure. The dashboard outstanding list shows
 * balances; this page owns the next step. Reminders send through the
 * templated message channel (FEAT-36).
 */
export default function ReceivablesPage() {
  const [tab, setTab] = useState<'followups' | 'defaulters'>('followups')
  const [items, setItems] = useState<Followup[] | null>(null)
  const [defaulters, setDefaulters] = useState<Defaulter[] | null>(null)
  const [overdueOnly, setOverdueOnly] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const [docId, setDocId] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [promise, setPromise] = useState('')

  const load = useCallback(async () => {
    setError('')
    try {
      const [f, d] = await Promise.all([
        followupsApi.list(overdueOnly),
        tab === 'defaulters' ? followupsApi.defaulters() : Promise.resolve({ data: defaulters ?? [] }),
      ])
      setItems(f.data)
      if (tab === 'defaulters') setDefaulters(d.data)
    } catch (e) {
      setError(apiError(e, 'Could not load receivables.'))
    }
  }, [overdueOnly, tab]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { load() }, [load])

  async function create() {
    if (!docId || !dueDate) return
    setBusy(true)
    setError('')
    try {
      await followupsApi.open({ receiveDocumentId: Number(docId), dueDate, promiseNote: promise || undefined })
      setDocId('')
      setDueDate('')
      setPromise('')
      await load()
    } catch (e) {
      setError(apiError(e, 'Could not open the follow-up.'))
    } finally {
      setBusy(false)
    }
  }

  async function closeItem(id: number) {
    setError('')
    try {
      await followupsApi.close(id)
      await load()
    } catch (e) {
      setError(apiError(e, 'Could not close the follow-up.'))
    }
  }

  async function remind(f: Followup) {
    if (!f.customerPhone) {
      setError('No phone on file for this customer — reminders need a number.')
      return
    }
    setError('')
    try {
      await messagesApi.send({
        templateCode: 'due_reminder',
        toPhone: f.customerPhone,
        variables: {
          name: f.customerName,
          amount: String(Math.round(f.pendingAmount)),
          docNo: f.documentNo ?? `#${f.receiveDocumentId}`,
          dueDate: f.dueDate,
          branch: f.branchCode,
        },
        relatedType: 'CreditFollowup',
        relatedId: f.id,
      })
      await load()
    } catch (e) {
      setError(apiError(e, 'Could not send the reminder.'))
    }
  }

  return (
    <div style={{ maxWidth: 1000, margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
        <h1 style={{ fontSize: '1.3rem', color: 'var(--navy)', margin: 0 }}>Receivables</h1>
        <HelpButton slug="chasing-dues" />
      </div>
      <p style={{ fontSize: '0.82rem', color: 'var(--muted)', marginTop: 0 }}>
        Who owes, by when, and what was promised — dues with owners and dates, not just balances.
      </p>

      <div style={{ display: 'flex', gap: 8, marginBottom: 14 }}>
        {(['followups', 'defaulters'] as const).map((t) => (
          <button key={t} type="button" onClick={() => setTab(t)} style={{ ...ghostBtn, fontWeight: tab === t ? 800 : 600 }}>
            {t === 'followups' ? 'Follow-ups' : 'Defaulters'}
          </button>
        ))}
        {tab === 'followups' && (
          <label style={{ marginLeft: 'auto', fontSize: '0.8rem', display: 'flex', alignItems: 'center', gap: 6 }}>
            <input type="checkbox" checked={overdueOnly} onChange={(e) => setOverdueOnly(e.target.checked)} />
            Overdue only
          </label>
        )}
      </div>

      <ErrorBanner message={error} />

      {tab === 'followups' && (
        <>
          <div style={{ ...card, marginBottom: 14, padding: 14 }}>
            <div style={{ fontWeight: 700, marginBottom: 8 }}>Log a promise</div>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              <input value={docId} onChange={(e) => setDocId(e.target.value)} placeholder="Receive doc id" inputMode="numeric" style={{ ...inputStyle, width: 130 }} />
              <input value={dueDate} onChange={(e) => setDueDate(e.target.value)} type="date" style={inputStyle} />
              <input value={promise} onChange={(e) => setPromise(e.target.value)} placeholder="What the customer promised" style={{ ...inputStyle, flex: 1, minWidth: 200 }} />
              <button type="button" onClick={create} disabled={busy || !docId || !dueDate} style={primaryBtn(busy)}>Save</button>
            </div>
          </div>
          <div style={card}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead><tr><th style={th}>Document</th><th style={th}>Customer</th><th style={th}>Due</th><th style={th}>Promise</th><th style={th}>Chase</th><th style={th}></th></tr></thead>              <tbody>
                {(items ?? []).map((f) => (
                  <tr key={f.id}>
                    <td style={td}>{f.documentNo ?? `#${f.receiveDocumentId}`}<div style={{ fontSize: '0.72rem', color: 'var(--faint)' }}>{f.branchCode} · {inr(f.pendingAmount)} due</div></td>
                    <td style={td}>{f.customerName}<div style={{ fontSize: '0.72rem', color: 'var(--faint)' }}>{f.customerPhone ?? 'no phone'}</div></td>
                    <td style={td}>{fmtDate(f.dueDate)}<div>{f.overdue ? <Badge tone="red">{f.daysOverdue}d overdue</Badge> : <Badge tone="gray">{f.status}</Badge>}</div></td>
                    <td style={{ ...td, fontSize: '0.8rem' }}>{f.promiseNote ?? '—'}</td>
                    <td style={{ ...td, fontSize: '0.78rem', color: 'var(--muted)' }}>
                      {f.remindedCount > 0 ? `${f.remindedCount}x reminded` : 'not yet'}
                    </td>
                    <td style={{ ...td, whiteSpace: 'nowrap' }}>
                      <button type="button" onClick={() => remind(f)} style={{ ...ghostBtn, marginRight: 6 }}>Remind</button>
                      <button type="button" onClick={() => closeItem(f.id)} style={ghostBtn}>Collected</button>
                    </td>
                  </tr>
                ))}
                {items?.length === 0 && <tr><td colSpan={6} style={{ ...td, color: 'var(--faint)' }}>Nothing due — the book is clean.</td></tr>}
              </tbody>
            </table>
          </div>
        </>
      )}

      {tab === 'defaulters' && (
        <div style={card}>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead><tr><th style={th}>Customer</th><th style={th}>Outstanding</th><th style={th}>Open</th><th style={th}>Overdue</th></tr></thead>
            <tbody>
              {(defaulters ?? []).map((d) => (
                <tr key={d.customerId}>
                  <td style={td}>{d.customerName}<div style={{ fontSize: '0.72rem', color: 'var(--faint)' }}>{d.customerPhone ?? 'no phone'}</div></td>
                  <td style={{ ...td, fontWeight: 800 }}>{inr(d.totalOutstanding)}</td>
                  <td style={td}>{d.openFollowups}</td>
                  <td style={td}>{d.overdueFollowups > 0
                    ? <Badge tone="red">{d.overdueFollowups} · oldest {d.oldestOverdueDays}d</Badge>
                    : <Badge tone="green">current</Badge>}</td>
                </tr>
              ))}
              {defaulters?.length === 0 && <tr><td colSpan={4} style={{ ...td, color: 'var(--faint)' }}>No open dues.</td></tr>}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

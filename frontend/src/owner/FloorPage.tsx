import { useEffect, useState } from 'react'
import { jobCardsApi, type RenewalRow, type WipRow } from '../api/jobCards'
import { messagesApi } from '../api/messaging'
import { card, ErrorBanner, inr, Badge, ghostBtn, th, td } from '../shell/ui'
import HelpButton from '../help/HelpButton'

/**
 * Floor board (FEAT-50) + renewals (FEAT-39). Read-only visibility over work
 * already in the system: what's sitting unbilled and why, and whose
 * service/AMC is due. Explicitly not workshop management — no scheduling,
 * no inventory, just the two money-adjacent views.
 */
export default function FloorPage() {
  const [tab, setTab] = useState<'wip' | 'renewals'>('wip')
  const [wip, setWip] = useState<WipRow[] | null>(null)
  const [renewals, setRenewals] = useState<RenewalRow[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    setError('')
    jobCardsApi.board().then(({ data }) => setWip(data)).catch((e) => setError(apiError(e, 'Could not load the floor board.')))
    jobCardsApi.renewals().then(({ data }) => setRenewals(data)).catch(() => {})
  }, [])

  async function remind(r: RenewalRow) {
    if (!r.customerPhone) {
      setError('No phone on file — renewal reminders need a number.')
      return
    }
    setError('')
    try {
      await messagesApi.send({
        templateCode: 'renewal_reminder',
        toPhone: r.customerPhone,
        variables: {
          name: r.customerName,
          vehicleNo: r.vehicleNo ?? '',
          dueDate: r.serviceDueDate,
          branch: r.branchCode,
        },
        relatedType: 'Renewal',
        relatedId: r.jobCardId,
      })
    } catch (e) {
      setError(apiError(e, 'Could not send the reminder.'))
    }
  }

  return (
    <div style={{ maxWidth: 1050, margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
        <h1 style={{ fontSize: '1.3rem', color: 'var(--navy)', margin: 0 }}>Floor & renewals</h1>
        <HelpButton slug="floor-and-renewals" />
      </div>
      <p style={{ fontSize: '0.82rem', color: 'var(--muted)', marginTop: 0 }}>
        What's sitting unbilled and why — plus whose service is due back.
      </p>
      <div style={{ display: 'flex', gap: 8, marginBottom: 14 }}>
        {(['wip', 'renewals'] as const).map((t) => (
          <button key={t} type="button" onClick={() => setTab(t)} style={{ ...ghostBtn, fontWeight: tab === t ? 800 : 600 }}>
            {t === 'wip' ? `Floor · WIP (${wip?.length ?? '…'})` : `Renewals (${renewals?.length ?? '…'})`}
          </button>
        ))}
      </div>
      <ErrorBanner message={error} />

      {tab === 'wip' && (
        <div style={card}>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead><tr><th style={th}>Job</th><th style={th}>Customer · vehicle</th><th style={th}>Status</th><th style={th}>Age</th><th style={th}>Pending</th></tr></thead>
            <tbody>
              {(wip ?? []).map((w) => (
                <tr key={w.jobCardId}>
                  <td style={td}>{w.reference}<div style={{ fontSize: '0.72rem', color: 'var(--faint)' }}>{w.branchCode} · {w.categoryName}</div></td>
                  <td style={td}>{w.customerName}<div style={{ fontSize: '0.72rem', color: 'var(--faint)' }}>{w.vehicleNo ?? 'no vehicle'}</div></td>
                  <td style={td}>{w.businessStatusName}
                    {w.stuckReason
                      ? <div style={{ fontSize: '0.74rem', color: 'var(--amber)' }}>Stuck: {w.stuckReason}</div>
                      : <div style={{ fontSize: '0.72rem', color: 'var(--faint)' }}>no stuck reason</div>}
                  </td>
                  <td style={td}><Badge tone={w.ageDays > 14 ? 'red' : w.ageDays > 7 ? 'amber' : 'gray'}>{w.ageDays}d</Badge></td>
                  <td style={{ ...td, fontWeight: 700 }}>{inr(w.pendingAmount)}</td>
                </tr>
              ))}
              {wip?.length === 0 && <tr><td colSpan={5} style={{ ...td, color: 'var(--faint)' }}>Floor is clear.</td></tr>}
            </tbody>
          </table>
        </div>
      )}

      {tab === 'renewals' && (
        <div style={card}>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead><tr><th style={th}>Due</th><th style={th}>Customer</th><th style={th}>Vehicle</th><th style={th}></th></tr></thead>
            <tbody>
              {(renewals ?? []).map((r) => (
                <tr key={r.jobCardId}>
                  <td style={td}>
                    {r.serviceDueDate}{' '}
                    {r.daysUntilDue < 0
                      ? <Badge tone="red">{-r.daysUntilDue}d overdue</Badge>
                      : <Badge tone={r.daysUntilDue <= 7 ? 'amber' : 'gray'}>in {r.daysUntilDue}d</Badge>}
                  </td>
                  <td style={td}>{r.customerName}<div style={{ fontSize: '0.72rem', color: 'var(--faint)' }}>{r.customerPhone ?? 'no phone'} · {r.branchCode}</div></td>
                  <td style={td}>{r.vehicleNo ?? '—'}</td>
                  <td style={td}><button type="button" onClick={() => remind(r)} style={ghostBtn}>Remind</button></td>
                </tr>
              ))}
              {renewals?.length === 0 && <tr><td colSpan={4} style={{ ...td, color: 'var(--faint)' }}>Nothing due in the next 45 days.</td></tr>}
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

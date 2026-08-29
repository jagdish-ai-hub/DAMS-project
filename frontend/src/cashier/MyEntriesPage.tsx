import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { myEntriesApi, type MyEntry } from '../api/myEntries'
import { card, ErrorBanner, inr, Badge, ghostBtn } from '../shell/ui'

/**
 * My Entries (AGENT.md decision #8): today + recent, workflow badges, queried items
 * highlighted and opened in edit mode for fix-and-resubmit.
 */
export default function MyEntriesPage() {
  const navigate = useNavigate()
  const [entries, setEntries] = useState<MyEntry[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    myEntriesApi.list()
      .then(({ data }) => setEntries(data))
      .catch((e) => setError((e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? 'Could not load your entries.'))
  }, [])

  const today = entries?.filter((e) => e.today) ?? []
  const earlier = entries?.filter((e) => !e.today) ?? []

  return (
    <div style={{ maxWidth: 820, margin: '0 auto' }}>
      <button
        type="button"
        onClick={() => navigate('/')}
        style={{ background: 'none', border: 'none', color: 'var(--navy)', fontSize: '0.82rem', fontWeight: 600, cursor: 'pointer', padding: '4px 0', marginBottom: 12 }}
      >
        ← Home
      </button>
      <h1 style={{ fontSize: '1.3rem', color: 'var(--navy)', marginBottom: 4 }}>My Entries</h1>
      <div style={{ fontSize: '0.85rem', color: 'var(--muted)', marginBottom: 16 }}>
        Everything you've entered. Queried items are highlighted — open one to fix and resubmit.
      </div>

      <ErrorBanner message={error} />
      {entries == null && <p style={{ color: 'var(--muted)', fontSize: '0.85rem' }}>Loading…</p>}

      {entries != null && entries.length === 0 && (
        <div style={{ ...card, color: 'var(--faint)', fontSize: '0.85rem' }}>
          You haven't entered anything yet.
        </div>
      )}

      {today.length > 0 && <Group title="Today" rows={today} onOpen={openEntry} />}
      {earlier.length > 0 && <Group title="Earlier" rows={earlier} onOpen={openEntry} />}
    </div>
  )

  function openEntry(e: MyEntry) {
    // Draft / queried open editable for fix-and-resubmit; others open read-only in the same
    // form (a dedicated review view lands with the Accountant queue in Stage 7).
    navigate(`/new-receipt?editDoc=${e.id}`)
  }
}

function Group({ title, rows, onOpen }: { title: string; rows: MyEntry[]; onOpen: (e: MyEntry) => void }) {
  return (
    <section style={{ marginBottom: 20 }}>
      <h4 style={{ fontSize: '0.74rem', textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--faint)', marginBottom: 8 }}>
        {title}
      </h4>
      <div style={{ ...card, padding: 0 }}>
        {rows.map((e, i) => (
          <div
            key={e.id}
            style={{
              display: 'flex', alignItems: 'center', gap: 14, padding: '12px 16px',
              borderTop: i === 0 ? 'none' : '1px solid var(--line)',
              background: e.queried ? 'var(--amber-bg)' : undefined,
            }}
          >
            <div style={{ minWidth: 150 }}>
              <div style={{ fontFamily: 'Consolas, monospace', fontSize: '0.8rem', fontWeight: 700, color: 'var(--navy2)' }}>
                {e.documentNo ?? 'draft'}
              </div>
              <div style={{ fontSize: '0.74rem', color: 'var(--faint)' }}>{e.jobCardReference}</div>
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: '0.88rem', fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                {e.customerName ?? '—'}
              </div>
              <div style={{ fontSize: '0.74rem', color: 'var(--muted)' }}>
                {e.lineCount} payment{e.lineCount === 1 ? '' : 's'} · received {inr(e.totalReceived)}
                {e.pendingAmount > 0 && ` · ${inr(e.pendingAmount)} pending`}
              </div>
            </div>
            <Badge tone={badgeTone(e)}>{e.settled ? 'SETTLED' : e.workflowStatus}</Badge>
            <button type="button" onClick={() => onOpen(e)} style={ghostBtn}>
              {e.queried ? 'Fix & Resubmit' : 'Open'}
            </button>
          </div>
        ))}
      </div>
    </section>
  )
}

function badgeTone(e: MyEntry): 'green' | 'amber' | 'gray' | 'red' {
  if (e.queried) return 'amber'
  if (e.workflowStatus === 'REJECTED') return 'red'
  if (e.settled || e.workflowStatus === 'APPROVED') return 'green'
  return 'gray'
}

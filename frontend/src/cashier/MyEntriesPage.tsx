import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { myEntriesApi, type MyEntry } from '../api/myEntries'
import { card, ErrorBanner, inr, Badge, ghostBtn, SkeletonRows } from '../shell/ui'

/**
 * My Entries (AGENT.md decision #8): today + recent, receipts and expenses together,
 * workflow badges, queried items highlighted and opened in edit mode for fix-and-resubmit.
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

  function openEntry(e: MyEntry) {
    // Draft / queried open editable for fix-and-resubmit; others open read-only in the same
    // form (a dedicated review view lands with the Accountant queue in Stage 7).
    const path = e.kind === 'EXPENSE' ? '/app/new-expense' : e.kind === 'CASH' ? '/app/cash' : '/app/new-receipt'
    navigate(`${path}?editDoc=${e.id}`)
  }

  return (
    <div style={{ maxWidth: 820, margin: '0 auto' }}>
      <button
        type="button"
        onClick={() => navigate('/app')}
        style={{ background: 'none', border: 'none', color: 'var(--navy)', fontSize: '0.82rem', fontWeight: 600, cursor: 'pointer', padding: '4px 0', marginBottom: 12 }}
      >
        ← Home
      </button>
      <h1 style={{ fontSize: '1.3rem', color: 'var(--navy)', marginBottom: 4 }}>My Entries</h1>
      <div style={{ fontSize: '0.85rem', color: 'var(--muted)', marginBottom: 16 }}>
        Every receipt and expense you've entered. Queried items are highlighted — open one to fix and resubmit.
      </div>

      <ErrorBanner message={error} />
      {entries == null && <SkeletonRows rows={6} height={56} />}

      {entries != null && entries.length === 0 && (
        <div style={{ ...card, color: 'var(--faint)', fontSize: '0.85rem' }}>
          You haven't entered anything yet.
        </div>
      )}

      {today.length > 0 && <Group title="Today" rows={today} onOpen={openEntry} />}
      {earlier.length > 0 && <Group title="Earlier" rows={earlier} onOpen={openEntry} />}
    </div>
  )
}

function Group({ title, rows, onOpen }: { title: string; rows: MyEntry[]; onOpen: (e: MyEntry) => void }) {
  return (
    <section style={{ marginBottom: 20 }}>
      <h4 style={{ fontSize: '0.74rem', textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--faint)', marginBottom: 8 }}>
        {title}
      </h4>
      <div style={{ ...card, padding: 0 }}>
        {rows.map((e, i) => {
          const isExpense = e.kind === 'EXPENSE'
          const isCash = e.kind === 'CASH'
          const tag = isCash ? 'CSH' : isExpense ? 'EXP' : 'RCP'
          const tagBg = isCash ? 'var(--blue-bg)' : isExpense ? 'var(--red-bg)' : 'var(--green-bg)'
          const tagFg = isCash ? 'var(--blue)' : isExpense ? 'var(--red)' : 'var(--green)'
          return (
            <div
              key={`${e.kind}-${e.id}`}
              style={{
                display: 'flex', alignItems: 'center', gap: 12, padding: '12px 16px',
                borderTop: i === 0 ? 'none' : '1px solid var(--line)',
                background: e.queried ? 'var(--amber-bg)' : undefined,
                flexWrap: 'wrap',
              }}
            >
              <div style={{ minWidth: 140 }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <span style={{
                    fontSize: '0.6rem', fontWeight: 800, letterSpacing: '0.04em',
                    padding: '1px 5px', borderRadius: 4, background: tagBg, color: tagFg,
                  }}>
                    {tag}
                  </span>
                  <span style={{ fontFamily: 'Consolas, monospace', fontSize: '0.8rem', fontWeight: 700, color: 'var(--navy2)' }}>
                    {e.documentNo ?? 'draft'}
                  </span>
                </div>
                <div style={{ fontSize: '0.74rem', color: 'var(--faint)' }}>
                  {e.jobCardReference ?? (isExpense ? 'branch overhead' : isCash ? 'cash drawer' : '')}
                </div>
              </div>
              <div style={{ flex: 1, minWidth: 180 }}>
                <div style={{ fontSize: '0.88rem', fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {e.partyName ?? '—'}
                </div>
                <div style={{ fontSize: '0.74rem', color: 'var(--muted)' }}>
                  {isCash
                    ? `moved ${inr(e.total)}`
                    : `${e.lineCount} ${isExpense ? 'line' : 'payment'}${e.lineCount === 1 ? '' : 's'} · ${isExpense ? 'paid' : 'received'} ${inr(e.total)}`}
                  {!isExpense && !isCash && e.pendingAmount != null && e.pendingAmount > 0 && ` · ${inr(e.pendingAmount)} pending`}
                  {isExpense && e.overLimit && ' · over limit'}
                </div>
              </div>
              <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 8 }}>
                {isExpense && e.overLimit && (
                  <span style={{ fontSize: '0.66rem', fontWeight: 700, color: 'var(--amber)', background: 'var(--amber-bg)', borderRadius: 999, padding: '2px 8px' }}>
                    ⚠ over limit
                  </span>
                )}
                <Badge tone={badgeTone(e)}>{e.settled ? 'SETTLED' : e.workflowStatus}</Badge>
                <button type="button" onClick={() => onOpen(e)} style={{ ...ghostBtn, minHeight: 34, display: 'inline-flex', alignItems: 'center' }}>
                  {e.queried ? 'Fix & Resubmit' : 'Open'}
                </button>
              </div>
            </div>
          )
        })}
      </div>
    </section>
  )
}

function badgeTone(e: MyEntry): 'green' | 'amber' | 'gray' | 'red' {
  if (e.queried) return 'amber'
  if (e.workflowStatus === 'REJECTED') return 'red'
  if (e.settled || e.workflowStatus === 'APPROVED' || e.workflowStatus === 'CLOSED') return 'green'
  return 'gray'
}

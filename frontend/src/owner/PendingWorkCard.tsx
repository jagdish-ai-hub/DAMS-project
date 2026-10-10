import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { dashboardApi, type PendingGroup, type PendingItem, type PendingWork } from '../api/dashboard'
import { Badge, Modal, Skeleton, card, inr, td, th } from '../shell/ui'

/**
 * "Stuck with whom" (rev 71): how many entries are waiting on the Cashier, the Accountant and the
 * Finance Manager, and what they are worth. Clicking a tile opens the entries behind that number,
 * oldest first; a row opens the document. Branch filter applies; the period does not — this is
 * "right now", not "this month".
 */

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

const HOLDER_NOTE: Record<string, string> = {
  CASHIER: 'sent back to fix',
  ACCOUNTANT: 'to verify or close',
  FINANCE_MANAGER: 'to approve or close',
}

/** Whole days since an instant (0 = today). */
export function daysSince(iso: string | null, now: number = Date.now()): number | null {
  if (!iso) return null
  const t = new Date(iso).getTime()
  if (Number.isNaN(t)) return null
  return Math.max(0, Math.floor((now - t) / 86_400_000))
}

function waitingText(days: number | null): string {
  if (days == null) return '—'
  if (days === 0) return 'today'
  return days === 1 ? '1 day' : `${days} days`
}

function oldestDays(g: PendingGroup): number | null {
  const ages = g.items.filter((i) => !i.draft).map((i) => daysSince(i.since)).filter((d): d is number => d != null)
  return ages.length ? Math.max(...ages) : null
}

export default function PendingWorkCard({ branchId, scopeLabel }: { branchId?: number; scopeLabel: string }) {
  const navigate = useNavigate()
  const [work, setWork] = useState<PendingWork | null>(null)
  const [error, setError] = useState('')
  const [open, setOpen] = useState<PendingGroup | null>(null)

  useEffect(() => {
    let live = true
    setError('')
    dashboardApi.pendingWork(branchId)
      .then(({ data }) => { if (live) setWork(data) })
      .catch((e) => { if (live) setError(apiError(e, 'Could not load what is waiting.')) })
    return () => { live = false }
  }, [branchId])

  function openItem(it: PendingItem) {
    setOpen(null)
    const path = it.type === 'expense' ? '/app/new-expense' : it.type === 'receipt' ? '/app/new-receipt' : '/app/cash'
    navigate(`${path}?editDoc=${it.id}`)
  }

  const everyone = work?.groups.reduce((n, g) => n + g.count, 0) ?? 0

  return (
    <div style={{ ...card, marginBottom: 18 }} aria-label="Stuck with whom">
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 10, flexWrap: 'wrap', marginBottom: 12 }}>
        <h3 style={{ fontSize: '0.94rem', fontWeight: 700, margin: 0 }}>Stuck with whom</h3>
        <span style={{ fontSize: '0.74rem', color: 'var(--faint)' }}>
          {work ? `${everyone} entr${everyone === 1 ? 'y' : 'ies'} waiting on someone · ` : ''}{scopeLabel} · click a box to see them
        </span>
      </div>

      {error && <div role="alert" style={{ color: 'var(--red)', fontSize: '0.82rem' }}>{error}</div>}
      {!work && !error && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 200px), 1fr))', gap: 12 }}>
          {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} height={84} radius={10} />)}
        </div>
      )}
      {work && (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 200px), 1fr))', gap: 12 }}>
          {work.groups.map((g) => {
            const total = g.count + g.draftCount
            const oldest = oldestDays(g)
            return (
              <button
                key={g.holder}
                type="button"
                disabled={total === 0}
                onClick={() => setOpen(g)}
                aria-label={`${g.label}: ${g.count} waiting`}
                className="dams-kpi"
                data-clickable={total > 0 ? '' : undefined}
                style={{
                  textAlign: 'left', border: '1px solid var(--line)', borderRadius: 10, padding: '12px 14px',
                  background: 'var(--surface)', cursor: total > 0 ? 'pointer' : 'default', opacity: total === 0 ? 0.7 : 1,
                }}
              >
                <div style={{ fontSize: '0.72rem', textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--muted)', fontWeight: 700 }}>
                  With {g.label}{total > 0 && <span style={{ color: 'var(--navy2)' }}> ⓘ</span>}
                </div>
                <div style={{ display: 'flex', alignItems: 'baseline', gap: 8, marginTop: 4 }}>
                  <span style={{ fontSize: '1.6rem', fontWeight: 800, fontVariantNumeric: 'tabular-nums', color: g.count > 0 ? 'var(--amber)' : 'var(--green)' }}>{g.count}</span>
                  <span style={{ fontSize: '0.78rem', color: 'var(--muted)' }}>{g.count > 0 ? `${HOLDER_NOTE[g.holder] ?? 'waiting'} · ${inr(g.amount)}` : 'nothing waiting'}</span>
                </div>
                <div style={{ fontSize: '0.72rem', color: 'var(--faint)', marginTop: 3, minHeight: '1.1em' }}>
                  {oldest != null && oldest > 0 ? `oldest ${waitingText(oldest)}` : ''}
                  {g.draftCount > 0 && `${oldest != null && oldest > 0 ? ' · ' : ''}+ ${g.draftCount} draft${g.draftCount === 1 ? '' : 's'} not submitted`}
                </div>
              </button>
            )
          })}
        </div>
      )}

      {open && <PendingModal group={open} scopeLabel={scopeLabel} onRow={openItem} onClose={() => setOpen(null)} />}
    </div>
  )
}

function PendingModal(props: { group: PendingGroup; scopeLabel: string; onRow: (i: PendingItem) => void; onClose: () => void }) {
  const { group } = props
  const total = group.items.reduce((s, i) => s + i.amount, 0)
  return (
    <Modal
      title={`Waiting on the ${group.label}`}
      subtitle={`${props.scopeLabel} · oldest first · click a row to open it`}
      onClose={props.onClose}
      maxWidth={860}
    >
      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', minWidth: 640, borderCollapse: 'collapse', fontSize: '0.82rem' }}>
          <thead>
            <tr>
              {['Waiting', 'Doc', 'Branch', 'Party', 'Why it is here'].map((h) => (
                <th key={h} style={{ ...th, position: 'sticky', top: 0, background: 'var(--surface)', zIndex: 1 }}>{h}</th>
              ))}
              <th style={{ ...th, textAlign: 'right', position: 'sticky', top: 0, background: 'var(--surface)', zIndex: 1 }}>Amount</th>
            </tr>
          </thead>
          <tbody>
            {group.items.map((it) => (
              <tr
                key={`${it.type}-${it.id}`}
                role="button"
                tabIndex={0}
                onClick={() => props.onRow(it)}
                onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); props.onRow(it) } }}
                style={{ cursor: 'pointer' }}
                onMouseEnter={(e) => { e.currentTarget.style.background = 'var(--navy3)' }}
                onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent' }}
              >
                <td style={td}>{waitingText(daysSince(it.since))}</td>
                <td style={{ ...td, fontFamily: 'Consolas, monospace', fontSize: '0.78rem' }}>
                  {it.documentNo ?? '(draft)'}
                  <span style={{ display: 'block', fontFamily: 'inherit', color: 'var(--faint)', fontSize: '0.68rem', textTransform: 'uppercase' }}>{it.type}</span>
                </td>
                <td style={td}>{it.branchCode}</td>
                <td style={td}>
                  {it.party}
                  <span style={{ display: 'block', color: 'var(--faint)', fontSize: '0.72rem' }}>{it.category}</span>
                </td>
                <td style={td}>
                  <Badge tone={it.draft ? 'gray' : it.workflowStatus.includes('QUERIED') ? 'amber' : 'blue'}>{it.stage}</Badge>
                </td>
                <td style={{ ...td, textAlign: 'right', fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>{inr(it.amount)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div style={{
        display: 'flex', justifyContent: 'space-between', alignItems: 'center',
        padding: '12px 4px 2px', marginTop: 8, borderTop: '2px solid var(--line)',
      }}>
        <span style={{ fontSize: '0.78rem', color: 'var(--muted)' }}>
          {group.items.length} entr{group.items.length === 1 ? 'y' : 'ies'}
          {group.draftCount > 0 ? ` (${group.draftCount} unsent draft${group.draftCount === 1 ? '' : 's'} included)` : ''}
        </span>
        <span style={{ fontSize: '0.98rem', fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{inr(total)}</span>
      </div>
    </Modal>
  )
}

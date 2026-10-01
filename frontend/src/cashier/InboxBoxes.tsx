import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { myEntriesApi, type InboxItem, type CashierInbox } from '../api/myEntries'
import { card, fmtDateShort, fmtDateTime, inr } from '../shell/ui'

/**
 * The two message boxes on the Cashier home page (rev 57):
 *   left  — "Queries for me": documents the Accountant / Finance Manager sent back (or rejected)
 *   right — "Approvals": expenses I asked the Finance Manager to pre-approve, and the replies
 *
 * Both are derived from each document's current state on the server, so acting on one
 * (resubmit, submit, re-request) makes it disappear and the badge count drops — nothing to
 * mark as read. Clicking a message opens that document in its own screen.
 */

/**
 * One fetch shared by both boxes. No polling: it loads when the cashier opens Home (login lands
 * here, and coming back from a document remounts the page), so the badges are current each visit.
 */
export function useInbox() {
  const [inbox, setInbox] = useState<CashierInbox | null>(null)
  const [failed, setFailed] = useState(false)

  const load = useCallback(() => {
    myEntriesApi.inbox()
      .then(({ data }) => { setInbox(data); setFailed(false) })
      .catch(() => setFailed(true))
  }, [])

  useEffect(() => { load() }, [load])

  return { inbox, failed }
}

const ROLE_LABEL: Record<string, string> = {
  ACCOUNTANT: 'Accountant',
  FINANCE_MANAGER: 'Finance Manager',
  OWNER: 'Owner',
}

const STATE_LABEL: Record<string, { text: string; fg: string; bg: string }> = {
  QUERIED: { text: 'Queried', fg: 'var(--amber)', bg: 'var(--amber-bg)' },
  REJECTED: { text: 'Rejected', fg: 'var(--red)', bg: 'var(--red-bg)' },
  PRE_QUERIED: { text: 'FM queried', fg: 'var(--amber)', bg: 'var(--amber-bg)' },
  PRE_APPROVED: { text: 'Approved', fg: 'var(--green)', bg: 'var(--green-bg)' },
  PRE_PENDING: { text: 'Waiting', fg: 'var(--muted)', bg: 'var(--navy3)' },
}

function pathFor(item: InboxItem): string {
  const base = item.kind === 'EXPENSE' ? '/app/new-expense' : item.kind === 'CASH' ? '/app/cash' : '/app/new-receipt'
  return `${base}?editDoc=${item.id}`
}

/** "5 min ago", "3 h ago", "2 d ago"; older than a week falls back to the date. The full time is the tooltip. */
function ago(iso: string | null): string {
  if (!iso) return ''
  const mins = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000))
  if (mins < 1) return 'just now'
  if (mins < 60) return `${mins} min ago`
  if (mins < 60 * 24) return `${Math.round(mins / 60)} h ago`
  if (mins < 60 * 24 * 7) return `${Math.round(mins / (60 * 24))} d ago`
  return fmtDateShort(iso)
}

function Box(props: {
  title: string
  hint: string
  count: number
  items: InboxItem[] | null
  failed: boolean
  empty: string
  testId: string
}) {
  const navigate = useNavigate()
  return (
    <section aria-label={props.title} data-testid={props.testId} style={{ ...card, padding: '16px 10px 10px' }}>
      <div style={{ padding: '0 6px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <h2 style={{ fontSize: '0.95rem', fontWeight: 700, color: 'var(--navy)', margin: 0 }}>{props.title}</h2>
          {props.count > 0 && (
            <span aria-label={`${props.count} need your attention`} style={{
              background: 'var(--red)', color: '#fff', borderRadius: 999, fontSize: '0.7rem', fontWeight: 700,
              minWidth: 20, height: 20, padding: '0 6px', display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
            }}>{props.count}</span>
          )}
        </div>
        <div style={{ fontSize: '0.72rem', color: 'var(--muted)', margin: '3px 0 10px' }}>{props.hint}</div>
      </div>

      {props.items == null ? (
        <div style={{ padding: '18px 6px', fontSize: '0.8rem', color: 'var(--faint)' }}>
          {props.failed ? 'Could not load messages.' : 'Loading…'}
        </div>
      ) : props.items.length === 0 ? (
        <div style={{
          margin: '0 6px 6px', padding: '22px 12px', textAlign: 'center', fontSize: '0.8rem',
          color: 'var(--muted)', background: 'var(--bg)', borderRadius: 10,
        }}>
          <div style={{ fontSize: '1.1rem', marginBottom: 4, color: 'var(--faint)' }}>✓</div>
          {props.empty}
        </div>
      ) : (
        <div style={{ maxHeight: 'min(440px, calc(100vh - 380px))', minHeight: 120, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: 2 }}>
          {props.items.map((it) => {
            const st = STATE_LABEL[it.state] ?? STATE_LABEL.QUERIED
            const from = it.fromName
              ? `${it.fromName}${it.fromRole ? ` (${ROLE_LABEL[it.fromRole] ?? it.fromRole})` : ''}`
              : null
            return (
              <button
                key={`${it.kind}-${it.id}-${it.state}`}
                type="button"
                onClick={() => navigate(pathFor(it))}
                title="Open this document"
                className="w-full text-left rounded-lg transition-colors hover:bg-[var(--navy3)]"
                style={{ display: 'block', background: 'transparent', cursor: 'pointer', border: 0, padding: '9px 8px' }}
              >
                <div style={{ display: 'flex', gap: 8, alignItems: 'center', justifyContent: 'space-between' }}>
                  <span style={{ display: 'flex', alignItems: 'center', gap: 7, minWidth: 0 }}>
                    {it.needsAction && (
                      <span aria-hidden="true" style={{ width: 7, height: 7, borderRadius: 999, background: 'var(--red)', flex: 'none' }} />
                    )}
                    <span style={{ fontWeight: 700, fontSize: '0.84rem', color: 'var(--ink)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                      {it.title}
                    </span>
                  </span>
                  <span style={{ fontSize: '0.78rem', fontVariantNumeric: 'tabular-nums', color: 'var(--muted)', flex: 'none' }}>{inr(it.total)}</span>
                </div>
                <div style={{ display: 'flex', gap: 6, alignItems: 'center', marginTop: 4, flexWrap: 'wrap' }}>
                  <span style={{ background: st.bg, color: st.fg, borderRadius: 5, fontSize: '0.68rem', fontWeight: 700, padding: '1px 6px' }}>{st.text}</span>
                  <span style={{ fontSize: '0.7rem', color: 'var(--muted)', fontFamily: 'Consolas, monospace' }}>
                    {it.documentNo ?? `${it.kind === 'EXPENSE' ? 'Expense' : it.kind === 'CASH' ? 'Cash' : 'Receipt'} draft`}
                  </span>
                  {it.at && (
                    <span title={fmtDateTime(it.at)} style={{ fontSize: '0.68rem', color: 'var(--faint)', marginLeft: 'auto' }}>{ago(it.at)}</span>
                  )}
                </div>
                {(from || it.note) && (
                  <div style={{
                    fontSize: '0.75rem', color: 'var(--ink)', marginTop: 6, padding: '6px 8px', background: 'var(--bg)',
                    borderRadius: 6, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden',
                  }}>
                    {from && <strong>{from}: </strong>}{it.note}
                  </div>
                )}
              </button>
            )
          })}
        </div>
      )}
    </section>
  )
}

export function QueriesBox(props: { inbox: CashierInbox | null; failed: boolean }) {
  return (
    <Box
      testId="queries-box"
      title="Queries for me"
      hint="From your Accountant or Finance Manager — click to fix"
      count={props.inbox?.queriesToAct ?? 0}
      items={props.inbox?.queries ?? null}
      failed={props.failed}
      empty="No queries — you're all caught up."
    />
  )
}

export function ApprovalsBox(props: { inbox: CashierInbox | null; failed: boolean }) {
  return (
    <Box
      testId="approvals-box"
      title="Approvals"
      hint="Expenses sent to the Finance Manager, and replies"
      count={props.inbox?.approvalsToAct ?? 0}
      items={props.inbox?.approvals ?? null}
      failed={props.failed}
      empty="No approval requests in progress."
    />
  )
}

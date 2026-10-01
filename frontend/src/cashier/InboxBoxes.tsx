import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { myEntriesApi, type InboxItem, type CashierInbox } from '../api/myEntries'
import { card, fmtDateTime, inr } from '../shell/ui'

/**
 * The two message boxes on the Cashier home page (rev 57):
 *   left  — "Queries for me": documents the Accountant / Finance Manager sent back (or rejected)
 *   right — "Approvals": expenses I asked the Finance Manager to pre-approve, and the replies
 *
 * Both are derived from each document's current state on the server, so acting on one
 * (resubmit, submit, re-request) makes it disappear and the badge count drops — nothing to
 * mark as read. Clicking a message opens that document in its own screen.
 */

const REFRESH_MS = 60_000

/** One fetch shared by both boxes; refreshes every minute and whenever the tab regains focus. */
export function useInbox() {
  const [inbox, setInbox] = useState<CashierInbox | null>(null)
  const [failed, setFailed] = useState(false)

  const load = useCallback(() => {
    myEntriesApi.inbox()
      .then(({ data }) => { setInbox(data); setFailed(false) })
      .catch(() => setFailed(true))
  }, [])

  useEffect(() => {
    load()
    const t = setInterval(load, REFRESH_MS)
    const onVisible = () => { if (document.visibilityState === 'visible') load() }
    document.addEventListener('visibilitychange', onVisible)
    return () => { clearInterval(t); document.removeEventListener('visibilitychange', onVisible) }
  }, [load])

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
    <section aria-label={props.title} data-testid={props.testId} style={{ ...card, padding: '14px 14px 8px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 2 }}>
        <h2 style={{ fontSize: '0.9rem', fontWeight: 700, color: 'var(--navy)', margin: 0 }}>{props.title}</h2>
        {props.count > 0 && (
          <span aria-label={`${props.count} need your attention`} style={{
            background: 'var(--red)', color: '#fff', borderRadius: 999, fontSize: '0.7rem', fontWeight: 700,
            minWidth: 20, height: 20, padding: '0 6px', display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
          }}>{props.count}</span>
        )}
      </div>
      <div style={{ fontSize: '0.72rem', color: 'var(--muted)', marginBottom: 8 }}>{props.hint}</div>

      {props.items == null ? (
        <div style={{ padding: '14px 0', fontSize: '0.8rem', color: 'var(--faint)' }}>
          {props.failed ? 'Could not load messages.' : 'Loading…'}
        </div>
      ) : props.items.length === 0 ? (
        <div style={{ padding: '14px 0', fontSize: '0.8rem', color: 'var(--faint)' }}>{props.empty}</div>
      ) : (
        <div style={{ maxHeight: 360, overflowY: 'auto' }}>
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
                style={{
                  display: 'block', width: '100%', textAlign: 'left', background: 'transparent', cursor: 'pointer',
                  border: 0, borderTop: '1px solid var(--line)', padding: '9px 4px 9px 8px',
                  borderLeft: it.needsAction ? '3px solid var(--red)' : '3px solid transparent',
                }}
              >
                <div style={{ display: 'flex', gap: 8, alignItems: 'center', justifyContent: 'space-between' }}>
                  <span style={{ fontWeight: 700, fontSize: '0.82rem', color: 'var(--ink)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {it.title}
                  </span>
                  <span style={{ fontSize: '0.78rem', fontVariantNumeric: 'tabular-nums', color: 'var(--muted)' }}>{inr(it.total)}</span>
                </div>
                <div style={{ display: 'flex', gap: 6, alignItems: 'center', marginTop: 3, flexWrap: 'wrap' }}>
                  <span style={{ background: st.bg, color: st.fg, borderRadius: 5, fontSize: '0.68rem', fontWeight: 700, padding: '1px 6px' }}>{st.text}</span>
                  <span style={{ fontSize: '0.72rem', color: 'var(--muted)', fontFamily: 'Consolas, monospace' }}>
                    {it.documentNo ?? `${it.kind === 'EXPENSE' ? 'Expense' : it.kind === 'CASH' ? 'Cash' : 'Receipt'} draft`}
                  </span>
                </div>
                {(from || it.note) && (
                  <div style={{
                    fontSize: '0.75rem', color: 'var(--ink)', marginTop: 4, display: '-webkit-box',
                    WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden',
                  }}>
                    {from && <strong>{from}: </strong>}{it.note}
                  </div>
                )}
                {it.at && <div style={{ fontSize: '0.68rem', color: 'var(--faint)', marginTop: 2 }}>{fmtDateTime(it.at)}</div>}
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
      hint="Sent back by your Accountant or the Finance Manager — click to fix and resubmit"
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
      hint="Expenses you sent to the Finance Manager, and their replies"
      count={props.inbox?.approvalsToAct ?? 0}
      items={props.inbox?.approvals ?? null}
      failed={props.failed}
      empty="No approval requests in progress."
    />
  )
}

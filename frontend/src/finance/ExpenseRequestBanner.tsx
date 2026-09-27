import type { ReviewQueueItem } from '../api/review'
import { card, inr, fmtDateShort } from '../shell/ui'

/**
 * rev 53 — the card at the top of the Finance Manager's home: over-limit expenses cashiers
 * sent for approval before submitting. Each request opens straight into its detail, where
 * the FM approves it or queries it back; the same list also sits in the Expenses tab.
 */
export default function ExpenseRequestBanner(props: {
  requests: ReviewQueueItem[]
  onOpen: (id: number) => void
}) {
  const { requests } = props
  if (requests.length === 0) return null
  const shown = requests.slice(0, 4)

  return (
    <div role="status" style={{ ...card, marginBottom: 12, borderLeft: '3px solid var(--navy)' }}>
      <div style={{ fontSize: '0.84rem', display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap', marginBottom: 8 }}>
        <strong>
          {requests.length === 1
            ? 'New expense approval request from a cashier'
            : `${requests.length} new expense approval requests from cashiers`}
        </strong>
        <span style={{ color: 'var(--muted)' }}>above the sub-category limit · waiting for you before they can submit</span>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {shown.map((r) => (
          <button
            key={r.id}
            type="button"
            onClick={() => props.onOpen(r.id)}
            style={{
              display: 'flex', alignItems: 'center', gap: 10, width: '100%', textAlign: 'left',
              border: 'none', borderTop: '1px solid var(--line)', background: 'transparent',
              padding: '9px 0', cursor: 'pointer', fontSize: '0.84rem', minHeight: 40,
            }}
          >
            <span style={{ fontFamily: 'Consolas, monospace', fontSize: '0.72rem', color: 'var(--navy2)', whiteSpace: 'nowrap' }}>
              {r.branchCode} · #{r.id}
            </span>
            <span style={{ flex: 1, minWidth: 0, fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {r.partyName} <span style={{ color: 'var(--muted)', fontWeight: 400 }}>· {r.categoryName}</span>
            </span>
            {r.approvalRequestedAt && (
              <span style={{ fontSize: '0.72rem', color: 'var(--faint)', whiteSpace: 'nowrap' }}>{fmtDateShort(r.approvalRequestedAt)}</span>
            )}
            <span style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap' }}>{inr(r.amount)}</span>
            <span style={{ color: 'var(--navy2)', fontWeight: 700, fontSize: '0.78rem', whiteSpace: 'nowrap' }}>Open →</span>
          </button>
        ))}
      </div>
      {requests.length > shown.length && (
        <div style={{ fontSize: '0.76rem', color: 'var(--faint)', marginTop: 6 }}>
          + {requests.length - shown.length} more in the Expenses tab
        </div>
      )}
    </div>
  )
}

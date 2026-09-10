import { Modal, Badge, SkeletonRows, ErrorBanner, inr, td, th } from './ui'
import type { MoneyMovementItem } from '../api/dashboard'
import type { CashDocument } from '../api/cash'

/** One reconciliation row — a receipt line, expense line, or cash In/Out movement. */
export interface BreakdownRow {
  id: string
  documentId: number
  kind: 'receipt' | 'expense' | 'cash-in' | 'cash-out'
  date: string
  documentNo: string | null
  status: string
  branchCode: string | null
  party: string
  description: string
  amount: number
}

/** Maps the backend's {@link MoneyMovementItem} rows (receipts/expenses) into breakdown rows. */
export function moneyMovementsToRows(items: MoneyMovementItem[]): BreakdownRow[] {
  return items.map((it, i) => ({
    id: `${it.kind}-${it.documentId}-${i}`,
    documentId: it.documentId,
    kind: it.kind,
    date: it.date,
    documentNo: it.documentNo,
    status: it.workflowStatus,
    branchCode: it.branchCode,
    party: it.party,
    description: it.description,
    amount: it.amount,
  }))
}

/**
 * Maps cash_document movements into breakdown rows — same filter as the cashIn/cashOut
 * subtotal itself (every movement that's actually left or entered the drawer: a DRAFT isn't
 * real money yet, a REJECTED never was). Pass `direction` to show only one side.
 */
export function cashMovementsToRows(movements: CashDocument[], direction?: 'IN' | 'OUT'): BreakdownRow[] {
  return movements
    .filter((m) => (direction == null || m.direction === direction)
      && m.workflowStatus !== 'DRAFT' && m.workflowStatus !== 'REJECTED')
    .map((m) => ({
      id: `cash-${m.id}`,
      documentId: m.id,
      kind: m.direction === 'IN' ? 'cash-in' : 'cash-out',
      date: m.transactionDate,
      documentNo: m.documentNo,
      status: m.workflowStatus,
      branchCode: m.branchCode,
      party: m.bankName ?? '—',
      description: m.remark || (m.direction === 'IN' ? 'Cash In from bank' : 'Cash Out to bank'),
      amount: m.amount,
    }))
}

function statusTone(status: string): 'green' | 'amber' | 'gray' | 'red' {
  if (status === 'APPROVED' || status === 'CLOSED') return 'green'
  if (status === 'QUERIED') return 'amber'
  if (status === 'REJECTED') return 'red'
  return 'gray'
}

/**
 * "Where is this money coming from / going to" — a clickable line-item list behind a Collections
 * / Expenses / Cash-in-hand KPI or a Cash-page drawer line. Each row opens the document it came
 * from (the same page "My Entries" opens into), so the total is always reconcilable back to
 * individual receipts, expenses, and cash movements.
 */
export default function MoneyBreakdownModal(props: {
  title: string
  subtitle?: string
  rows: BreakdownRow[] | null   // null while loading
  error?: string
  onRowClick: (row: BreakdownRow) => void
  onClose: () => void
}) {
  const { rows } = props
  const total = rows?.reduce((sum, r) => sum + r.amount, 0) ?? 0

  return (
    <Modal title={props.title} subtitle={props.subtitle} onClose={props.onClose} maxWidth={720}>
      <ErrorBanner message={props.error ?? ''} />
      {rows == null && <SkeletonRows rows={5} height={44} />}
      {rows != null && rows.length === 0 && (
        <div style={{ padding: '18px 4px', fontSize: '0.84rem', color: 'var(--muted)', textAlign: 'center' }}>
          Nothing here for this period.
        </div>
      )}
      {rows != null && rows.length > 0 && (
        <>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.82rem' }}>
              <thead>
                <tr>
                  <th style={th}>Date</th>
                  <th style={th}>Doc</th>
                  <th style={th}>Party</th>
                  <th style={th}>Detail</th>
                  <th style={th}>Status</th>
                  <th style={{ ...th, textAlign: 'right' }}>Amount</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr
                    key={r.id}
                    onClick={() => props.onRowClick(r)}
                    style={{ cursor: 'pointer' }}
                    onMouseEnter={(e) => { e.currentTarget.style.background = 'var(--navy3)' }}
                    onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent' }}
                  >
                    <td style={td}>{r.date}</td>
                    <td style={{ ...td, fontFamily: 'Consolas, monospace', fontSize: '0.78rem' }}>
                      {r.documentNo ?? '(draft)'}
                    </td>
                    <td style={td}>{r.party}</td>
                    <td style={td}>
                      {r.description}
                      {r.branchCode && <span style={{ color: 'var(--faint)' }}> · {r.branchCode}</span>}
                    </td>
                    <td style={td}><Badge tone={statusTone(r.status)}>{r.status}</Badge></td>
                    <td style={{ ...td, textAlign: 'right', fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>
                      {inr(r.amount)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div style={{
            display: 'flex', justifyContent: 'space-between', alignItems: 'center',
            padding: '12px 4px 2px', marginTop: 8, borderTop: '2px solid var(--line)',
          }}>
            <span style={{ fontSize: '0.78rem', color: 'var(--muted)' }}>{rows.length} entr{rows.length === 1 ? 'y' : 'ies'} · click a row to open it</span>
            <span style={{ fontSize: '0.98rem', fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{inr(total)}</span>
          </div>
        </>
      )}
    </Modal>
  )
}

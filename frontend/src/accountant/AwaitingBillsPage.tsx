import { useEffect, useState } from 'react'
import { expensesApi, type ExpenseDocument } from '../api/expenses'
import { card, ErrorBanner, inr, Badge, th, td, fmtDate } from '../shell/ui'
import HelpButton from '../help/HelpButton'

/**
 * Awaiting bills (FEAT-47): expenses parked on the 'Awaiting Receipt'
 * business status, oldest first. Turns the month-end bill-chasing scramble
 * into a daily five-minute habit — open an entry to attach its bill.
 */
export default function AwaitingBillsPage() {
  const [items, setItems] = useState<ExpenseDocument[] | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    expensesApi.awaitingBills()
      .then(({ data }) => setItems(data))
      .catch((e) => setError(apiError(e, 'Could not load awaiting bills.')))
  }, [])

  return (
    <div style={{ maxWidth: 1000, margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
        <h1 style={{ fontSize: '1.3rem', color: 'var(--navy)', margin: 0 }}>Awaiting bills</h1>
        <HelpButton slug="awaiting-bills" />
      </div>
      <p style={{ fontSize: '0.82rem', color: 'var(--muted)', marginTop: 0 }}>
        Expenses recorded without their bills. Attach the bill on the entry — Tally stays provisional until then.
      </p>
      <ErrorBanner message={error} />
      <div style={card}>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead><tr><th style={th}>Document</th><th style={th}>Receiver</th><th style={th}>Amount</th><th style={th}>Waiting since</th><th style={th}>State</th></tr></thead>
          <tbody>
            {(items ?? []).map((d) => (
              <tr key={d.id}>
                <td style={td}>{d.documentNo ?? `#${d.id}`}<div style={{ fontSize: '0.72rem', color: 'var(--faint)' }}>{d.branchCode}</div></td>
                <td style={td}>{d.receiverName ?? '—'}</td>
                  <td style={{ ...td, fontWeight: 700 }}>{inr(d.totalAmount)}</td>
                <td style={td}>{fmtDate(d.createdAt)}</td>
                <td style={td}><Badge tone="amber">{d.workflowStatus}</Badge></td>
              </tr>
            ))}
            {items?.length === 0 && <tr><td colSpan={5} style={{ ...td, color: 'var(--faint)' }}>Every expense has its bill. Close season will be boring — good.</td></tr>}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

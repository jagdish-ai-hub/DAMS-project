import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { ledgerApi, type CustomerStatement } from '../api/ledger'
import { fmtDate, fmtDateShort, inr } from '../shell/ui'

/**
 * Fleet-owner statement (FEAT-43): dues, payments and balance across every
 * vehicle — printable like the receipt slip, or shared on WhatsApp with one
 * tap (same wa.me pattern as the payment slip).
 */
export default function StatementModal({ customerId, onClose }: {
  customerId: number
  onClose: () => void
}) {
  const [stmt, setStmt] = useState<CustomerStatement | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    ledgerApi.statement(customerId)
      .then(({ data }) => setStmt(data))
      .catch(() => setError('Could not load the statement.'))
  }, [customerId])

  function shareWhatsApp() {
    if (!stmt) return
    const lines = [
      `${stmt.customerName} — account statement (${stmt.generatedOn})`,
      `Invoiced: Rs.${Math.round(stmt.totalInvoiced)}`,
      `Received: Rs.${Math.round(stmt.totalReceived)}`,
      `Balance due: Rs.${Math.round(stmt.balanceDue)}`,
      ...stmt.jobCards.filter((j) => j.balance > 0).map((j) => `${j.reference}: Rs.${Math.round(j.balance)} due`),
    ]
    window.open(`https://wa.me/?text=${encodeURIComponent(lines.join('\n'))}`, '_blank', 'noopener')
  }

  return (
    <div style={{
      position: 'fixed', inset: 0, background: 'rgba(16,24,40,.5)', zIndex: 60,
      display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 16,
    }} onClick={onClose}>
      <style>{`
        @media print {
          body * { visibility: hidden; }
          #dams-printable-statement, #dams-printable-statement * { visibility: visible; }
          #dams-printable-statement { position: absolute; left: 0; top: 0; width: 100%; padding: 24px; font-size: 14px; }
          .no-print { display: none !important; }
        }
      `}</style>
      <div
        onClick={(e) => e.stopPropagation()}
        style={{ background: 'var(--surface)', borderRadius: 12, maxWidth: 640, width: '100%', maxHeight: '88vh', overflowY: 'auto', padding: 22 }}
      >
        <div className="no-print" style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <button type="button" onClick={onClose} aria-label="Close" style={{ background: 'none', border: 'none', cursor: 'pointer' }}>
            <X size={20} />
          </button>
        </div>
        {error && <p style={{ color: 'var(--red)' }}>{error}</p>}
        {!stmt && !error && <p style={{ color: 'var(--muted)' }}>Loading statement…</p>}
        {stmt && (
          <div id="dams-printable-statement">
            <h2 style={{ fontSize: '1.15rem', margin: '0 0 2px' }}>{stmt.customerName} — Statement</h2>
            <div style={{ fontSize: '0.78rem', color: 'var(--muted)', marginBottom: 12 }}>
              {stmt.generatedOn} · prepared by {stmt.generatedBy}{stmt.customerPhone ? ` · ${stmt.customerPhone}` : ''}
            </div>
            <div style={{ display: 'flex', gap: 18, marginBottom: 12, fontSize: '0.9rem' }}>
              <span>Invoiced <strong>{inr(stmt.totalInvoiced)}</strong></span>
              <span>Received <strong>{inr(stmt.totalReceived)}</strong></span>
              <span>Balance due <strong>{inr(stmt.balanceDue)}</strong></span>
            </div>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.82rem' }}>
              <thead>
                <tr>
                  <th style={{ textAlign: 'left', borderBottom: '1px solid var(--line)', padding: '6px 4px' }}>Job</th>
                  <th style={{ textAlign: 'left', borderBottom: '1px solid var(--line)', padding: '6px 4px' }}>Invoice</th>
                  <th style={{ textAlign: 'right', borderBottom: '1px solid var(--line)', padding: '6px 4px' }}>Received</th>
                  <th style={{ textAlign: 'right', borderBottom: '1px solid var(--line)', padding: '6px 4px' }}>Balance</th>
                </tr>
              </thead>
              <tbody>
                {stmt.jobCards.map((j) => (
                  <tr key={j.id}>
                    <td style={{ padding: '6px 4px' }}>{j.reference}<div style={{ fontSize: '0.72rem', color: 'var(--muted)' }}>{j.categoryName ?? ''}</div></td>
                    <td style={{ padding: '6px 4px' }}>{j.invoiceAmount != null ? inr(j.invoiceAmount) : '—'}</td>
                    <td style={{ padding: '6px 4px', textAlign: 'right' }}>{inr(j.received)}</td>
                    <td style={{ padding: '6px 4px', textAlign: 'right', fontWeight: 700 }}>{inr(j.balance)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <h3 style={{ fontSize: '0.9rem', margin: '14px 0 6px' }}>Payments</h3>
            {stmt.payments.length === 0 && <p style={{ fontSize: '0.82rem', color: 'var(--muted)' }}>No payments recorded.</p>}
            {stmt.payments.map((t) => (
              <div key={t.lineId} style={{ display: 'flex', gap: 10, fontSize: '0.8rem', padding: '4px 0' }}>
                <span style={{ color: 'var(--muted)', width: 90 }}>{fmtDateShort(t.date)}</span>
                <span style={{ flex: 1 }}>{t.description}{t.mode ? ` · ${t.mode}` : ''}</span>
                <strong>{inr(t.amount)}</strong>
              </div>
            ))}
            <div style={{ fontSize: '0.72rem', color: 'var(--muted)', marginTop: 12 }}>Generated {fmtDate(stmt.generatedOn)} · {stmt.generatedBy}</div>
          </div>
        )}
        <div className="no-print" style={{ display: 'flex', gap: 8, marginTop: 14 }}>
          <button type="button" onClick={() => window.print()} style={{ flex: 1, minHeight: 38, borderRadius: 8, border: '1px solid var(--line)', background: 'var(--surface)', fontWeight: 700, cursor: 'pointer' }}>
            Print
          </button>
          <button type="button" onClick={shareWhatsApp} disabled={!stmt} style={{ flex: 1, minHeight: 38, borderRadius: 8, border: 'none', background: 'var(--green)', color: '#fff', fontWeight: 700, cursor: 'pointer' }}>
            Share on WhatsApp
          </button>
        </div>
      </div>
    </div>
  )
}

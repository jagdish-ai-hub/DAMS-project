import { useState, type ReactNode } from 'react'
import type { ReceiveDocument, SettlementLine } from '../api/receipts'
import type { ExpenseDocument, ExpenseLine } from '../api/expenses'
import type { CashDocument } from '../api/cash'
import { card, Badge, ghostBtn, primaryBtn, inputStyle, inr, fmtDate, fmtDateTime } from '../shell/ui'

/** Shared pieces for the Accountant review queue and the Finance Manager queue. */

export type AnyDoc = ReceiveDocument | ExpenseDocument

export function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

export const isExpense = (d: AnyDoc): d is ExpenseDocument => 'expenseCategoryName' in d

export function wfTone(wf: string): 'green' | 'amber' | 'gray' | 'red' {
  if (wf === 'QUERIED') return 'amber'
  if (wf === 'REJECTED') return 'red'
  if (wf === 'VERIFIED' || wf === 'APPROVED' || wf === 'CLOSED') return 'green'
  return 'gray'
}

export function Tag({ children }: { children: ReactNode }) {
  return (
    <span style={{
      fontSize: '0.6rem', fontWeight: 800, letterSpacing: '0.03em', textTransform: 'uppercase',
      padding: '1px 6px', borderRadius: 4, background: 'var(--amber-bg)', color: 'var(--amber)',
    }}>
      {children}
    </span>
  )
}

export function Kv({ k, v }: { k: string; v: string }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, padding: '6px 0', borderBottom: '1px dashed var(--line)' }}>
      <span style={{ fontSize: '0.75rem', color: 'var(--muted)' }}>{k}</span>
      <span style={{ fontSize: '0.83rem', fontWeight: 600, textAlign: 'right' }}>{v}</span>
    </div>
  )
}

const lcell = { padding: '8px 6px', borderTop: '1px solid var(--line)', fontSize: '0.82rem', verticalAlign: 'top' as const }

/**
 * The record body every reviewer sees — header, lines (with an inline Override box when
 * {@code canOverride}), totals and history. The action bar is supplied by the caller.
 */
export function RecordCard(props: {
  doc: AnyDoc
  canOverride: boolean
  busy: boolean
  onOverride: (lineNo: number, amount: number, reason: string) => Promise<void>
  onError: (msg: string) => void
}) {
  const { doc } = props
  const expense = isExpense(doc)
  const lines: (SettlementLine | ExpenseLine)[] = doc.lines
  const total = expense ? doc.totalAmount : doc.totalReceived
  const party = expense ? doc.receiverName : doc.customerName
  const docNo = doc.documentNo ?? 'draft'

  const [editLine, setEditLine] = useState<number | null>(null)
  const [editAmt, setEditAmt] = useState('')
  const [editReason, setEditReason] = useState('')

  async function save(lineNo: number) {
    const amount = Number(editAmt)
    if (!(amount > 0)) { props.onError('Enter an amount greater than 0'); return }
    if (!editReason.trim()) { props.onError('A reason is required to override the amount'); return }
    await props.onOverride(lineNo, amount, editReason.trim())
    setEditLine(null)
  }

  return (
    <>
      <div style={{ ...card, marginBottom: 14 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap', marginBottom: 10 }}>
          <h2 style={{ fontSize: '1.05rem', color: 'var(--navy)' }}>{party ?? '—'}</h2>
          <span style={{ fontFamily: 'Consolas, monospace', fontSize: '0.78rem', color: 'var(--navy2)' }}>{docNo}</span>
          <span style={{ flex: 1 }} />
          <Badge tone={wfTone(doc.workflowStatus)}>{doc.workflowStatus}</Badge>
          <Badge tone="gray">{doc.businessStatusName ?? '—'}</Badge>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 220px), 1fr))', gap: '2px 26px' }}>
          <Kv k={expense ? 'Expenses category' : 'Category'} v={expense ? doc.expenseCategoryName ?? '—' : doc.categoryName ?? '—'} />
          <Kv k={expense ? 'Job ID / PO / SO' : 'Job card'} v={doc.jobCardReference ?? '—'} />
          <Kv k="Branch" v={doc.branchCode ?? '—'} />
          {expense
            ? <Kv k="Entered" v={fmtDate(doc.createdAt)} />
            : <Kv k="Invoice amount" v={doc.invoiceAmount != null ? inr(doc.invoiceAmount) : '—'} />}
        </div>
        {expense && doc.overLimit && (
          <div style={{ fontSize: '0.76rem', color: 'var(--amber)', marginTop: 10 }}>
            ⚠ Over the category limit — needs Finance Manager approval before it can be closed.
          </div>
        )}
      </div>

      <div style={{ ...card, marginBottom: 14 }}>
        <h3 style={{ fontSize: '0.9rem', fontWeight: 700, marginBottom: 8 }}>
          {expense ? 'Expense lines' : 'Settlement lines'} <span style={{ color: 'var(--faint)', fontWeight: 400 }}>· {lines.length}</span>
        </h3>
        <div style={{ overflowX: 'auto' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', minWidth: 520 }}>
            <thead>
              <tr>
                {['Date', expense ? 'Sub-category' : 'Mode', 'Amount', 'Bank', 'Transaction ID', 'Remark'].map((h) => (
                  <th key={h} style={{ fontSize: '0.66rem', textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--faint)', textAlign: 'left', padding: '6px 6px', borderBottom: '1.5px solid var(--line)' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {lines.map((l) => {
                const editing = editLine === l.lineNo
                return (
                  <tr key={l.id}>
                    <td style={lcell}>{fmtDate(l.transactionDate)}</td>
                    <td style={lcell}>{'subCategoryName' in l ? l.subCategoryName : l.settlementModeName}</td>
                    <td style={{ ...lcell, fontVariantNumeric: 'tabular-nums' }}>
                      {l.originalAmount != null && (
                        <span style={{ textDecoration: 'line-through', color: 'var(--faint)', marginRight: 6 }}>{inr(l.originalAmount)}</span>
                      )}
                      <span style={{ fontWeight: l.originalAmount != null ? 700 : 400, color: l.originalAmount != null ? 'var(--amber)' : undefined }}>
                        {inr(l.amount)}
                      </span>
                      {props.canOverride && !editing && (
                        <button type="button" onClick={() => { setEditLine(l.lineNo); setEditAmt(String(l.amount)); setEditReason(''); props.onError('') }}
                          style={{ ...ghostBtn, marginLeft: 8, padding: '4px 8px', fontSize: '0.74rem', minHeight: 32 }}>
                          Override
                        </button>
                      )}
                      {l.overrideReason && (
                        <div style={{ fontSize: '0.68rem', color: 'var(--amber)', marginTop: 2 }}>Overridden — {l.overrideReason}</div>
                      )}
                      {editing && (
                        <div style={{ display: 'flex', gap: 6, alignItems: 'center', marginTop: 6, background: 'var(--amber-bg)', border: '1px solid #EAD3AE', borderRadius: 8, padding: 8, flexWrap: 'wrap' }}>
                          <input type="number" value={editAmt} onChange={(e) => setEditAmt(e.target.value)}
                            style={{ ...inputStyle, width: 100, padding: '5px 8px' }} />
                          <input value={editReason} onChange={(e) => setEditReason(e.target.value)} placeholder="Reason (required)"
                            style={{ ...inputStyle, flex: 1, minWidth: 140, padding: '5px 8px' }} />
                          <button type="button" onClick={() => save(l.lineNo)} disabled={props.busy} style={{ ...primaryBtn(props.busy), padding: '5px 10px', fontSize: '0.76rem', minHeight: 32 }}>Save</button>
                          <button type="button" onClick={() => setEditLine(null)} style={{ ...ghostBtn, padding: '5px 9px', fontSize: '0.76rem', minHeight: 32 }}>Cancel</button>
                        </div>
                      )}
                    </td>
                    <td style={lcell}>{l.bankName ?? '—'}</td>
                    <td style={{ ...lcell, fontFamily: 'Consolas, monospace', fontSize: '0.74rem' }}>{l.transactionRef ?? '—'}</td>
                    <td style={lcell}>{l.remark ?? '—'}</td>
                  </tr>
                )
              })}
              {lines.length === 0 && (
                <tr><td colSpan={6} style={{ ...lcell, color: 'var(--faint)' }}>No lines yet.</td></tr>
              )}
            </tbody>
          </table>
        </div>
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 26, borderTop: '2px solid var(--line)', marginTop: 8, paddingTop: 10, flexWrap: 'wrap' }}>
          <div style={{ textAlign: 'right' }}>
            <div style={{ fontSize: '0.68rem', textTransform: 'uppercase', color: 'var(--muted)' }}>{expense ? 'Total expenses' : 'Total received'}</div>
            <div style={{ fontSize: '1rem', fontWeight: 800, fontVariantNumeric: 'tabular-nums', color: expense ? 'var(--red)' : 'var(--green)' }}>{inr(total)}</div>
          </div>
          {!expense && doc.invoiceAmount != null && (
            <div style={{ textAlign: 'right' }}>
              <div style={{ fontSize: '0.68rem', textTransform: 'uppercase', color: 'var(--muted)' }}>Pending</div>
              <div style={{ fontSize: '1rem', fontWeight: 800, fontVariantNumeric: 'tabular-nums', color: doc.pendingAmount > 0 ? 'var(--amber)' : 'var(--muted)' }}>{inr(doc.pendingAmount)}</div>
            </div>
          )}
        </div>
      </div>

      <div style={{ ...card, marginBottom: 14 }}>
        <h3 style={{ fontSize: '0.9rem', fontWeight: 700, marginBottom: 10 }}>History</h3>
        <div style={{ borderLeft: '2px solid var(--line)', marginLeft: 4, paddingLeft: 14, display: 'flex', flexDirection: 'column', gap: 10 }}>
          {doc.history.map((h, i) => (
            <div key={i} style={{ fontSize: '0.8rem' }}>
              <strong>{h.actor}</strong> — {h.action}{h.note ? `: ${h.note}` : ''}
              <div style={{ fontSize: '0.68rem', color: 'var(--faint)' }}>{fmtDateTime(h.at)}</div>
            </div>
          ))}
          {doc.history.length === 0 && <div style={{ fontSize: '0.78rem', color: 'var(--faint)' }}>No history yet.</div>}
        </div>
      </div>
    </>
  )
}

/** A cash movement has no lines — a compact header + history view for the review panes. */
export function CashRecordCard({ doc }: { doc: CashDocument }) {
  const inbound = doc.direction === 'IN'
  return (
    <>
      <div style={{ ...card, marginBottom: 14 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap', marginBottom: 10 }}>
          <h2 style={{ fontSize: '1.05rem', color: 'var(--navy)' }}>
            {inbound ? 'Cash IN from bank' : 'Cash OUT to bank'}
          </h2>
          <span style={{ fontFamily: 'Consolas, monospace', fontSize: '0.78rem', color: 'var(--navy2)' }}>
            {doc.documentNo ?? 'draft'}
          </span>
          <span style={{ flex: 1 }} />
          <Badge tone={wfTone(doc.workflowStatus)}>{doc.workflowStatus}</Badge>
          <span style={{
            fontSize: '0.7rem', fontWeight: 800, padding: '2px 8px', borderRadius: 5,
            background: inbound ? 'var(--green-bg)' : 'var(--red-bg)',
            color: inbound ? 'var(--green)' : 'var(--red)',
          }}>
            {doc.direction}
          </span>
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 220px), 1fr))', gap: '2px 26px' }}>
          <Kv k="Date" v={fmtDate(doc.transactionDate)} />
          <Kv k="Amount" v={inr(doc.amount)} />
          <Kv k="Branch" v={doc.branchCode ?? '—'} />
          <Kv k="Bank" v={doc.bankName ?? '—'} />
          <Kv k="Transaction ID" v={doc.transactionRef ?? '—'} />
          <Kv k="Remark" v={doc.remark ?? '—'} />
        </div>
      </div>

      <div style={{ ...card, marginBottom: 14 }}>
        <h3 style={{ fontSize: '0.9rem', fontWeight: 700, marginBottom: 10 }}>History</h3>
        <div style={{ borderLeft: '2px solid var(--line)', marginLeft: 4, paddingLeft: 14, display: 'flex', flexDirection: 'column', gap: 10 }}>
          {doc.history.map((h, i) => (
            <div key={i} style={{ fontSize: '0.8rem' }}>
              <strong>{h.actor}</strong> — {h.action}{h.note ? `: ${h.note}` : ''}
              <div style={{ fontSize: '0.68rem', color: 'var(--faint)' }}>{fmtDateTime(h.at)}</div>
            </div>
          ))}
          {doc.history.length === 0 && <div style={{ fontSize: '0.78rem', color: 'var(--faint)' }}>No history yet.</div>}
        </div>
      </div>
    </>
  )
}

/** The Query / Reject text box shared by both action bars. */
export function QueryRejectBox(props: {
  kind: 'query' | 'reject'
  text: string
  busy: boolean
  onText: (s: string) => void
  onCancel: () => void
  onSubmit: () => void
}) {
  return (
    <div style={{ background: 'var(--bg)', border: '1px solid var(--line)', borderRadius: 9, padding: 12, marginBottom: 10 }}>
      <div style={{ fontSize: '0.78rem', fontWeight: 600, color: 'var(--muted)', marginBottom: 6 }}>
        {props.kind === 'query' ? 'Question for the cashier' : 'Reason for rejection'} <span style={{ color: 'var(--red)' }}>*</span>
      </div>
      <textarea value={props.text} onChange={(e) => props.onText(e.target.value)} rows={2}
        placeholder={props.kind === 'query' ? "e.g. Amount doesn't match the invoice…" : ''}
        style={{ ...inputStyle, resize: 'vertical' }} />
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 8, flexWrap: 'wrap' }}>
        <button type="button" onClick={props.onCancel} style={{ ...ghostBtn, minHeight: 36, padding: '6px 14px' }}>Cancel</button>
        <button type="button" onClick={props.onSubmit} disabled={props.busy}
          style={{ ...ghostBtn, color: props.kind === 'query' ? 'var(--amber)' : 'var(--red)', fontWeight: 700, minHeight: 36, padding: '6px 14px' }}>
          {props.kind === 'query' ? 'Send query' : 'Reject'}
        </button>
      </div>
    </div>
  )
}

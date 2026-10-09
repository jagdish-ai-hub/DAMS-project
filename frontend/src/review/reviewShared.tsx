import { useEffect, useState, type ReactNode } from 'react'
import type { DocumentHistoryEntry, ReceiveDocument, SettlementLine } from '../api/receipts'
import { reviewApi, type Reviewer } from '../api/review'
import type { ExpenseDocument, ExpenseLine } from '../api/expenses'
import type { CashDocument } from '../api/cash'
import type { MasterRow } from '../api/masters'
import { BusinessStatusSelect } from '../shared/BusinessStatusSelect'
import AttachmentsPanel, { type LineTarget } from '../cashier/AttachmentsPanel'
import { receiptsApi } from '../api/receipts'
import { expensesApi } from '../api/expenses'
import { card, Badge, ghostBtn, primaryBtn, inputStyle, inr, fmtDate, fmtDateTime } from '../shell/ui'
import { actorLabel, ROLE_LABEL } from '../auth/roleLabels'
import type { AuthUser, Role } from '../auth/AuthContext'

/** Shared pieces for the Accountant review queue and the Finance Manager queue. */

export type AnyDoc = ReceiveDocument | ExpenseDocument

export function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

export const isExpense = (d: AnyDoc): d is ExpenseDocument => 'expenseCategoryName' in d

/** The fields every document type (receipt, expense, cash) carries for maker-checker. */
export interface MakerDoc {
  createdBy: number
  lastModifiedBy: number | null
  branchId: number
  branchCode: string | null
  history: DocumentHistoryEntry[]
}

/**
 * Maker-checker mirror (AGENT.md): the server refuses review actions on an entry the caller
 * created or last modified, so the screens hide them to avoid a dead-end click. An Owner is the
 * one exception (rev 59) - they may act as every role, so they may review their own entries.
 * It keys on `primaryRole` (the user's own role), not the acting role: a switched Accountant or
 * Finance Manager is still blocked. The server stays authoritative.
 */
export function isMakerOf(user: AuthUser | null, doc: MakerDoc): boolean {
  if (!user || user.primaryRole === 'OWNER') return false
  return user.userId === doc.createdBy
    || (doc.lastModifiedBy != null && user.userId === doc.lastModifiedBy)
}

function joinNames(names: string[]): string {
  const shown = names.slice(0, 3)
  const more = names.length - shown.length
  if (more > 0) return `${shown.join(', ')} and ${more} more`
  return shown.length > 1 ? `${shown.slice(0, -1).join(', ')} or ${shown[shown.length - 1]}` : shown[0]
}

/**
 * Shown in place of the action buttons when maker-checker blocks the signed-in user (rev 59).
 * Says who entered it and in which role, and who can clear it, so it is never a dead end.
 * The reviewer list is advisory: if it cannot be loaded the first sentence still stands alone.
 */
export function MakerBlockedNote({ doc, step }: { doc: MakerDoc; step: Extract<Role, 'ACCOUNTANT' | 'FINANCE_MANAGER'> }) {
  const [reviewers, setReviewers] = useState<Reviewer[] | null>(null)
  const verb = step === 'ACCOUNTANT' ? 'verify' : 'approve'
  const who = step === 'ACCOUNTANT' ? 'accountant' : 'Finance Manager'

  // The role they were acting in when they made it, from the entry's own CREATED event.
  const enteredAs = doc.history.find((h) => h.action === 'Created')?.actorRole
  const roleText = enteredAs ? ` as ${ROLE_LABEL[enteredAs as Role] ?? enteredAs}` : ''

  const { branchId, createdBy, lastModifiedBy } = doc
  useEffect(() => {
    let live = true
    const makers = [createdBy, lastModifiedBy].filter((id): id is number => id != null)
    reviewApi.reviewers(branchId, step, makers)
      .then(({ data }) => { if (live) setReviewers(data) })
      .catch(() => { if (live) setReviewers(null) })
    return () => { live = false }
  }, [branchId, createdBy, lastModifiedBy, step])

  const where = doc.branchCode ? ` at ${doc.branchCode}` : ''
  return (
    <>
      You entered this{roleText} (or edited it last), so you can&rsquo;t {verb} it yourself &mdash; maker-checker needs a different person.
      {reviewers && reviewers.length > 0 && (
        <div style={{ marginTop: 6, color: 'var(--ink)' }}>
          <strong>{joinNames(reviewers.map((r) => r.name))}</strong> can {verb} it.
        </div>
      )}
      {reviewers && reviewers.length === 0 && (
        <div style={{ marginTop: 6, color: 'var(--ink)' }}>
          No other {who}{where} is set up yet &mdash; ask your Owner to add one in Team &amp; Branches.
        </div>
      )}
    </>
  )
}

/** The workflow states in which a reviewer may change an expense's business status (rev 58) — mirrors the server. */
export const EXPENSE_STATUS_EDITABLE = new Set(['SUBMITTED', 'VERIFIED', 'APPROVED', 'FM_QUERIED'])

export function wfTone(wf: string): 'green' | 'amber' | 'gray' | 'red' {
  if (wf === 'QUERIED' || wf === 'FM_QUERIED') return 'amber'
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

/** Mirrors the server: a receipt is frozen once approved or rejected; an expense once approved, closed or rejected. */
function docFrozen(doc: AnyDoc): boolean {
  return isExpense(doc)
    ? doc.workflowStatus === 'APPROVED' || doc.workflowStatus === 'CLOSED' || doc.workflowStatus === 'REJECTED'
    : doc.workflowStatus === 'APPROVED' || doc.workflowStatus === 'REJECTED'
}

const lcell = { padding: '8px 6px', borderTop: '1px solid var(--line)', fontSize: '0.82rem', verticalAlign: 'top' as const }

/**
 * The record body every reviewer sees — header, lines (with an inline Override box when
 * {@code canOverride}), totals and history. The action bar is supplied by the caller.
 *
 * Pass {@code statusOptions} + {@code onStatusChange} to make the business status editable in
 * place — a receipt's job-card status, or (rev 58) an expense's own status. Each caller passes
 * the master list that matches the document.
 *
 * Pass {@code onOverrideInvoice} to add the same Override affordance to the invoice amount
 * that settlement lines already have. Receive documents only — an expense has no invoice
 * amount. Gated by {@code canOverride}, same as line overrides.
 */
export function RecordCard(props: {
  doc: AnyDoc
  canOverride: boolean
  busy: boolean
  onOverride: (lineNo: number, amount: number, reason: string) => Promise<void>
  onError: (msg: string) => void
  statusOptions?: MasterRow[]
  onStatusChange?: (statusId: number) => Promise<void>
  onOverrideInvoice?: (amount: number, reason: string) => Promise<void>
  /** The Accountant may add documents (and notes) from here (rev 72); everyone else only views them. */
  canUploadDocs?: boolean
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
  const [savingStatus, setSavingStatus] = useState(false)
  const [editingInvoice, setEditingInvoice] = useState(false)
  const [invoiceAmt, setInvoiceAmt] = useState('')
  const [invoiceReason, setInvoiceReason] = useState('')

  const canEditStatus = props.statusOptions != null && props.onStatusChange != null
  const canOverrideInvoice = !expense && props.canOverride && props.onOverrideInvoice != null

  async function changeStatus(statusId: number) {
    if (!props.onStatusChange) return
    setSavingStatus(true)
    try {
      await props.onStatusChange(statusId)
    } finally {
      setSavingStatus(false)
    }
  }

  async function save(lineNo: number) {
    const amount = Number(editAmt)
    if (!(amount > 0)) { props.onError('Enter an amount greater than 0'); return }
    if (!editReason.trim()) { props.onError('A reason is required to override the amount'); return }
    await props.onOverride(lineNo, amount, editReason.trim())
    setEditLine(null)
  }

  async function saveInvoice() {
    const amount = Number(invoiceAmt)
    if (!(amount > 0)) { props.onError('Enter an amount greater than 0'); return }
    if (!invoiceReason.trim()) { props.onError('A reason is required to override the amount'); return }
    if (!props.onOverrideInvoice) return
    await props.onOverrideInvoice(amount, invoiceReason.trim())
    setEditingInvoice(false)
  }

  return (
    <>
      <div style={{ ...card, marginBottom: 14 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap', marginBottom: 10 }}>
          <h2 style={{ fontSize: '1.05rem', color: 'var(--navy)' }}>{party ?? '—'}</h2>
          <span style={{ fontFamily: 'Consolas, monospace', fontSize: '0.78rem', color: 'var(--navy2)' }}>{docNo}</span>
          <span style={{ flex: 1 }} />
          <Badge tone={wfTone(doc.workflowStatus)}>{doc.workflowStatus}</Badge>
          {canEditStatus
            ? (
              <BusinessStatusSelect
                statuses={props.statusOptions ?? []}
                value={doc.businessStatusId}
                currentName={doc.businessStatusName}
                disabled={props.busy || savingStatus}
                onChange={changeStatus}
                style={{ ...inputStyle, width: 'auto', minWidth: 170, padding: '5px 9px', fontSize: '0.78rem' }}
              />
            )
            : <Badge tone="gray">{doc.businessStatusName ?? '—'}</Badge>}
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 220px), 1fr))', gap: '2px 26px' }}>
          <Kv k={expense ? 'Expenses category' : 'Category'} v={expense ? doc.expenseCategoryName ?? '—' : doc.categoryName ?? '—'} />
          {/* Ooriba ID = a DAMS-Receive-ID: a receipt's own, or the receipt(s) an expense is linked to. */}
          <Kv k="Ooriba ID" v={(expense ? doc.receiveDocumentNos?.join(', ') : doc.documentNo) || '—'} />
          <Kv k="Vehicle #" v={doc.vehicleNo ?? '—'} />
          {!expense && <Kv k="Chassis #" v={doc.chassisNo ?? '—'} />}
          {!expense && <Kv k="Contact" v={doc.contactPhone ?? '—'} />}
          {/* rev 72: every role sees the customer type; the GST number is the B2B customer's, blank for B2C. */}
          {!expense && <Kv k="Customer type" v={doc.b2b ? 'B2B' : 'B2C'} />}
          {!expense && <Kv k="GST #" v={doc.b2b ? (doc.gstNo ?? '') : ''} />}
          <Kv k={expense ? 'Job ID / PO / SO' : 'Job card / DBM'} v={doc.dbmId ?? '—'} />
          <Kv k="Branch" v={doc.branchCode ?? '—'} />
          {expense ? (
            <Kv k="Entered" v={fmtDate(doc.createdAt)} />
          ) : (
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, padding: '6px 0', borderBottom: '1px dashed var(--line)', flexWrap: 'wrap' }}>
              <span style={{ fontSize: '0.75rem', color: 'var(--muted)' }}>Invoice amount</span>
              {!editingInvoice ? (
                <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  <span style={{ fontSize: '0.83rem', fontWeight: 600 }}>{doc.invoiceAmount != null ? inr(doc.invoiceAmount) : '—'}</span>
                  {canOverrideInvoice && (
                    <button
                      type="button"
                      onClick={() => {
                        setEditingInvoice(true)
                        setInvoiceAmt(doc.invoiceAmount != null ? String(doc.invoiceAmount) : '')
                        setInvoiceReason('')
                        props.onError('')
                      }}
                      style={{ ...ghostBtn, padding: '6px 10px', fontSize: '0.7rem', minHeight: 34 }}
                    >
                      Override
                    </button>
                  )}
                </span>
              ) : (
                <div style={{ display: 'flex', gap: 6, alignItems: 'center', background: 'var(--amber-bg)', border: '1px solid #EAD3AE', borderRadius: 8, padding: 8, flexWrap: 'wrap', width: '100%' }}>
                  <input type="number" value={invoiceAmt} onChange={(e) => setInvoiceAmt(e.target.value)}
                    style={{ ...inputStyle, width: 100, padding: '5px 8px' }} />
                  <input value={invoiceReason} onChange={(e) => setInvoiceReason(e.target.value)} placeholder="Reason (required)"
                    style={{ ...inputStyle, flex: 1, minWidth: 140, padding: '5px 8px' }} />
                  <button type="button" onClick={saveInvoice} disabled={props.busy} style={{ ...primaryBtn(props.busy), padding: '5px 10px', fontSize: '0.76rem', minHeight: 32 }}>Save</button>
                  <button type="button" onClick={() => setEditingInvoice(false)} style={{ ...ghostBtn, padding: '5px 9px', fontSize: '0.76rem', minHeight: 32 }}>Cancel</button>
                </div>
              )}
            </div>
          )}
        </div>
        {expense && doc.claimFinalAmount != null && (
          <div style={{ background: 'var(--purple-bg, #EFE7FB)', border: '1px solid #D9C7EF', borderRadius: 8, padding: '10px 12px', marginTop: 10 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', gap: 10, fontSize: '0.86rem' }}>
              <span style={{ fontWeight: 700 }}>Claim closed · Final amount recovered (locked)</span>
              <span style={{ fontWeight: 800, fontVariantNumeric: 'tabular-nums' }}>{inr(doc.claimFinalAmount)}</span>
            </div>
            <div style={{ fontSize: '0.76rem', color: doc.claimOverridden ? 'var(--amber)' : 'var(--muted)', marginTop: 4 }}>
              {doc.claimOverridden
                ? `Overridden · Final — ${doc.claimOverrideReason ?? ''} (expense total ${inr(doc.totalAmount)})`
                : 'Recovered in full — no override.'}
              {doc.claimClosedByName ? ` · closed by ${doc.claimClosedByName}` : ''}
            </div>
          </div>
        )}
        {expense && doc.needsFmApproval && doc.workflowStatus !== 'DRAFT' && (doc.preApprovalCovers ? (
          <div style={{ fontSize: '0.76rem', color: 'var(--green)', marginTop: 10 }}>
            ✓ {doc.overLimit ? 'Over the category limit' : `Status “${doc.businessStatusName ?? ''}” needs Finance Manager approval`}, but pre-approved by {doc.preApprovedByName ?? 'the Finance Manager'} for
            {' '}{inr(doc.preApprovedAmount ?? 0)} before it was submitted — it can be closed once verified, no second FM approval.
          </div>
        ) : (
          <div style={{ fontSize: '0.76rem', color: 'var(--amber)', marginTop: 10 }}>
            ⚠ {doc.overLimit ? 'Over the category limit' : `Status “${doc.businessStatusName ?? ''}”`} — needs Finance Manager approval before it can be closed
            {doc.preApprovalStatus === 'APPROVED' && doc.preApprovedAmount != null
              ? ` (it was pre-approved for ${inr(doc.preApprovedAmount)}, but the total has grown past that).`
              : '.'}
          </div>
        ))}
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
                          style={{ ...ghostBtn, marginLeft: 8, padding: '6px 10px', fontSize: '0.74rem', minHeight: 34 }}>
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

      <div style={{ ...card, marginBottom: 14 }} aria-label="Documents">
        <AttachmentsPanel
          docId={doc.id}
          noun={expense ? 'expense' : 'receipt'}
          frozen={docFrozen(doc)}
          readOnly={!props.canUploadDocs}
          allowRemove={false}
          lineTargets={lines.map<LineTarget>((l) => ({
            lineNo: l.lineNo,
            label: `Line ${l.lineNo} · ${'subCategoryName' in l ? l.subCategoryName : l.settlementModeName} ${inr(l.amount)}`,
          }))}
          api={expense ? expensesApi : receiptsApi}
          ensureDraft={async () => doc.id}
        />
      </div>

      <div style={{ ...card, marginBottom: 14 }}>
        <h3 style={{ fontSize: '0.9rem', fontWeight: 700, marginBottom: 10 }}>History</h3>
        <div style={{ borderLeft: '2px solid var(--line)', marginLeft: 4, paddingLeft: 14, display: 'flex', flexDirection: 'column', gap: 10 }}>
          {doc.history.map((h, i) => (
            <div key={i} style={{ fontSize: '0.8rem' }}>
              <strong>{actorLabel(h.actor, h.actorRole)}</strong> — {h.action}{h.note ? `: ${h.note}` : ''}
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
              <strong>{actorLabel(h.actor, h.actorRole)}</strong> — {h.action}{h.note ? `: ${h.note}` : ''}
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

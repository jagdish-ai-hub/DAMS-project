import { useCallback, useEffect, useMemo, useState } from 'react'
import { receiptsApi } from '../api/receipts'
import { expensesApi } from '../api/expenses'
import { cashApi, type CashDocument } from '../api/cash'
import { reviewApi, type ReviewQueueItem, type ReviewType } from '../api/review'
import { card, ErrorBanner, ghostBtn, primaryBtn, Skeleton, SkeletonRows, inr } from '../shell/ui'
import { RecordCard, CashRecordCard, QueryRejectBox, Tag, apiError, type AnyDoc } from '../review/reviewShared'
import GlobalSearch from '../shared/GlobalSearch'

/**
 * Accountant review queue (intial ui prototypes/review-close.html, Accountant view). Two
 * panes: the SUBMITTED queue on the left, and either an overview (nothing selected) or the
 * full record with verify / query / reject / per-line override on the right. Expenses also
 * get an explicit Close; cash movements verify / query / reject (no lines).
 */

type DetailDoc = AnyDoc | CashDocument

const queueFor = (t: ReviewType) =>
  t === 'receipt' ? reviewApi.receiptQueue() : t === 'expense' ? reviewApi.expenseQueue() : reviewApi.cashQueue()
const detailFor = (t: ReviewType, id: number) =>
  t === 'receipt' ? receiptsApi.get(id) : t === 'expense' ? expensesApi.get(id) : cashApi.get(id)

export default function ReviewQueuePage() {
  const [type, setType] = useState<ReviewType>('receipt')
  const [items, setItems] = useState<ReviewQueueItem[] | null>(null)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [doc, setDoc] = useState<DetailDoc | null>(null)
  const [error, setError] = useState('')
  const [flash, setFlash] = useState('')
  const [tick, setTick] = useState(0)

  const reload = useCallback(() => setTick((n) => n + 1), [])

  useEffect(() => {
    let live = true
    setError('')
    queueFor(type)
      .then(({ data }) => { if (live) setItems(data) })
      .catch((e) => { if (live) setError(apiError(e, 'Could not load the review queue.')) })
    return () => { live = false }
  }, [type, tick])

  useEffect(() => {
    if (selectedId == null) { setDoc(null); return }
    let live = true
    detailFor(type, selectedId)
      .then(({ data }) => { if (live) setDoc(data as DetailDoc) })
      .catch((e) => { if (live) setError(apiError(e, 'Could not load that document.')) })
    return () => { live = false }
  }, [selectedId, type, tick])

  function pickType(t: ReviewType) {
    setType(t)
    setSelectedId(null)
    setDoc(null)
    setFlash('')
  }

  function afterAction(message: string, keepOpen: boolean) {
    setFlash(message)
    setTimeout(() => setFlash(''), 3000)
    if (!keepOpen) setSelectedId(null)
    reload()
  }

  return (
    <div style={{ maxWidth: 1180, margin: '0 auto' }}>
      <h1 style={{ fontSize: '1.3rem', color: 'var(--navy)', marginBottom: 4 }}>Review Queue</h1>
      <div style={{ fontSize: '0.85rem', color: 'var(--muted)', marginBottom: 14 }}>
        Verify, query, reject or adjust each submitted entry. A verified entry moves on to the Finance Manager.
      </div>

      <GlobalSearch />

      <ErrorBanner message={error} />
      {flash && (
        <div className="dams-anim-notice" style={{ background: 'var(--green-bg)', color: 'var(--green)', borderRadius: 8, padding: '8px 12px', fontSize: '0.82rem', marginBottom: 12 }}>
          {flash}
        </div>
      )}

      <div style={{
        display: 'grid', gridTemplateColumns: '340px 1fr', gap: 0,
        border: '1px solid var(--line)', borderRadius: 'var(--radius)', overflow: 'hidden',
        background: 'var(--surface)', minHeight: '68vh',
      }}>
        <QueuePane type={type} items={items} selectedId={selectedId} onType={pickType} onSelect={setSelectedId} />
        <div style={{ borderLeft: '1px solid var(--line)', padding: '20px 24px', overflowY: 'auto' }}>
          {selectedId == null
            ? <Overview type={type} items={items ?? []} onSelect={setSelectedId} />
            : doc == null
              ? <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                  <Skeleton width={200} height={20} />
                  <SkeletonRows rows={5} />
                </div>
              : <RecordDetail type={type} doc={doc} onDone={afterAction} />}
        </div>
      </div>
    </div>
  )
}

// ───────────────────────────── Queue pane ─────────────────────────────

function QueuePane(props: {
  type: ReviewType
  items: ReviewQueueItem[] | null
  selectedId: number | null
  onType: (t: ReviewType) => void
  onSelect: (id: number) => void
}) {
  const { items } = props
  return (
    <div style={{ display: 'flex', flexDirection: 'column', overflowY: 'auto' }}>
      <div style={{ display: 'flex', padding: 12, gap: 4 }}>
        {(['receipt', 'expense', 'cash'] as const).map((t) => (
          <button key={t} type="button" onClick={() => props.onType(t)}
            style={{
              flex: 1, border: '1px solid var(--line)', borderRadius: 7, padding: '7px 0',
              fontSize: '0.78rem', fontWeight: 700, cursor: 'pointer',
              background: props.type === t ? 'var(--navy)' : 'var(--surface)',
              color: props.type === t ? '#fff' : 'var(--muted)',
            }}>
            {t === 'receipt' ? 'Receipts' : t === 'expense' ? 'Expenses' : 'Cash'}
          </button>
        ))}
      </div>
      <div style={{ padding: '4px 14px 8px', fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--faint)', fontWeight: 700 }}>
        Awaiting your review
        <span style={{ background: 'var(--amber-bg)', color: 'var(--amber)', borderRadius: 999, fontSize: '0.66rem', padding: '1px 7px', marginLeft: 6 }}>
          {items?.length ?? 0}
        </span>
      </div>

      {items == null && <div style={{ padding: 14 }}><SkeletonRows rows={5} height={52} /></div>}
      {items != null && items.length === 0 && (
        <p style={{ padding: 14, color: 'var(--faint)', fontSize: '0.82rem' }}>Nothing waiting on you — the queue is clear.</p>
      )}

      {(items ?? []).map((it) => (
        <QueueRow key={it.id} it={it} selected={props.selectedId === it.id} onSelect={() => props.onSelect(it.id)} />
      ))}
    </div>
  )
}

export function QueueRow({ it, selected, onSelect }: { it: ReviewQueueItem; selected: boolean; onSelect: () => void }) {
  return (
    <button type="button" onClick={onSelect}
      style={{
        textAlign: 'left', border: 'none', borderBottom: '1px solid var(--line)', cursor: 'pointer',
        borderLeft: `3px solid ${selected ? 'var(--navy)' : 'transparent'}`,
        background: selected ? 'var(--navy3)' : 'transparent', padding: '11px 14px',
        display: 'flex', flexDirection: 'column', gap: 3,
      }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
        <span style={{ fontFamily: 'Consolas, monospace', fontSize: '0.7rem', fontWeight: 700, color: 'var(--navy2)' }}>
          {it.documentNo ?? 'draft'}
        </span>
        <span style={{ marginLeft: 'auto', fontSize: '0.68rem', color: 'var(--faint)' }}>{it.branchCode}</span>
      </div>
      <div style={{ fontSize: '0.85rem', fontWeight: 600 }}>{it.partyName}</div>
      <div style={{ fontSize: '0.74rem', color: 'var(--muted)' }}>
        {it.categoryName} · <strong style={{ fontVariantNumeric: 'tabular-nums' }}>{inr(it.amount)}</strong>
      </div>
      {(it.overLimit || it.hasOverride) && (
        <div style={{ display: 'flex', gap: 5, marginTop: 2 }}>
          {it.hasOverride && <Tag>Overridden</Tag>}
          {it.overLimit && <Tag>Above limit</Tag>}
        </div>
      )}
    </button>
  )
}

// ───────────────────────────── Overview panel ─────────────────────────────

function Overview(props: { type: ReviewType; items: ReviewQueueItem[]; onSelect: (id: number) => void }) {
  const { items } = props
  const total = items.reduce((a, r) => a + r.amount, 0)
  const byBranch = useMemo(() => {
    const m = new Map<string, number>()
    items.forEach((r) => m.set(r.branchCode, (m.get(r.branchCode) ?? 0) + 1))
    const max = Math.max(1, ...m.values())
    return [...m.entries()].map(([code, n]) => ({ code, n, pct: Math.round((n / max) * 100) }))
  }, [items])

  const label = props.type === 'receipt' ? 'receipts' : props.type === 'expense' ? 'expenses' : 'cash movements'

  return (
    <div>
      <h2 style={{ fontSize: '1.1rem', color: 'var(--navy)' }}>Accountant overview</h2>
      <div style={{ fontSize: '0.82rem', color: 'var(--muted)', marginBottom: 16 }}>Reviewing {label}</div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 18 }}>
        <Stat label="Awaiting your review" value={String(items.length)} sub={items.length === 1 ? 'item' : 'items'} />
        <Stat label="Total value pending" value={inr(total)} sub="across your branches" accent />
      </div>

      {items.length === 0 ? (
        <div style={{ ...card, color: 'var(--faint)', fontSize: '0.85rem', textAlign: 'center' }}>
          Nothing waiting on you right now — the {label} queue is clear.
        </div>
      ) : (
        <>
          <div style={{ ...card, marginBottom: 14 }}>
            <h3 style={{ fontSize: '0.9rem', fontWeight: 700, marginBottom: 10 }}>By branch</h3>
            {byBranch.map((b) => (
              <div key={b.code} style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '5px 0', fontSize: '0.82rem' }}>
                <span style={{ width: 46, fontWeight: 600, color: 'var(--muted)' }}>{b.code}</span>
                <span style={{ flex: 1, background: 'var(--bg)', borderRadius: 5, height: 10, overflow: 'hidden' }}>
                  <span style={{ display: 'block', height: '100%', width: `${b.pct}%`, background: 'var(--navy2)' }} />
                </span>
                <span style={{ width: 26, textAlign: 'right', fontWeight: 700 }}>{b.n}</span>
              </div>
            ))}
          </div>
          <div style={{ ...card }}>
            <h3 style={{ fontSize: '0.9rem', fontWeight: 700, marginBottom: 4 }}>Needs your attention</h3>
            <div style={{ fontSize: '0.74rem', color: 'var(--faint)', marginBottom: 8 }}>click to open</div>
            {items.slice(0, 6).map((r) => (
              <button key={r.id} type="button" onClick={() => props.onSelect(r.id)}
                style={{
                  display: 'flex', alignItems: 'center', gap: 12, width: '100%', textAlign: 'left',
                  border: 'none', borderTop: '1px solid var(--line)', background: 'transparent',
                  padding: '10px 0', cursor: 'pointer', fontSize: '0.84rem',
                }}>
                <span style={{ fontFamily: 'Consolas, monospace', fontSize: '0.72rem', color: 'var(--navy2)' }}>{r.documentNo ?? 'draft'}</span>
                <span style={{ flex: 1, fontWeight: 700 }}>{r.partyName}</span>
                <span style={{ fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>{inr(r.amount)}</span>
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  )
}

function Stat({ label, value, sub, accent }: { label: string; value: string; sub: string; accent?: boolean }) {
  return (
    <div style={{ ...card, borderTop: `3px solid ${accent ? 'var(--amber)' : 'var(--navy2)'}` }}>
      <div style={{ fontSize: '0.7rem', textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--muted)', fontWeight: 600 }}>{label}</div>
      <div style={{ fontSize: '1.4rem', fontWeight: 800, marginTop: 4, fontVariantNumeric: 'tabular-nums' }}>{value}</div>
      <div style={{ fontSize: '0.76rem', color: 'var(--faint)' }}>{sub}</div>
    </div>
  )
}

// ───────────────────────────── Record detail ─────────────────────────────

function RecordDetail(props: { type: ReviewType; doc: DetailDoc; onDone: (message: string, keepOpen: boolean) => void }) {
  const { type, doc } = props
  const cash = type === 'cash'
  const expense = !cash && 'expenseCategoryName' in doc
  const wf = doc.workflowStatus
  const docNo = doc.documentNo ?? 'draft'

  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [box, setBox] = useState<'query' | 'reject' | null>(null)
  const [boxText, setBoxText] = useState('')

  const canReview = wf === 'SUBMITTED'
  const canClose = expense && (wf === 'VERIFIED' || wf === 'APPROVED')
  const overLimit = expense ? (doc as { overLimit: boolean }).overLimit : false

  async function run(fn: () => Promise<unknown>, message: string, keepOpen = false) {
    setBusy(true)
    setError('')
    try {
      await fn()
      props.onDone(message, keepOpen)
    } catch (e) {
      setError(apiError(e, 'The action could not be completed.'))
    } finally {
      setBusy(false)
    }
  }

  function submitBox() {
    const text = boxText.trim()
    if (!text) { setError(box === 'query' ? 'Type the question for the cashier' : 'A reason is required'); return }
    if (box === 'query') run(() => reviewApi.query(type, doc.id, text), `${docNo} queried — sent back to the cashier`)
    else run(() => reviewApi.reject(type, doc.id, text), `${docNo} rejected`)
  }

  return (
    <div>
      <ErrorBanner message={error} />
      {cash
        ? <CashRecordCard doc={doc as CashDocument} />
        : (
          <RecordCard
            doc={doc as AnyDoc}
            canOverride={canReview}
            busy={busy}
            onError={setError}
            onOverride={(lineNo, amount, reason) =>
              run(() => reviewApi.overrideLine(type, doc.id, lineNo, amount, reason),
                `Line ${lineNo} overridden — shows on this record permanently`, true)}
          />
        )}

      {!canReview && !canClose ? (
        <div style={{ ...card, textAlign: 'center', color: 'var(--faint)', fontSize: '0.84rem' }}>
          No action needed from you right now.
        </div>
      ) : (
        <div style={{ ...card }}>
          {box && (
            <QueryRejectBox
              kind={box}
              text={boxText}
              busy={busy}
              onText={setBoxText}
              onCancel={() => { setBox(null); setBoxText(''); setError('') }}
              onSubmit={submitBox}
            />
          )}
          <div style={{ display: 'flex', gap: 10, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
            {canReview && (
              <>
                <button type="button" onClick={() => { setBox(box === 'query' ? null : 'query'); setBoxText(''); setError('') }}
                  style={{ ...ghostBtn, color: 'var(--amber)' }}>Query</button>
                <button type="button" onClick={() => { setBox(box === 'reject' ? null : 'reject'); setBoxText(''); setError('') }}
                  style={{ ...ghostBtn, color: 'var(--red)' }}>Reject</button>
                <button type="button" onClick={() => run(() => reviewApi.verify(type, doc.id), `${docNo} verified — moved to Finance Manager`)}
                  disabled={busy} style={primaryBtn(busy)}>Verify</button>
              </>
            )}
            {canClose && (
              <button type="button" onClick={() => run(() => reviewApi.closeExpense(doc.id), `${docNo} closed`)}
                disabled={busy || (overLimit && wf !== 'APPROVED')}
                style={primaryBtn(busy || (overLimit && wf !== 'APPROVED'))}
                title={overLimit && wf !== 'APPROVED' ? 'Over-limit expense — needs Finance Manager approval first' : undefined}>
                Close expense
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

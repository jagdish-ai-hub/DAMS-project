import { useEffect, useRef, useState, type CSSProperties } from 'react'
import { searchApi, type SearchHit } from '../api/search'
import { customersApi, type CustomerHistory } from '../api/customers'
import ViewReceiptsModal from '../cashier/ViewReceiptsModal'
import { Modal, ErrorBanner, SkeletonRows, ghostBtn, inr, initials, fmtDateShort } from '../shell/ui'

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

/**
 * Read-only global search for reviewers (Accountant / Finance Manager). Same backend as the
 * cashier's home search (`GET /api/v1/search`, already branch-scoped by role); picking a hit
 * opens the customer's history in a drawer — no create / pay actions, just a look.
 */
export default function GlobalSearch() {
  const [q, setQ] = useState('')
  const [hits, setHits] = useState<SearchHit[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [openId, setOpenId] = useState<number | null>(null)
  const seq = useRef(0)
  const boxRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const query = q.trim()
    if (query.length < 2) { setHits(null); setError(''); setLoading(false); return }
    const mine = ++seq.current
    setLoading(true)
    const t = setTimeout(async () => {
      try {
        const { data } = await searchApi.query(query)
        if (mine === seq.current) { setHits(data.hits); setError('') }
      } catch (e) {
        if (mine === seq.current) setError(apiError(e, 'Search failed. Try again.'))
      } finally {
        if (mine === seq.current) setLoading(false)
      }
    }, 250)
    return () => clearTimeout(t)
  }, [q])

  useEffect(() => {
    function onDown(e: MouseEvent) {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) setHits(null)
    }
    document.addEventListener('mousedown', onDown)
    return () => document.removeEventListener('mousedown', onDown)
  }, [])

  return (
    <div ref={boxRef} style={{ position: 'relative', width: 340, maxWidth: '100%', flexShrink: 0 }}>
      <span style={{
        position: 'absolute', left: 11, top: '50%', transform: 'translateY(-50%)',
        color: 'var(--faint)', fontSize: '0.95rem', pointerEvents: 'none',
      }}>
        ⌕
      </span>
      <input
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder="Search customers, vehicles, job cards…"
        autoComplete="off"
        aria-label="Search customers and transactions"
        style={{
          width: '100%', border: '1.5px solid var(--line)', borderRadius: 9,
          padding: '8px 28px 8px 32px', fontSize: '0.84rem',
          background: 'var(--surface)', outline: 'none', boxShadow: 'var(--shadow)',
        }}
      />
      {q && (
        <button
          type="button"
          onClick={() => { setQ(''); setHits(null) }}
          aria-label="Clear search"
          style={{
            position: 'absolute', right: 8, top: '50%', transform: 'translateY(-50%)',
            background: 'none', border: 'none', color: 'var(--muted)', cursor: 'pointer',
            fontSize: '0.85rem', padding: '4px 6px', lineHeight: 1,
          }}
        >
          ✕
        </button>
      )}

      {error && <ErrorBanner message={error} />}

      {(hits != null || loading) && (
        <div
          className="dams-anim-notice"
          style={{
            position: 'absolute', top: 'calc(100% + 6px)', right: 0, width: 'min(380px, 92vw)', zIndex: 40,
            background: 'var(--surface)', border: '1px solid var(--line)', borderRadius: 10,
            boxShadow: 'var(--shadow-lift)', overflow: 'hidden', maxHeight: 380, overflowY: 'auto',
          }}
        >
          {hits == null && loading && (
            <div style={{ padding: 14, textAlign: 'center', color: 'var(--faint)', fontSize: '0.82rem' }}>
              Searching…
            </div>
          )}
          {hits != null && hits.length === 0 && (
            <div style={{ padding: 16, textAlign: 'center', color: 'var(--faint)', fontSize: '0.84rem' }}>
              Nothing matches “{q.trim()}”
            </div>
          )}
          {hits != null && hits.map((h) => (
            <button
              key={h.customerId}
              type="button"
              onClick={() => { setOpenId(h.customerId); setHits(null); setQ('') }}
              style={{
                display: 'flex', alignItems: 'center', gap: 10, width: '100%', textAlign: 'left',
                padding: '10px 14px', background: 'transparent', border: 'none',
                borderTop: '1px solid var(--line)', cursor: 'pointer',
              }}
            >
              <span style={{
                width: 30, height: 30, borderRadius: 8, background: 'var(--navy3)', color: 'var(--navy)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                fontWeight: 700, fontSize: '0.72rem', flexShrink: 0,
              }}>
                {initials(h.customerName)}
              </span>
              <span style={{ flex: 1, minWidth: 0 }}>
                <span style={{ fontSize: '0.86rem', fontWeight: 700 }}>{h.customerName}</span>
                <span style={{
                  fontSize: '0.64rem', background: 'var(--amber-bg)', color: 'var(--amber)',
                  borderRadius: 5, padding: '1px 6px', fontWeight: 700, marginLeft: 6,
                }}>
                  {h.matchField}
                </span>
                <span style={{ display: 'block', fontSize: '0.74rem', color: 'var(--muted)' }}>
                  {(h.vehicles.join(', ') || 'No vehicle')} · {h.jobCardCount} job card{h.jobCardCount === 1 ? '' : 's'}
                </span>
              </span>
              <span style={{
                fontSize: '0.76rem', fontWeight: 700, flexShrink: 0,
                color: h.totalOutstanding > 0 ? 'var(--amber)' : 'var(--green)',
              }}>
                {h.totalOutstanding > 0 ? inr(h.totalOutstanding) : 'Clear'}
              </span>
            </button>
          ))}
        </div>
      )}

      {openId != null && <CustomerDrawer customerId={openId} onClose={() => setOpenId(null)} />}
    </div>
  )
}

// ───────────────────────────── read-only history drawer ─────────────────────────────

type DocTarget = { receiptId: number; subtitle: string; frozen: boolean }

function CustomerDrawer({ customerId, onClose }: { customerId: number; onClose: () => void }) {
  const [data, setData] = useState<CustomerHistory | null>(null)
  const [error, setError] = useState('')
  const [docs, setDocs] = useState<DocTarget | null>(null)

  useEffect(() => {
    let live = true
    customersApi.history(customerId)
      .then(({ data }) => { if (live) setData(data) })
      .catch((e) => { if (live) setError(apiError(e, 'Could not load this customer.')) })
    return () => { live = false }
  }, [customerId])

  return (
    <>
      <Modal
        title={data?.customerName ?? 'Customer'}
        subtitle={data ? `${data.jobCardCount} job card${data.jobCardCount === 1 ? '' : 's'} · view only` : undefined}
        onClose={onClose}
        maxWidth={720}
      >
        <ErrorBanner message={error} />
        {data == null && !error && <SkeletonRows rows={4} />}

        {data && (
          <>
            <div style={{ fontSize: '0.8rem', color: 'var(--muted)' }}>
              {data.vehicles.map((v) => v.vehicleNo).join(', ') || 'No vehicle on file'}
              {data.phone && ` · ${data.phone}`}
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: 8 }}>
              <Stat label="Job Cards" value={String(data.jobCardCount)} />
              <Stat label="Invoiced" value={inr(data.totalInvoiced)} />
              <Stat label="Received" value={inr(data.totalReceived)} tone="green" />
              <Stat label="Outstanding" value={inr(data.totalOutstanding)} tone={data.totalOutstanding > 0 ? 'amber' : undefined} />
            </div>

            <div>
              <h4 style={sectionH}>Job cards</h4>
              {data.jobCards.length === 0 && <p style={mutedLine}>No job cards on file.</p>}
              {data.jobCards.map((j) => (
                <div key={j.id} style={{
                  display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap',
                  gap: 10, padding: '8px 0', borderTop: '1px solid var(--line)', fontSize: '0.82rem',
                }}>
                  <div>
                    <span style={{ fontWeight: 700, color: 'var(--navy)' }}>{j.reference}</span>
                    <span style={{ color: 'var(--muted)', marginLeft: 8 }}>
                      {j.categoryName}{j.businessStatusName ? ` · ${j.businessStatusName}` : ''}
                    </span>
                    {j.invoiceNo && (
                      <span style={{ color: 'var(--faint)', marginLeft: 8, fontSize: '0.74rem' }}>
                        Inv #{j.invoiceNo}
                      </span>
                    )}
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                    <div style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums', fontSize: '0.78rem' }}>
                      <div style={{ color: 'var(--green)' }}>Rcvd {inr(j.received)}</div>
                      <div style={{ color: j.balance > 0 ? 'var(--amber)' : 'var(--faint)' }}>
                        Bal {j.invoiceAmount != null ? inr(j.balance) : '—'}
                      </div>
                    </div>
                    {j.workflowStatus && (
                      <span style={{
                        fontSize: '0.66rem', fontWeight: 700, padding: '2px 8px', borderRadius: 999,
                        background: 'var(--blue-bg)', color: 'var(--blue)',
                      }}>
                        {j.workflowStatus}
                      </span>
                    )}
                    {j.receiveDocumentId != null && (
                      <button
                        type="button"
                        onClick={() => setDocs({
                          receiptId: j.receiveDocumentId!,
                          subtitle: `${j.reference} · whole receipt`,
                          frozen: j.receiveDocumentSettled || j.workflowStatus === 'REJECTED',
                        })}
                        style={{ ...ghostBtn, fontSize: '0.72rem' }}
                      >
                        View documents
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>

            <div>
              <h4 style={sectionH}>Payment timeline</h4>
              {data.timeline.length === 0 && <p style={mutedLine}>No payments recorded.</p>}
              {data.timeline.map((t) => (
                <div key={t.lineId} style={{
                  display: 'flex', gap: 10, padding: '7px 0', borderTop: '1px dashed var(--line)', fontSize: '0.8rem',
                }}>
                  <span style={{ width: 84, color: 'var(--faint)', fontSize: '0.74rem', flexShrink: 0 }}>
                    {fmtDateShort(t.date)}
                  </span>
                  <span style={{ flex: 1, color: 'var(--muted)' }}>
                    {t.description}{t.mode ? ` · ${t.mode}` : ''}
                  </span>
                  <span style={{ fontWeight: 700, color: 'var(--green)', fontVariantNumeric: 'tabular-nums' }}>
                    {inr(t.amount)}
                  </span>
                </div>
              ))}
            </div>
          </>
        )}
      </Modal>

      {docs && (
        <ViewReceiptsModal
          receiptId={docs.receiptId}
          subtitle={docs.subtitle}
          frozen={docs.frozen}
          onClose={() => setDocs(null)}
        />
      )}
    </>
  )
}

const sectionH: CSSProperties = { fontSize: '0.8rem', fontWeight: 700, color: 'var(--navy)', margin: '2px 0 4px' }
const mutedLine: CSSProperties = { fontSize: '0.8rem', color: 'var(--faint)', padding: '6px 0' }

function Stat({ label, value, tone }: { label: string; value: string; tone?: 'green' | 'amber' }) {
  const color = tone === 'green' ? 'var(--green)' : tone === 'amber' ? 'var(--amber)' : 'var(--ink)'
  return (
    <div style={{ border: '1px solid var(--line)', borderRadius: 8, padding: '8px 10px' }}>
      <div style={{ fontSize: '0.66rem', textTransform: 'uppercase', letterSpacing: '0.03em', color: 'var(--faint)' }}>{label}</div>
      <div style={{ fontSize: '0.95rem', fontWeight: 700, marginTop: 2, fontVariantNumeric: 'tabular-nums', color }}>{value}</div>
    </div>
  )
}

import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { apiMessage, sparePartsApi, type StockPage as StockData, type StockRow } from '../api/spareParts'
import { ErrorBanner, Field, Modal, SkeletonRows, card, ghostBtn, inputStyle, inr, primaryBtn, td, th, Spinner } from '../shell/ui'
import BillForm from './BillForm'
import RequestForm from './RequestForm'
import { useSpareAccess } from './SpareAccessContext'
import { FlashBanner, Hint, mono, useDebounced, useFlash } from './spareUi'

/**
 * Every part with what each branch holds: the "search here first" screen. Everyone who can open Spare Parts sees every
 * branch; the buttons on a row appear only where the server would accept them (own branch, permission ticked).
 */
export default function StockPage() {
  const { access, can } = useSpareAccess()
  const [q, setQ] = useState('')
  const dq = useDebounced(q, 250)
  const [branchId, setBranchId] = useState<number | ''>('')
  const [inStockOnly, setInStockOnly] = useState(false)
  const [data, setData] = useState<StockData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const flash = useFlash()
  const [modal, setModal] = useState<{ kind: 'bill' | 'ask' | 'correct'; row: StockRow } | null>(null)
  const [exporting, setExporting] = useState(false)

  const branches = access.branches
  const myIds = access.actBranchIds
  const myBranches = branches.filter((b) => myIds.includes(b.id))
  const canExport = access.owner || access.permissions.includes('EXPORT')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const { data: page } = await sparePartsApi.stock({ q: dq, branchId: branchId === '' ? null : branchId, inStockOnly })
      setData(page)
    } catch (e) {
      setError(apiMessage(e, 'Could not load the stock.'))
    } finally {
      setLoading(false)
    }
  }, [dq, branchId, inStockOnly])

  useEffect(() => { load() }, [load])

  const totals = useMemo(() => {
    const rows = data?.rows ?? []
    return {
      parts: rows.length,
      units: rows.reduce((n, r) => n + r.totalQuantity, 0),
      value: rows.reduce((n, r) => n + r.totalValue, 0),
    }
  }, [data])

  const qtyAt = (row: StockRow, id: number) => row.cells.find((c) => c.branchId === id)?.quantity ?? 0

  async function exportCsv() {
    setExporting(true)
    try {
      await sparePartsApi.downloadStock()
    } catch (e) {
      setError(apiMessage(e, 'Could not export the stock.'))
    } finally {
      setExporting(false)
    }
  }

  function done(message: string) {
    setModal(null)
    flash.show(message)
    load()
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <FlashBanner message={flash.message} />

      <section style={{ ...card, padding: '14px 16px' }}>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Search part ID or description"
            aria-label="Search parts"
            style={{ ...inputStyle, flex: '1 1 240px', minHeight: 40 }}
          />
          <select
            aria-label="Branch"
            value={branchId}
            onChange={(e) => setBranchId(e.target.value === '' ? '' : Number(e.target.value))}
            style={{ ...inputStyle, width: 'auto', minHeight: 40 }}
          >
            <option value="">All branches</option>
            {branches.map((b) => <option key={b.id} value={b.id}>{b.name} ({b.code})</option>)}
          </select>
          <label style={{ display: 'flex', alignItems: 'center', gap: 7, fontSize: '0.83rem', color: 'var(--ink)', minHeight: 40 }}>
            <input type="checkbox" checked={inStockOnly} onChange={(e) => setInStockOnly(e.target.checked)} />
            Only in stock
          </label>
          {canExport && (
            <button type="button" onClick={exportCsv} disabled={exporting}
              style={{ ...ghostBtn, minHeight: 40, display: 'inline-flex', alignItems: 'center', gap: 7 }}>
              {exporting && <Spinner />} Export Excel
            </button>
          )}
        </div>
        {data && (
          <div style={{ display: 'flex', gap: 22, flexWrap: 'wrap', marginTop: 12, fontSize: '0.8rem', color: 'var(--muted)' }}>
            <span><strong style={{ color: 'var(--ink)' }}>{totals.parts}</strong> parts</span>
            <span><strong style={{ color: 'var(--ink)' }}>{totals.units}</strong> units</span>
            <span><strong style={{ color: 'var(--ink)' }}>{inr(totals.value)}</strong> value</span>
          </div>
        )}
      </section>

      <ErrorBanner message={error} />

      <section style={{ ...card, padding: 0, overflow: 'hidden' }}>
        {loading && !data ? (
          <div style={{ padding: 16 }}><SkeletonRows rows={5} height={44} /></div>
        ) : (
          <div style={{ overflowX: 'auto', WebkitOverflowScrolling: 'touch' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.84rem', minWidth: 520 + branches.length * 74 }}>
              <thead>
                <tr style={{ background: 'var(--bg)' }}>
                  <th style={{ ...th, position: 'sticky', left: 0, background: 'var(--bg)', zIndex: 1, minWidth: 220 }}>Part</th>
                  {branches.map((b) => (
                    <th key={b.id} title={b.name} style={{
                      ...th, textAlign: 'right', width: 74,
                      color: myIds.includes(b.id) ? 'var(--navy)' : 'var(--muted)',
                      background: myIds.includes(b.id) ? 'var(--navy3)' : 'var(--bg)',
                    }}>
                      <span style={mono}>{b.code}</span>
                    </th>
                  ))}
                  <th style={{ ...th, textAlign: 'right' }}>Total</th>
                  <th style={{ ...th, textAlign: 'right' }}>Value</th>
                  <th style={th}></th>
                </tr>
              </thead>
              <tbody>
                {(data?.rows ?? []).map((r) => {
                  const heldAtMine = myIds.some((id) => qtyAt(r, id) > 0)
                  const heldElsewhere = r.cells.some((c) => c.quantity > 0 && !myIds.includes(c.branchId))
                  return (
                    <tr key={r.partId}>
                      <td style={{ ...td, position: 'sticky', left: 0, background: 'var(--surface)', zIndex: 1 }}>
                        <div style={{ ...mono, fontWeight: 700, color: 'var(--navy)' }}>{r.partCode}</div>
                        <div style={{ fontSize: '0.78rem', color: 'var(--muted)', maxWidth: 260 }}>{r.description}</div>
                      </td>
                      {branches.map((b) => {
                        const cell = r.cells.find((c) => c.branchId === b.id)
                        const qty = cell?.quantity ?? 0
                        return (
                          <td key={b.id} style={{ ...td, textAlign: 'right', background: myIds.includes(b.id) ? 'rgba(234,240,251,.4)' : undefined }}>
                            {qty > 0 ? (
                              <>
                                <span style={{ fontWeight: 700, color: 'var(--ink)' }}>{qty}</span>
                                {cell?.bin && <div style={{ ...mono, fontSize: '0.66rem', color: 'var(--faint)' }}>{cell.bin}</div>}
                              </>
                            ) : <span style={{ color: 'var(--faint)' }}>—</span>}
                          </td>
                        )
                      })}
                      <td style={{ ...td, textAlign: 'right' }}>
                        <span style={{ fontWeight: 700 }}>{r.totalQuantity}</span>
                        {r.inTransit > 0 && (
                          <div title="Sent by a branch, not yet received" style={{ fontSize: '0.68rem', fontWeight: 700, color: 'var(--blue)' }}>
                            ↗ {r.inTransit} on the road
                          </div>
                        )}
                      </td>
                      <td style={{ ...td, textAlign: 'right', whiteSpace: 'nowrap' }}>{r.unitPrice == null ? '—' : inr(r.totalValue)}</td>
                      <td style={{ ...td, textAlign: 'right', whiteSpace: 'nowrap' }}>
                        <div style={{ display: 'inline-flex', gap: 6 }}>
                          {heldAtMine && myIds.some((id) => can('BILL_PART', id) && qtyAt(r, id) > 0) && (
                            <button style={{ ...ghostBtn, minHeight: 34 }} onClick={() => setModal({ kind: 'bill', row: r })}>Bill</button>
                          )}
                          {heldElsewhere && can('RAISE_REQUEST') && myBranches.length > 0 && (
                            <button style={{ ...ghostBtn, minHeight: 34 }} onClick={() => setModal({ kind: 'ask', row: r })}>Ask a branch</button>
                          )}
                          {can('CORRECT_QTY') && myBranches.length > 0 && (
                            <button style={{ ...ghostBtn, minHeight: 34 }} onClick={() => setModal({ kind: 'correct', row: r })}>Correct</button>
                          )}
                        </div>
                      </td>
                    </tr>
                  )
                })}
                {!loading && (data?.rows.length ?? 0) === 0 && (
                  <tr>
                    <td style={{ ...td, padding: '28px 16px', color: 'var(--muted)' }} colSpan={branches.length + 4}>
                      {q || branchId !== '' || inStockOnly
                        ? 'No part matches. Try a shorter search.'
                        : 'No stock has been added yet. An Inventory Manager can upload the Excel sheet on the Upload tab.'}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
        {data?.truncated && (
          <div style={{ padding: '10px 16px', borderTop: '1px solid var(--line)' }}>
            <Hint>Showing the first 500 parts. Narrow the search to see the rest.</Hint>
          </div>
        )}
      </section>

      {modal?.kind === 'bill' && (
        <Modal title="Bill a part" subtitle={`${modal.row.partCode} · ${modal.row.description}`} onClose={() => setModal(null)} maxWidth={560}>
          <BillForm myBranches={myBranches} allBranches={branches} initialPart={modal.row}
            initialBranchId={myIds.find((id) => qtyAt(modal.row, id) > 0 && can('BILL_PART', id)) ?? null}
            onDone={done} onCancel={() => setModal(null)} />
        </Modal>
      )}
      {modal?.kind === 'ask' && (
        <Modal title="Ask another branch" subtitle={`${modal.row.partCode} · ${modal.row.description}`} onClose={() => setModal(null)} maxWidth={560}>
          <RequestForm myBranches={myBranches} allBranches={branches} initialPart={modal.row} onDone={done} onCancel={() => setModal(null)} />
        </Modal>
      )}
      {modal?.kind === 'correct' && (
        <CorrectModal row={modal.row} myBranches={myBranches} onClose={() => setModal(null)} onDone={done} />
      )}
    </div>
  )
}

/** Set a branch's quantity to what was counted. The reason is required; the old number stays in the history. */
function CorrectModal(props: {
  row: StockRow
  myBranches: { id: number; code: string; name: string }[]
  onClose: () => void
  onDone: (message: string) => void
}) {
  const [branchId, setBranchId] = useState(props.myBranches[0]?.id ?? 0)
  const current = props.row.cells.find((c) => c.branchId === branchId)?.quantity ?? 0
  const [value, setValue] = useState(String(current))
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const code = props.myBranches.find((b) => b.id === branchId)?.code ?? ''

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError('')
    const n = Number(value)
    if (!Number.isInteger(n) || n < 0) { setError('The counted quantity must be a whole number, 0 or more.'); return }
    if (n === current) { setError(`${code} already holds ${current}.`); return }
    setSaving(true)
    try {
      await sparePartsApi.correct({ branchId, partId: props.row.partId, newQuantity: n, reason: reason.trim() })
      props.onDone(`${code} now holds ${n} of ${props.row.partCode} (was ${current}).`)
    } catch (err) {
      setError(apiMessage(err, 'Could not correct this quantity.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal title="Correct a quantity" subtitle={`${props.row.partCode} · ${props.row.description}`} onClose={props.onClose} maxWidth={480}>
      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        {props.myBranches.length > 1 && (
          <Field label="Branch">
            <select
              value={branchId}
              onChange={(e) => {
                const id = Number(e.target.value)
                setBranchId(id)
                setValue(String(props.row.cells.find((c) => c.branchId === id)?.quantity ?? 0))
              }}
              style={inputStyle}
            >
              {props.myBranches.map((b) => <option key={b.id} value={b.id}>{b.name} ({b.code})</option>)}
            </select>
          </Field>
        )}
        <Hint>The system says <strong>{code}</strong> holds <strong>{current}</strong>. Enter what you actually counted.</Hint>
        <Field label="Counted quantity">
          <input type="number" min={0} step={1} value={value} onChange={(e) => setValue(e.target.value)} required style={inputStyle} />
        </Field>
        <Field label="Reason" hint="Required. It is kept in the history next to the old number.">
          <input value={reason} onChange={(e) => setReason(e.target.value)} required maxLength={500} style={inputStyle} />
        </Field>
        <ErrorBanner message={error} />
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 9 }}>
          <button type="button" style={{ ...ghostBtn, minHeight: 38 }} onClick={props.onClose}>Cancel</button>
          <button type="submit" disabled={saving} style={{ ...primaryBtn(saving), minHeight: 38, display: 'inline-flex', alignItems: 'center', gap: 8 }}>
            {saving && <Spinner />} Save correction
          </button>
        </div>
      </form>
    </Modal>
  )
}

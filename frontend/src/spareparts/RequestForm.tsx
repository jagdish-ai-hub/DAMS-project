import { useMemo, useState, type FormEvent } from 'react'
import { apiMessage, sparePartsApi, type SpareBranch, type StockRow } from '../api/spareParts'
import { ErrorBanner, Field, TextInput, ghostBtn, inputStyle, primaryBtn, Spinner } from '../shell/ui'
import { Hint, PartPicker, mono } from './spareUi'

/**
 * Ask another branch for a part. Asking changes no stock: it only puts a request in front of the branch that holds the part.
 * The supplier is chosen from the branches that actually hold it, so nobody asks a branch that has none.
 */
export default function RequestForm(props: {
  /** Branches the caller may act at (the branch that will receive the part). */
  myBranches: SpareBranch[]
  allBranches: SpareBranch[]
  initialPart?: StockRow | null
  onDone: (message: string) => void
  onCancel?: () => void
}) {
  const [part, setPart] = useState<StockRow | null>(props.initialPart ?? null)
  const [toBranchId, setToBranchId] = useState<number>(props.myBranches[0]?.id ?? 0)
  const [fromBranchId, setFromBranchId] = useState<number | null>(null)
  const [quantity, setQuantity] = useState('1')
  const [jobCard, setJobCard] = useState('')
  const [note, setNote] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const suppliers = useMemo(
    () => (part?.cells ?? []).filter((c) => c.quantity > 0 && c.branchId !== toBranchId),
    [part, toBranchId],
  )
  const code = (id: number) => props.allBranches.find((b) => b.id === id)?.code ?? `#${id}`
  const supplier = suppliers.find((c) => c.branchId === fromBranchId) ?? null
  const qty = Number(quantity)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError('')
    if (!part) { setError('Pick the part first.'); return }
    if (!supplier) { setError('Choose which branch to ask.'); return }
    if (!Number.isInteger(qty) || qty < 1) { setError('Quantity must be a whole number, 1 or more.'); return }
    if (qty > supplier.quantity) { setError(`${code(supplier.branchId)} holds only ${supplier.quantity} of ${part.partCode}.`); return }
    setSaving(true)
    try {
      const { data } = await sparePartsApi.createTransfer({
        fromBranchId: supplier.branchId, toBranchId, partId: part.partId, quantity: qty,
        jobCardNo: jobCard.trim() || undefined, note: note.trim() || undefined,
      })
      props.onDone(`Request ${data.requestNo} sent to ${code(supplier.branchId)}. Stock moves when they mark it Sent.`)
    } catch (err) {
      setError(apiMessage(err, 'Could not raise this request.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      {props.myBranches.length > 1 && (
        <Field label="Send the part to">
          <select value={toBranchId} onChange={(e) => { setToBranchId(Number(e.target.value)); setFromBranchId(null) }} style={inputStyle}>
            {props.myBranches.map((b) => <option key={b.id} value={b.id}>{b.name} ({b.code})</option>)}
          </select>
        </Field>
      )}
      <Field label="Part">
        <PartPicker value={part} onChange={(p) => { setPart(p); setFromBranchId(null) }} branches={props.allBranches}
          autoFocus={!props.initialPart} inStockOnly />
      </Field>

      {part && (
        <Field label="Ask which branch?">
          {suppliers.length === 0 ? (
            <Hint>No other branch holds this part right now.</Hint>
          ) : (
            <div role="radiogroup" style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {suppliers.map((c) => {
                const chosen = c.branchId === fromBranchId
                return (
                  <button
                    key={c.branchId}
                    type="button"
                    role="radio"
                    aria-checked={chosen}
                    onClick={() => setFromBranchId(c.branchId)}
                    style={{
                      border: `1.5px solid ${chosen ? 'var(--navy2)' : 'var(--line)'}`,
                      background: chosen ? 'var(--navy3)' : '#fff', borderRadius: 9, padding: '8px 14px',
                      cursor: 'pointer', textAlign: 'left', minHeight: 44,
                    }}
                  >
                    <span style={{ ...mono, fontWeight: 700, color: 'var(--navy)' }}>{code(c.branchId)}</span>
                    <span style={{ display: 'block', fontSize: '0.74rem', color: 'var(--muted)' }}>
                      holds {c.quantity}{c.bin ? ` · ${c.bin}` : ''}
                    </span>
                  </button>
                )
              })}
            </div>
          )}
        </Field>
      )}

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 12 }}>
        <Field label="Quantity needed">
          <input type="number" min={1} step={1} value={quantity} onChange={(e) => setQuantity(e.target.value)} required style={inputStyle} />
        </Field>
        <Field label="Job card number" hint="Optional: the job it is for">
          <input value={jobCard} onChange={(e) => setJobCard(e.target.value)} maxLength={40} inputMode="numeric"
            placeholder="e.g. 4010615068" style={{ ...inputStyle, ...mono }} />
        </Field>
      </div>
      <Field label="Note" hint="Optional: urgency, delivery instructions">
        <TextInput value={note} onChange={setNote} maxLength={500} />
      </Field>
      <ErrorBanner message={error} />
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 9 }}>
        {props.onCancel && <button type="button" style={{ ...ghostBtn, minHeight: 38 }} onClick={props.onCancel}>Cancel</button>}
        <button type="submit" disabled={saving || !supplier} style={{ ...primaryBtn(saving || !supplier), minHeight: 38, display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          {saving && <Spinner />} Send request
        </button>
      </div>
    </form>
  )
}

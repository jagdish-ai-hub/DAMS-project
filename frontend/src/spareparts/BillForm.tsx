import { useMemo, useState, type FormEvent } from 'react'
import { apiMessage, sparePartsApi, type SpareBranch, type StockRow } from '../api/spareParts'
import { ErrorBanner, Field, TextInput, ghostBtn, inputStyle, primaryBtn, Spinner } from '../shell/ui'
import { Hint, PartPicker, mono } from './spareUi'

/**
 * Bill a part to a job card: the branch quantity goes down. The job card number is typed (Eicher's own number). The server
 * refuses a claim job card and anything above what the branch holds; this form only helps the person see those numbers first.
 */
export default function BillForm(props: {
  /** Branches the caller may act at. */
  myBranches: SpareBranch[]
  allBranches: SpareBranch[]
  initialPart?: StockRow | null
  initialBranchId?: number | null
  onDone: (message: string) => void
  onCancel?: () => void
}) {
  const [part, setPart] = useState<StockRow | null>(props.initialPart ?? null)
  const [branchId, setBranchId] = useState<number>(props.initialBranchId ?? props.myBranches[0]?.id ?? 0)
  const [quantity, setQuantity] = useState('1')
  const [jobCard, setJobCard] = useState('')
  const [vehicle, setVehicle] = useState('')
  const [note, setNote] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  const held = useMemo(() => part?.cells.find((c) => c.branchId === branchId)?.quantity ?? 0, [part, branchId])
  const branchCode = props.myBranches.find((b) => b.id === branchId)?.code ?? ''
  const qty = Number(quantity)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError('')
    if (!part) { setError('Pick the part first.'); return }
    if (!Number.isInteger(qty) || qty < 1) { setError('Quantity must be a whole number, 1 or more.'); return }
    if (qty > held) { setError(`${branchCode} holds ${held} of ${part.partCode}, so ${qty} cannot be billed.`); return }
    setSaving(true)
    try {
      const { data } = await sparePartsApi.bill({
        branchId, partId: part.partId, quantity: qty, jobCardNo: jobCard.trim(),
        vehicleNo: vehicle.trim() || undefined, note: note.trim() || undefined,
      })
      props.onDone(`Billed ${qty} × ${part.partCode} to job card ${jobCard.trim()}. ${branchCode} now holds ${data.quantityAfter}.`)
    } catch (err) {
      setError(apiMessage(err, 'Could not bill this part.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      {props.myBranches.length > 1 && (
        <Field label="Billing from branch">
          <select value={branchId} onChange={(e) => setBranchId(Number(e.target.value))} style={inputStyle}>
            {props.myBranches.map((b) => <option key={b.id} value={b.id}>{b.name} ({b.code})</option>)}
          </select>
        </Field>
      )}
      <Field label="Part">
        <PartPicker value={part} onChange={setPart} branches={props.allBranches} autoFocus={!props.initialPart} />
      </Field>
      {part && (
        <Hint>
          <strong>{branchCode}</strong> holds <strong>{held}</strong> of this part
          {part.cells.some((c) => c.branchId !== branchId && c.quantity > 0) && (
            <> · other branches: {part.cells.filter((c) => c.branchId !== branchId && c.quantity > 0)
              .map((c) => `${props.allBranches.find((b) => b.id === c.branchId)?.code ?? c.branchId} ${c.quantity}`).join(', ')}</>
          )}
          .
        </Hint>
      )}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 12 }}>
        <Field label="Quantity">
          <input type="number" min={1} step={1} value={quantity} onChange={(e) => setQuantity(e.target.value)} required style={inputStyle} />
        </Field>
        <Field label="Job card number" hint="Eicher's own number, as printed">
          <input value={jobCard} onChange={(e) => setJobCard(e.target.value)} required maxLength={40} inputMode="numeric"
            placeholder="e.g. 4010615068" style={{ ...inputStyle, ...mono }} />
        </Field>
        <Field label="Vehicle number" hint="Optional">
          <TextInput value={vehicle} onChange={setVehicle} maxLength={20} placeholder="e.g. OD02AB1234" />
        </Field>
      </div>
      <Field label="Note" hint="Optional">
        <TextInput value={note} onChange={setNote} maxLength={500} />
      </Field>
      <ErrorBanner message={error} />
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 9 }}>
        {props.onCancel && <button type="button" style={{ ...ghostBtn, minHeight: 38 }} onClick={props.onCancel}>Cancel</button>}
        <button type="submit" disabled={saving} style={{ ...primaryBtn(saving), minHeight: 38, display: 'inline-flex', alignItems: 'center', gap: 8 }}>
          {saving && <Spinner />} Bill this part
        </button>
      </div>
    </form>
  )
}

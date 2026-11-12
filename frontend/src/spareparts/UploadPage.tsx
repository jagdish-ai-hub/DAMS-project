import { useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import {
  apiMessage, sparePartsApi,
  type ImportMode, type ImportPreview, type ImportResult, type ImportRowOut,
} from '../api/spareParts'
import { Badge, ErrorBanner, Field, Spinner, TextInput, card, cardTitle, ghostBtn, inputStyle, primaryBtn, td, th } from '../shell/ui'
import { useSpareAccess } from './SpareAccessContext'
import { FlashBanner, Hint, Notice, mono, useFlash } from './spareUi'

const MODES: { value: ImportMode; title: string; text: string }[] = [
  { value: 'ADD', title: 'Add to stock', text: 'Adds each row’s quantity to what the branch already holds. Use it for parts that came in.' },
  { value: 'SET', title: 'Set stock to the file', text: 'Makes each listed part match the file. Parts that are not in the file are left alone. Use it to correct counts.' },
]

/**
 * Stock from the owner's Excel sheet, in two steps so nothing is posted by surprise: first the file is read and every row is shown
 * with what it would do (or why it cannot), then the person confirms. Below it, one part can be added by hand.
 */
export default function UploadPage() {
  const { access, can } = useSpareAccess()
  const myBranches = access.branches.filter((b) => access.actBranchIds.includes(b.id))
  const flash = useFlash()

  const [mode, setMode] = useState<ImportMode>('ADD')
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<ImportPreview | null>(null)
  const [result, setResult] = useState<ImportResult | null>(null)
  const [onlyErrors, setOnlyErrors] = useState(false)
  const [busy, setBusy] = useState<'' | 'check' | 'post'>('')
  const [error, setError] = useState('')
  const input = useRef<HTMLInputElement>(null)

  if (!can('UPLOAD_ADD')) {
    return (
      <section style={card}>
        <Notice tone="warn">
          Your role is not allowed to upload or add parts. The Owner can allow this in Masters, under Spare Parts.
        </Notice>
      </section>
    )
  }

  function reset() {
    setFile(null)
    setPreview(null)
    setResult(null)
    setError('')
    if (input.current) input.current.value = ''
  }

  async function check() {
    if (!file) return
    setBusy('check')
    setError('')
    setResult(null)
    try {
      const { data } = await sparePartsApi.previewImport(file, mode)
      setPreview(data)
      setOnlyErrors(false)
    } catch (e) {
      setPreview(null)
      setError(apiMessage(e, 'Could not read this file.'))
    } finally {
      setBusy('')
    }
  }

  async function post() {
    if (!preview) return
    setBusy('post')
    setError('')
    try {
      const good = preview.rows.filter((r) => !r.error)
      const { data } = await sparePartsApi.confirmImport(preview.fileName, preview.mode, good)
      setResult(data)
      setPreview(null)
    } catch (e) {
      setError(apiMessage(e, 'Could not post this file.'))
    } finally {
      setBusy('')
    }
  }

  const shown = preview ? preview.rows.filter((r) => !onlyErrors || r.error) : []

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <FlashBanner message={flash.message} />

      {!result && (
        <section style={card}>
          <h2 style={cardTitle}>Upload your stock sheet</h2>
          <div role="radiogroup" aria-label="What should the upload do?" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 10, marginBottom: 14 }}>
            {MODES.map((m) => {
              const chosen = mode === m.value
              return (
                <button
                  key={m.value}
                  type="button"
                  role="radio"
                  aria-checked={chosen}
                  onClick={() => { setMode(m.value); setPreview(null) }}
                  style={{
                    textAlign: 'left', cursor: 'pointer', borderRadius: 10, padding: '12px 14px', minHeight: 44,
                    border: `1.5px solid ${chosen ? 'var(--navy2)' : 'var(--line)'}`,
                    background: chosen ? 'var(--navy3)' : '#fff',
                    boxShadow: chosen ? 'var(--ring)' : 'var(--shadow-xs)',
                  }}
                >
                  <div style={{ fontWeight: 700, color: 'var(--navy)', fontSize: '0.9rem' }}>{m.title}</div>
                  <div style={{ fontSize: '0.78rem', color: 'var(--muted)', marginTop: 3, lineHeight: 1.45 }}>{m.text}</div>
                </button>
              )
            })}
          </div>

          <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
            <input
              ref={input}
              type="file"
              accept=".xlsx"
              aria-label="Excel file"
              onChange={(e) => { setFile(e.target.files?.[0] ?? null); setPreview(null); setError('') }}
              style={{ fontSize: '0.84rem', maxWidth: '100%' }}
            />
            <button type="button" onClick={check} disabled={!file || busy !== ''}
              style={{ ...primaryBtn(!file || busy !== ''), minHeight: 38, display: 'inline-flex', alignItems: 'center', gap: 8 }}>
              {busy === 'check' && <Spinner />} Check the file
            </button>
          </div>
          <div style={{ marginTop: 10 }}>
            <Hint>
              Use your sheet as it is: a header row with <span style={mono}>BRANCH</span>, <span style={mono}>PART ID</span>,{' '}
              <span style={mono}>PART DESCRIPTION</span>, <span style={mono}>CLOSING QTY</span> (or <span style={mono}>OPENING QTY</span>),{' '}
              <span style={mono}>STORAGE BIN</span>, <span style={mono}>PRICE PER UNIT</span>. Nothing is posted until you confirm.
            </Hint>
          </div>
        </section>
      )}

      <ErrorBanner message={error} />

      {preview && (
        <section style={{ ...card, padding: 0, overflow: 'hidden' }}>
          <div style={{ padding: '16px 18px', display: 'flex', flexDirection: 'column', gap: 12 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
              <h2 style={{ ...cardTitle, margin: 0, flex: 1 }}>
                {preview.fileName} <span style={{ fontWeight: 400, color: 'var(--muted)' }}>· {preview.mode === 'ADD' ? 'adding to stock' : 'setting stock to the file'}</span>
              </h2>
              <Badge tone="gray">{preview.total} rows</Badge>
              <Badge tone="green">{preview.ok} ready</Badge>
              {preview.newParts > 0 && <Badge tone="blue">{preview.newParts} new parts</Badge>}
              {preview.errors > 0 && <Badge tone="red">{preview.errors} with a problem</Badge>}
            </div>
            {preview.errors > 0 && (
              <Notice tone="warn">
                {preview.errors} row{preview.errors === 1 ? '' : 's'} cannot be posted and will be skipped. Fix {preview.errors === 1 ? 'it' : 'them'} in the file and upload again if they matter.
                <button type="button" onClick={() => setOnlyErrors((v) => !v)}
                  style={{ marginLeft: 10, background: 'none', border: 'none', color: 'inherit', fontWeight: 700, textDecoration: 'underline', cursor: 'pointer' }}>
                  {onlyErrors ? 'Show all rows' : 'Show only these'}
                </button>
              </Notice>
            )}
          </div>

          <div style={{ overflow: 'auto', maxHeight: 460, WebkitOverflowScrolling: 'touch', borderTop: '1px solid var(--line)' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.82rem', minWidth: 820 }}>
              <thead>
                <tr style={{ background: 'var(--bg)' }}>
                  {['Row', 'Branch', 'Part ID', 'Description', 'Bin', 'Price', 'In file', 'Now → after', 'Result'].map((h, i) => (
                    <th key={h} style={{ ...th, position: 'sticky', top: 0, background: 'var(--bg)', textAlign: i === 6 || i === 7 ? 'right' : 'left' }}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {shown.map((r) => <PreviewRow key={`${r.rowNumber}-${r.partCode}`} r={r} />)}
              </tbody>
            </table>
          </div>

          <div style={{ padding: '12px 18px', borderTop: '1px solid var(--line)', display: 'flex', justifyContent: 'flex-end', gap: 9, flexWrap: 'wrap' }}>
            <button type="button" style={{ ...ghostBtn, minHeight: 38 }} onClick={reset} disabled={busy !== ''}>Choose another file</button>
            <button type="button" onClick={post} disabled={preview.ok === 0 || busy !== ''}
              style={{ ...primaryBtn(preview.ok === 0 || busy !== ''), minHeight: 38, display: 'inline-flex', alignItems: 'center', gap: 8 }}>
              {busy === 'post' && <Spinner />} Post {preview.ok} row{preview.ok === 1 ? '' : 's'}
            </button>
          </div>
        </section>
      )}

      {result && (
        <section style={card}>
          <h2 style={cardTitle}>Upload finished</h2>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginBottom: 12 }}>
            <Badge tone="green">{result.posted} row{result.posted === 1 ? '' : 's'} posted</Badge>
            {result.newParts > 0 && <Badge tone="blue">{result.newParts} new parts</Badge>}
            {result.rejected > 0 && <Badge tone="red">{result.rejected} skipped</Badge>}
          </div>
          {result.rejectedRows.length > 0 && (
            <ul style={{ margin: '0 0 12px', paddingLeft: 18, fontSize: '0.82rem', color: 'var(--red)' }}>
              {result.rejectedRows.map((r) => <li key={r.rowNumber}>Row {r.rowNumber} ({r.partCode || 'no part ID'}): {r.error}</li>)}
            </ul>
          )}
          <div style={{ display: 'flex', gap: 9 }}>
            <Link to="/app/spare-parts/stock" style={{ ...primaryBtn(), textDecoration: 'none', display: 'inline-flex', alignItems: 'center', minHeight: 38 }}>See the stock</Link>
            <button type="button" style={{ ...ghostBtn, minHeight: 38 }} onClick={reset}>Upload another file</button>
          </div>
        </section>
      )}

      <AddPartCard myBranches={myBranches} onDone={flash.show} />
    </div>
  )
}

function PreviewRow({ r }: { r: ImportRowOut }) {
  const bad = !!r.error
  return (
    <tr style={{ background: bad ? 'var(--red-bg)' : undefined }}>
      <td style={{ ...td, color: 'var(--muted)' }}>{r.rowNumber}</td>
      <td style={{ ...td, ...mono }}>{r.branchCode || '—'}</td>
      <td style={{ ...td, ...mono, fontWeight: 700 }}>{r.partCode || '—'}</td>
      <td style={{ ...td, maxWidth: 220 }}>{r.description || <span style={{ color: 'var(--faint)' }}>—</span>}</td>
      <td style={{ ...td, ...mono }}>{r.bin || '—'}</td>
      <td style={{ ...td, textAlign: 'right' }}>{r.unitPrice || '—'}</td>
      <td style={{ ...td, textAlign: 'right', fontWeight: 700 }}>{r.quantity || '—'}</td>
      <td style={{ ...td, textAlign: 'right', whiteSpace: 'nowrap' }}>
        {bad ? '—' : <>{r.currentQty} → <strong>{r.resultQty}</strong></>}
      </td>
      <td style={{ ...td, minWidth: 200 }}>
        {bad
          ? <span style={{ color: 'var(--red)', fontWeight: 600 }}>{r.error}</span>
          : r.newPart ? <Badge tone="blue">Ready · new part</Badge> : <Badge tone="green">Ready</Badge>}
      </td>
    </tr>
  )
}

/** One part by hand: for a part that is not in any sheet yet. */
function AddPartCard(props: { myBranches: { id: number; code: string; name: string }[]; onDone: (message: string) => void }) {
  const [branchId, setBranchId] = useState(props.myBranches[0]?.id ?? 0)
  const [code, setCode] = useState('')
  const [description, setDescription] = useState('')
  const [bin, setBin] = useState('')
  const [price, setPrice] = useState('')
  const [quantity, setQuantity] = useState('1')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError('')
    const q = Number(quantity)
    if (!Number.isInteger(q) || q < 0) { setError('Quantity must be a whole number, 0 or more.'); return }
    setSaving(true)
    try {
      await sparePartsApi.addPart({
        branchId, partCode: code.trim(), description: description.trim(), bin: bin.trim() || undefined,
        unitPrice: price.trim() === '' ? null : Number(price), quantity: q,
      })
      const branch = props.myBranches.find((b) => b.id === branchId)?.code ?? ''
      props.onDone(`Added ${q} × ${code.trim().toUpperCase()} at ${branch}.`)
      setCode(''); setDescription(''); setBin(''); setPrice(''); setQuantity('1')
    } catch (err) {
      setError(apiMessage(err, 'Could not add this part.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <section style={card}>
      <h2 style={cardTitle}>Add one part by hand</h2>
      <form onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))', gap: 12 }}>
          {props.myBranches.length > 1 && (
            <Field label="Branch">
              <select value={branchId} onChange={(e) => setBranchId(Number(e.target.value))} style={inputStyle}>
                {props.myBranches.map((b) => <option key={b.id} value={b.id}>{b.name} ({b.code})</option>)}
              </select>
            </Field>
          )}
          <Field label="Part ID">
            <input value={code} onChange={(e) => setCode(e.target.value)} required maxLength={40} placeholder="e.g. IA306245" style={{ ...inputStyle, ...mono }} />
          </Field>
          <Field label="Description"><TextInput value={description} onChange={setDescription} required maxLength={255} /></Field>
          <Field label="Storage bin" hint="Optional"><TextInput value={bin} onChange={setBin} maxLength={60} placeholder="e.g. BOX-2" /></Field>
          <Field label="Price per unit (₹)" hint="Optional">
            <input type="number" min={0} step="0.01" value={price} onChange={(e) => setPrice(e.target.value)} style={inputStyle} />
          </Field>
          <Field label="Quantity">
            <input type="number" min={0} step={1} value={quantity} onChange={(e) => setQuantity(e.target.value)} required style={inputStyle} />
          </Field>
        </div>
        <ErrorBanner message={error} />
        <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
          <button type="submit" disabled={saving} style={{ ...primaryBtn(saving), minHeight: 38, display: 'inline-flex', alignItems: 'center', gap: 8 }}>
            {saving && <Spinner />} Add part
          </button>
        </div>
      </form>
    </section>
  )
}

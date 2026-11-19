import { useRef, useState, type CSSProperties } from 'react'
import { useNavigate } from 'react-router-dom'
import { invoiceExtractApi, type ExtractItem, type InvoiceExtract } from '../api/invoiceExtract'
import { apiError } from '../review/reviewShared'
import { card, cardTitle, ErrorBanner, ghostBtn, primaryBtn, Badge, Spinner, td, th } from '../shell/ui'
import { buildInvoiceCsv } from './invoiceExtractCsv'
import HelpButton from '../help/HelpButton'

/**
 * Extract invoice — a TEST tool (AGENT.md "Extract invoice", rev 98). The Finance Manager uploads an Eicher dealer PDF
 * (customer tax invoice or warranty claim), the server reads it, and this page shows what was found: header, Spares,
 * Lube and Labour tables, free-under-warranty items, printed totals — and whether every figure reconciles with the
 * totals the document prints. Nothing is saved; a CSV can be downloaded. Where it feeds into DAMS is decided later.
 */

const money = new Intl.NumberFormat('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const fmt = (v: number | undefined | null) => (v === undefined || v === null ? '' : money.format(v))

type ColSpec = { key: keyof ExtractItem; label: string; kind: 'text' | 'num' | 'qty' }
const COLS: ColSpec[] = [
  { key: 'sno', label: '#', kind: 'text' },
  { key: 'code', label: 'Code', kind: 'text' },
  { key: 'hsnSac', label: 'HSN / SAC', kind: 'text' },
  { key: 'gstRate', label: 'GST %', kind: 'qty' },
  { key: 'description', label: 'Description', kind: 'text' },
  { key: 'qty', label: 'Qty', kind: 'qty' },
  { key: 'uom', label: 'UoM', kind: 'text' },
  { key: 'manHr', label: 'Man hrs', kind: 'qty' },
  { key: 'ratePerHr', label: 'Rate / hr', kind: 'num' },
  { key: 'unitAmount', label: 'Unit price / rate', kind: 'num' },
  { key: 'discount', label: 'Discount', kind: 'num' },
  { key: 'handling', label: 'Handling', kind: 'num' },
  { key: 'taxable', label: 'Taxable', kind: 'num' },
  { key: 'cgst', label: 'CGST', kind: 'num' },
  { key: 'sgst', label: 'SGST', kind: 'num' },
  { key: 'igst', label: 'IGST', kind: 'num' },
  { key: 'kfc', label: 'KFC', kind: 'num' },
  { key: 'tds', label: 'TDS', kind: 'num' },
  { key: 'total', label: 'Total', kind: 'num' },
]

export default function InvoiceExtractPage() {
  const navigate = useNavigate()
  const fileInput = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [result, setResult] = useState<InvoiceExtract | null>(null)

  async function read() {
    if (!file) return
    setBusy(true)
    setError('')
    setResult(null)
    try {
      const { data } = await invoiceExtractApi.extract(file)
      setResult(data)
    } catch (e) {
      setError(apiError(e, 'Could not read this PDF.'))
    } finally {
      setBusy(false)
    }
  }

  function clear() {
    setFile(null)
    setResult(null)
    setError('')
    if (fileInput.current) fileInput.current.value = ''
  }

  function download() {
    if (!result) return
    const blob = new Blob([buildInvoiceCsv(result)], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${result.fileName.replace(/\.pdf$/i, '')}-extracted.csv`
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(url)
  }

  return (
    <div style={{ maxWidth: 1180, margin: '0 auto', display: 'flex', flexDirection: 'column', gap: 14 }}>
      <button type="button" onClick={() => navigate('/app')}
        style={{ background: 'none', border: 'none', color: 'var(--navy)', fontSize: '0.82rem', fontWeight: 600, cursor: 'pointer', padding: '4px 0', alignSelf: 'flex-start' }}>
        ← Approvals & Claims
      </button>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 12 }}>
        <div>
          <h1 style={{ fontSize: '1.3rem', color: 'var(--navy)', marginBottom: 4, display: 'flex', alignItems: 'center', gap: 10 }}>
            Extract invoice <Badge tone="amber">Test tool</Badge>
          </h1>
          <div style={{ fontSize: '0.85rem', color: 'var(--muted)', maxWidth: 760 }}>
            Upload an Eicher dealer <strong>tax invoice</strong> or <strong>warranty claim</strong> PDF. It is read here and shown below —
            <strong> nothing is saved</strong> in DAMS.
          </div>
        </div>
        <HelpButton slug="extracting-an-invoice" />
      </div>

      <section style={card}>
        <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap' }}>
          <input ref={fileInput} type="file" accept="application/pdf,.pdf" aria-label="PDF file"
            onChange={(e) => { setFile(e.target.files?.[0] ?? null); setResult(null); setError('') }}
            style={{ fontSize: '0.85rem', maxWidth: '100%' }} />
          <button type="button" onClick={read} disabled={!file || busy} style={primaryBtn(!file || busy)}>
            {busy ? <><Spinner /> Reading…</> : 'Read the PDF'}
          </button>
          {(file || result) && <button type="button" onClick={clear} style={{ ...ghostBtn, minHeight: 34 }}>Clear</button>}
        </div>
      </section>

      <ErrorBanner message={error} />

      {result && <Result r={result} onDownload={download} />}
    </div>
  )
}

function Result({ r, onDownload }: { r: InvoiceExtract; onDownload: () => void }) {
  const failed = r.checks.filter((c) => !c.ok)
  const allOk = r.checksTotal > 0 && failed.length === 0 && r.problems.length === 0
  const lines = r.spares.length + r.lube.length + r.labour.length
  return (
    <>
      <section style={{ ...card, borderLeft: `4px solid ${allOk ? 'var(--green)' : r.checksTotal === 0 ? 'var(--amber)' : 'var(--red)'}` }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
          <div style={{ flex: 1, minWidth: 260 }}>
            <div style={{ fontWeight: 700, color: 'var(--ink)' }}>{r.documentLabel}</div>
            <div style={{ fontSize: '0.8rem', color: 'var(--muted)', marginTop: 2 }}>
              {r.fileName} · {r.spares.length} spares · {r.lube.length} lube · {r.labour.length} labour
              {r.freeItems.length > 0 ? ` · ${r.freeItems.length} replaced free under warranty` : ''}
            </div>
            <div role="status" style={{ marginTop: 8, fontSize: '0.86rem', fontWeight: 600, color: allOk ? 'var(--green)' : r.checksTotal === 0 ? 'var(--amber)' : 'var(--red)' }}>
              {allOk
                ? `All ${r.checksTotal} checks passed — every figure adds up to what the document prints.`
                : r.checksTotal === 0
                  ? 'Nothing could be checked — this does not look like one of the invoice formats this tool reads.'
                  : `${failed.length} of ${r.checksTotal} checks failed — do not rely on this read; see the checks below.`}
            </div>
          </div>
          <button type="button" onClick={onDownload} disabled={lines + r.freeItems.length === 0} style={{ ...primaryBtn(lines + r.freeItems.length === 0), minHeight: 38 }}>
            Download CSV
          </button>
        </div>
        {r.problems.length > 0 && (
          <ul style={{ margin: '10px 0 0', paddingLeft: 18, fontSize: '0.8rem', color: 'var(--red)' }}>
            {r.problems.map((p, i) => <li key={i}>{p}</li>)}
          </ul>
        )}
      </section>

      {r.header.length > 0 && (
        <section style={card} aria-label="Document details">
          <h2 style={cardTitle}>Document details</h2>
          <dl style={{ margin: 0, display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: '10px 22px' }}>
            {r.header.map((f) => (
              <div key={f.label} style={{ minWidth: 0 }}>
                <dt style={{ fontSize: '0.72rem', fontWeight: 700, color: 'var(--muted)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{f.label}</dt>
                <dd style={{ margin: '2px 0 0', fontSize: '0.88rem', overflowWrap: 'anywhere' }}>{f.value}</dd>
              </div>
            ))}
          </dl>
        </section>
      )}

      <ItemsTable title="Spares" items={r.spares} />
      <ItemsTable title="Lube" items={r.lube} />
      <ItemsTable title="Labour" items={r.labour} />

      {r.freeItems.length > 0 && (
        <section style={{ ...card, padding: 0, overflow: 'hidden' }} aria-label="Replaced under warranty">
          <h2 style={{ ...cardTitle, padding: '16px 18px 0', margin: '0 0 10px' }}>Replaced under warranty / AMC / FOC <span style={{ fontWeight: 400, color: 'var(--muted)' }}>— listed, not charged</span></h2>
          <div style={{ overflowX: 'auto' }}>
            <table style={tableStyle}>
              <thead><tr style={{ background: 'var(--bg)' }}><th style={th}>#</th><th style={th}>Item code</th><th style={th}>Description</th><th style={{ ...th, textAlign: 'right' }}>Quantity</th></tr></thead>
              <tbody>
                {r.freeItems.map((f, i) => (
                  <tr key={i}><td style={td}>{f.sno}</td><td style={{ ...td, fontFamily: 'Consolas, monospace' }}>{f.itemCode}</td><td style={td}>{f.description}</td><td style={{ ...td, textAlign: 'right' }}>{f.quantity}</td></tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {r.totals.length > 0 && (
        <section style={card} aria-label="Printed totals">
          <h2 style={cardTitle}>Totals printed on the document</h2>
          <dl style={{ margin: 0, display: 'grid', gridTemplateColumns: 'minmax(180px, 320px) auto', rowGap: 6, columnGap: 24, fontSize: '0.86rem', justifyContent: 'start' }}>
            {r.totals.map((t) => (
              <div key={t.label} style={{ display: 'contents' }}>
                <dt style={{ color: 'var(--muted)' }}>{t.label}</dt>
                <dd style={{ margin: 0, fontWeight: 700, textAlign: 'right' }}>{fmt(t.value)}</dd>
              </div>
            ))}
          </dl>
        </section>
      )}

      {r.hsnSummary.length > 0 && (
        <details style={card}>
          <summary style={{ cursor: 'pointer', fontWeight: 700, fontSize: '0.9rem' }}>HSN summary ({r.hsnSummary.length})</summary>
          <table style={{ ...tableStyle, marginTop: 8, maxWidth: 520 }}>
            <thead><tr style={{ background: 'var(--bg)' }}><th style={th}>HSN / SAC</th><th style={{ ...th, textAlign: 'right' }}>Taxable</th><th style={{ ...th, textAlign: 'right' }}>Total with tax</th></tr></thead>
            <tbody>
              {r.hsnSummary.map((h) => <tr key={h.hsn}><td style={td}>{h.hsn}</td><td style={{ ...td, textAlign: 'right' }}>{fmt(h.taxable)}</td><td style={{ ...td, textAlign: 'right' }}>{fmt(h.total)}</td></tr>)}
            </tbody>
          </table>
        </details>
      )}

      <details style={card} open={failed.length > 0}>
        <summary style={{ cursor: 'pointer', fontWeight: 700, fontSize: '0.9rem' }}>
          Checks — {r.checksPassed} of {r.checksTotal} passed
        </summary>
        <p style={{ fontSize: '0.78rem', color: 'var(--muted)', margin: '6px 0 8px' }}>
          Each line is recomputed (amount − discount = taxable, taxable + tax = total, tax = rate % of taxable), then the sections, the invoice total and the HSN summary are matched to the totals the document prints.
        </p>
        <ul style={{ listStyle: 'none', margin: 0, padding: 0, fontSize: '0.8rem' }}>
          {[...failed, ...r.checks.filter((c) => c.ok)].map((c, i) => (
            <li key={i} style={{ padding: '3px 0', color: c.ok ? 'var(--muted)' : 'var(--red)', fontWeight: c.ok ? 400 : 700 }}>
              {c.ok ? '✓' : '✗'} {c.name}{!c.ok && c.detail ? ` — ${c.detail}` : ''}
            </li>
          ))}
        </ul>
      </details>
    </>
  )
}

const tableStyle: CSSProperties = { width: '100%', borderCollapse: 'collapse', fontSize: '0.8rem' }

function ItemsTable({ title, items }: { title: string; items: ExtractItem[] }) {
  if (items.length === 0) {
    return (
      <section style={card} aria-label={title}>
        <h2 style={{ ...cardTitle, margin: 0 }}>{title} <span style={{ fontWeight: 400, color: 'var(--muted)' }}>— none on this document</span></h2>
      </section>
    )
  }
  // Show only the columns this document actually has (a tax invoice has no man-hours; a claim has no UoM).
  const cols = COLS.filter((c) => items.some((i) => i[c.key] !== undefined && i[c.key] !== null && i[c.key] !== ''))
  const sum = (key: 'taxable' | 'total') => items.reduce((a, i) => a + (i[key] ?? 0), 0)
  return (
    <section style={{ ...card, padding: 0, overflow: 'hidden' }} aria-label={title}>
      <h2 style={{ ...cardTitle, padding: '16px 18px 0', margin: '0 0 10px' }}>{title} <span style={{ fontWeight: 400, color: 'var(--muted)' }}>— {items.length} {items.length === 1 ? 'line' : 'lines'}</span></h2>
      <div style={{ overflowX: 'auto' }}>
        <table style={{ ...tableStyle, minWidth: 720 }}>
          <thead>
            <tr style={{ background: 'var(--bg)' }}>
              {cols.map((c) => <th key={c.key} style={{ ...th, textAlign: c.kind === 'text' ? 'left' : 'right', whiteSpace: 'nowrap' }}>{c.label}</th>)}
            </tr>
          </thead>
          <tbody>
            {items.map((i, n) => (
              <tr key={n}>
                {cols.map((c) => {
                  const v = i[c.key]
                  return (
                    <td key={c.key} style={{ ...td, textAlign: c.kind === 'text' ? 'left' : 'right', fontFamily: c.key === 'code' || c.key === 'hsnSac' ? 'Consolas, monospace' : undefined, minWidth: c.key === 'description' ? 200 : undefined }}>
                      {c.kind === 'num' ? fmt(v as number | undefined) : (v as string | number | undefined) ?? ''}
                    </td>
                  )
                })}
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr style={{ background: 'var(--bg)', fontWeight: 700 }}>
              {cols.map((c, idx) => (
                <td key={c.key} style={{ ...td, textAlign: c.kind === 'text' ? 'left' : 'right' }}>
                  {idx === 0 ? 'Sum' : c.key === 'taxable' ? fmt(sum('taxable')) : c.key === 'total' ? fmt(sum('total')) : ''}
                </td>
              ))}
            </tr>
          </tfoot>
        </table>
      </div>
    </section>
  )
}

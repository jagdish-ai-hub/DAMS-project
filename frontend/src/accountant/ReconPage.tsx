import { useEffect, useState } from 'react'
import { reconApi, type ReconBatch, type ReconLine } from '../api/recon'
import { card, ErrorBanner, inr, Badge, ghostBtn, primaryBtn, inputStyle, th, td, fmtDate } from '../shell/ui'
import HelpButton from '../help/HelpButton'

/**
 * Bank reconciliation (FEAT-40): upload the statement CSV, confirm or ignore
 * each suggested match. Matching only explains money — it never moves it or
 * edits a document. The unmatched rows are the work product.
 */
export default function ReconPage() {
  const [batches, setBatches] = useState<ReconBatch[] | null>(null)
  const [batchId, setBatchId] = useState<number | null>(null)
  const [lines, setLines] = useState<ReconLine[] | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const [unresolvedOnly, setUnresolvedOnly] = useState(true)

  async function loadBatches(select?: number) {
    try {
      const { data } = await reconApi.batches()
      setBatches(data)
      const pick = select ?? data[0]?.id ?? null
      setBatchId(pick)
      if (pick != null) {
        const { data: l } = await reconApi.lines(pick)
        setLines(l)
      } else {
        setLines([])
      }
    } catch (e) {
      setError(apiError(e, 'Could not load reconciliation batches.'))
    }
  }

  useEffect(() => { loadBatches() }, [])

  async function upload(file: File | undefined) {
    if (!file) return
    setBusy(true)
    setError('')
    try {
      const { data } = await reconApi.upload(file)
      await loadBatches(data.id)
    } catch (e) {
      setError(apiError(e, 'Upload failed. The CSV needs date,utr,amount[,narration] columns.'))
    } finally {
      setBusy(false)
    }
  }

  async function act(line: ReconLine, kind: 'confirm' | 'ignore', settlementLineId?: number) {
    setError('')
    try {
      if (kind === 'confirm' && settlementLineId != null) await reconApi.confirm(line.id, settlementLineId)
      else await reconApi.ignore(line.id, !line.ignored)
      if (batchId != null) {
        const { data } = await reconApi.lines(batchId)
        setLines(data)
      }
    } catch (e) {
      setError(apiError(e, 'Could not update that line.'))
    }
  }

  const visible = (lines ?? []).filter((l) => !unresolvedOnly || !l.resolved)
  const open = (lines ?? []).filter((l) => !l.resolved).length

  return (
    <div style={{ maxWidth: 1050, margin: '0 auto' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 4 }}>
        <h1 style={{ fontSize: '1.3rem', color: 'var(--navy)', margin: 0 }}>Reconcile bank credits</h1>
        <HelpButton slug="reconciling-the-bank" />
      </div>
      <p style={{ fontSize: '0.82rem', color: 'var(--muted)', marginTop: 0 }}>
        Statement lines matched to receipts by UTR and amount. Confirm with eyes — the matcher suggests, you decide.
      </p>
      <ErrorBanner message={error} />

      <div style={{ ...card, marginBottom: 14, padding: 14, display: 'flex', gap: 10, flexWrap: 'wrap', alignItems: 'center' }}>
        <label style={{ ...primaryBtn(busy), cursor: 'pointer' }}>
          {busy ? 'Matching…' : 'Upload statement CSV'}
          <input type="file" accept=".csv" hidden onChange={(e) => upload(e.target.files?.[0])} />
        </label>
        <select value={batchId ?? ''} onChange={(e) => { const id = Number(e.target.value); setBatchId(id); reconApi.lines(id).then(({ data }) => setLines(data)).catch((er) => setError(apiError(er, 'Could not load that batch.'))) }} style={inputStyle}>
          {(batches ?? []).map((b) => (
            <option key={b.id} value={b.id}>{b.filename} — {b.resolvedCount}/{b.lineCount} resolved</option>
          ))}
        </select>
        <label style={{ marginLeft: 'auto', fontSize: '0.8rem', display: 'flex', alignItems: 'center', gap: 6 }}>
          <input type="checkbox" checked={unresolvedOnly} onChange={(e) => setUnresolvedOnly(e.target.checked)} />
          Unresolved only ({open} open)
        </label>
      </div>

      <div style={card}>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead><tr><th style={th}>Date</th><th style={th}>UTR</th><th style={th}>Amount</th><th style={th}>Suggestion</th><th style={th}></th></tr></thead>
          <tbody>
            {visible.map((l) => (
              <tr key={l.id}>
                <td style={{ ...td, whiteSpace: 'nowrap' }}>{fmtDate(l.txnDate)}</td>
                <td style={{ ...td, fontFamily: 'Consolas, monospace', fontSize: '0.76rem' }}>{l.utr ?? '—'}</td>
                <td style={{ ...td, fontWeight: 700 }}>{inr(l.amount)}</td>
                <td style={td}>
                  {l.matchedSettlementLineId == null && !l.ignored && <Badge tone="red">unmatched</Badge>}
                  {l.ignored && <Badge tone="gray">ignored</Badge>}
                  {l.matchedSettlementLineId != null && (
                    <span style={{ fontSize: '0.8rem' }}>
                      <Badge tone={l.matchKind === 'EXACT' ? 'green' : 'amber'}>{l.matchKind}</Badge>{' '}
                      {l.matchedDocumentNo ?? `line #${l.matchedSettlementLineId}`}
                    </span>
                  )}
                  {l.narration && <div style={{ fontSize: '0.72rem', color: 'var(--faint)' }}>{l.narration}</div>}
                </td>
                <td style={{ ...td, whiteSpace: 'nowrap' }}>
                  {l.matchedSettlementLineId != null && !l.resolved && (
                    <button type="button" onClick={() => act(l, 'confirm', l.matchedSettlementLineId!)} style={{ ...ghostBtn, marginRight: 6 }}>Confirm</button>
                  )}
                  <button type="button" onClick={() => act(l, 'ignore')} style={ghostBtn}>{l.ignored ? 'Un-ignore' : 'Ignore'}</button>
                </td>
              </tr>
            ))}
            {visible.length === 0 && <tr><td colSpan={5} style={{ ...td, color: 'var(--faint)' }}>Everything here is explained. Upload a statement to start.</td></tr>}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

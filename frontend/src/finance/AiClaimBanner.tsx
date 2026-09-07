import { useEffect, useState } from 'react'
import { aiApi, type ClaimInsight } from '../api/ai'
import { useCopy } from '../shared/useCopy'
import { Badge, ErrorBanner, card } from '../shell/ui'

/**
 * FEAT-12 claim watch for the Finance Manager queue: oldest open claims first
 * with their age bucket. Closing still happens on the claim card itself —
 * this banner only points at what needs attention.
 */
export default function AiClaimBanner() {
  const [claims, setClaims] = useState<ClaimInsight[] | null>(null)
  const { copiedKey, copyError, copy } = useCopy()

  useEffect(() => {
    let live = true
    aiApi.claimInsights()
      .then(({ data }) => { if (live) setClaims(data) })
      .catch(() => { if (live) setClaims([]) })
    return () => { live = false }
  }, [])

  if (claims == null || claims.length === 0) return null
  const critical = claims.filter((c) => c.bucket === '90+' || c.bucket === '61-90')
  const oldest = claims[0]

  return (
    <div style={{ ...card, marginBottom: 12, borderLeft: '3px solid var(--amber)' }}>
      <ErrorBanner message={copyError} />
      <div style={{ fontSize: '0.84rem', display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
        <strong>AI claim watch</strong>
        <span style={{ color: 'var(--muted)' }}>
          {claims.length} open claim{claims.length === 1 ? '' : 's'}
          {critical.length > 0 && <> · {critical.length} over 60 days</>}
        </span>
        <span style={{ display: 'flex', gap: 6, marginLeft: 'auto', flexWrap: 'wrap', alignItems: 'center' }}>
          {claims.slice(0, 3).map((c) => (
            <Badge key={c.documentNo} tone={c.bucket === '90+' ? 'red' : 'amber'}>
              {c.documentNo} · {c.ageDays}d
            </Badge>
          ))}
        </span>
      </div>
      <div style={{ display: 'flex', gap: 10, alignItems: 'center', flexWrap: 'wrap', marginTop: 8 }}>
        <span style={{ fontSize: '0.76rem', color: 'var(--faint)' }}>
          Oldest: {oldest.documentNo} ({oldest.customerName}, {oldest.ageDays} days) · suggestions only
        </span>
        <button
          type="button"
          onClick={() => void copy(`claim-${oldest.documentNo}`, oldest.draftFollowUp)}
          style={{ background: 'none', border: 'none', color: 'var(--navy2)', cursor: 'pointer', fontSize: '0.76rem', fontWeight: 700, padding: 0 }}
        >
          {copiedKey === `claim-${oldest.documentNo}` ? 'Draft copied ✓' : 'Copy OEM follow-up draft'}
        </button>
      </div>
    </div>
  )
}

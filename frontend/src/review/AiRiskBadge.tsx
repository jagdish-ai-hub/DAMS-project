import { useEffect, useState } from 'react'
import { aiApi, type RiskScore } from '../api/ai'
import { Badge } from '../shell/ui'

/**
 * FEAT-16 risk pills for the review queues. One fetch per queue type, mapped by
 * document id — rows stay cheap and the score never blocks the queue itself.
 */
export function useRiskMap(queue: 'receipt' | 'expense' | 'cash'): Map<number, RiskScore> {
  const [map, setMap] = useState<Map<number, RiskScore>>(new Map())

  useEffect(() => {
    if (queue === 'cash') { setMap(new Map()); return }
    let live = true
    aiApi.risk(queue)
      .then(({ data }) => {
        if (!live) return
        const next = new Map<number, RiskScore>()
        for (const r of data) next.set(r.id, r)
        setMap(next)
      })
      .catch(() => { if (live) setMap(new Map()) })
    return () => { live = false }
  }, [queue])

  return map
}

export function RiskDot({ risk }: { risk: RiskScore | undefined }) {
  if (!risk || risk.score <= 0) return null
  // Native tooltip keeps the row compact while the full reasons stay one hover away.
  const hint = risk.reasons.length > 0 ? risk.reasons.join(' · ') : 'No issues found'
  return (
    <span title={hint} style={{ cursor: 'help', display: 'inline-flex' }}>
      <Badge tone={risk.score >= 50 ? 'red' : risk.score >= 25 ? 'amber' : 'green'}>
        risk {risk.score}
      </Badge>
    </span>
  )
}

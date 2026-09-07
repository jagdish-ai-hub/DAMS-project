import { useEffect, useState } from 'react'
import { aiApi, type LimitAdvice, type MastersHealthItem } from '../api/ai'
import { Badge, ErrorBanner, card, cardTitle, inr } from '../shell/ui'

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

/**
 * FEAT-18 Masters janitor + FEAT-19 limit advisor strip on the Owner Masters
 * page. Suggestions only — every change still goes through the normal
 * deactivate / edit actions below (never delete).
 */
export default function AiMastersStrip() {
  const [health, setHealth] = useState<MastersHealthItem[]>([])
  const [limits, setLimits] = useState<LimitAdvice[]>([])
  const [error, setError] = useState('')
  const [loaded, setLoaded] = useState(false)

  useEffect(() => {
    let live = true
    Promise.all([aiApi.mastersHealth(), aiApi.limitAdvice()])
      .then(([h, l]) => {
        if (!live) return
        setHealth(h.data.slice(0, 5))
        setLimits(l.data.slice(0, 5))
        setLoaded(true)
      })
      .catch((e) => { if (live) setError(apiError(e, 'Could not load AI masters hints.')) })
    return () => { live = false }
  }, [])

  if (!loaded && !error) return null
  if (error) return <ErrorBanner message={error} />
  if (health.length === 0 && limits.length === 0) return null

  return (
    <section aria-label="AI masters hints" style={{ ...card, borderLeft: '3px solid var(--purple, #6B3FA0)' }}>
      <h2 style={cardTitle}>AI masters hints</h2>
      <p style={{ fontSize: '0.74rem', color: 'var(--faint)', margin: '-4px 0 10px' }}>
        Suggestions only — every change still happens with the actions below.
      </p>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 260px), 1fr))', gap: 14 }}>
        {health.length > 0 && (
          <div>
            <h3 style={{ fontSize: '0.82rem', fontWeight: 700, marginBottom: 8 }}>Cleanup suggestions</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
              {health.map((h, i) => (
                <div key={i} style={{ fontSize: '0.79rem' }}>
                  <Badge tone="purple">{h.list}</Badge>{' '}
                  <strong>{h.name}</strong> — {h.suggestion}
                </div>
              ))}
            </div>
          </div>
        )}
        {limits.length > 0 && (
          <div>
            <h3 style={{ fontSize: '0.82rem', fontWeight: 700, marginBottom: 8 }}>Limit hints</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 7 }}>
              {limits.map((l, i) => (
                <div key={i} style={{ fontSize: '0.79rem' }}>
                  <strong>{l.subCategory}</strong>
                  {l.limitAmount != null && <> · limit {inr(l.limitAmount)}</>} — {l.suggestion}
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    </section>
  )
}

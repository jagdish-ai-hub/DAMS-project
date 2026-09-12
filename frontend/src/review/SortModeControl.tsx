export type SortMode = 'all' | 'branch' | 'date'

const OPTIONS: { v: SortMode; label: string }[] = [
  { v: 'all', label: 'All' },
  { v: 'branch', label: 'By branch' },
  { v: 'date', label: 'By date' },
]

/** Shared "All / By branch / By date" toggle — Accountant Review Queue and Finance
 * Manager Approvals & Claims render the identical control near the top of the screen. */
export default function SortModeControl({ value, onChange }: { value: SortMode; onChange: (m: SortMode) => void }) {
  return (
    <div style={{ display: 'flex', border: '1px solid var(--line)', borderRadius: 8, overflow: 'hidden' }}>
      {OPTIONS.map((o) => (
        <button
          key={o.v}
          type="button"
          onClick={() => onChange(o.v)}
          style={{
            border: 'none', padding: '6px 10px', fontSize: '0.76rem', fontWeight: 700, cursor: 'pointer',
            background: value === o.v ? 'var(--navy)' : 'var(--surface)',
            color: value === o.v ? '#fff' : 'var(--muted)',
          }}
        >
          {o.label}
        </button>
      ))}
    </div>
  )
}

/** Groups a queue list for display under the chosen sort mode. 'all' returns one
 * unheaded group (today's default, unchanged list order). */
export function groupItems<T extends { branchCode: string; submittedAt: string | null }>(
  items: T[],
  mode: SortMode,
): { heading: string; rows: T[] }[] {
  if (mode === 'all') return [{ heading: '', rows: items }]

  if (mode === 'branch') {
    const m = new Map<string, T[]>()
    for (const it of items) {
      const key = it.branchCode || '—'
      const bucket = m.get(key) ?? []
      bucket.push(it)
      m.set(key, bucket)
    }
    return [...m.entries()]
      .sort((a, b) => a[0].localeCompare(b[0]))
      .map(([heading, rows]) => ({ heading, rows }))
  }

  const m = new Map<string, T[]>()
  for (const it of items) {
    const key = it.submittedAt ? it.submittedAt.slice(0, 10) : 'Unknown date'
    const bucket = m.get(key) ?? []
    bucket.push(it)
    m.set(key, bucket)
  }
  return [...m.entries()]
    .sort((a, b) => b[0].localeCompare(a[0]))
    .map(([heading, rows]) => ({ heading, rows }))
}

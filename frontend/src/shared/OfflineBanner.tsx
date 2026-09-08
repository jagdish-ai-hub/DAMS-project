import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { outboxCount, outboxList, outboxMarkError, outboxRemove, type OutboxEntry } from './outbox'
import { receiptsApi } from '../api/receipts'
import { expensesApi } from '../api/expenses'
import { cashApi } from '../api/cash'
import { ghostBtn, primaryBtn } from '../shell/ui'

/**
 * Offline banner + sync (FEAT-49). Shows when the browser is offline or the
 * outbox holds queued creates. Sync posts each queued payload as a DRAFT and
 * drops it from the queue — the cashier then opens it from My Entries and
 * submits normally. A queued entry that the server rejects on sync stays in
 * the queue with the server's reason, editable nowhere — delete it and
 * re-enter online.
 */
export default function OfflineBanner() {
  const navigate = useNavigate()
  const [online, setOnline] = useState(() => (typeof navigator === 'undefined' ? true : navigator.onLine))
  const [entries, setEntries] = useState<OutboxEntry[]>(() => outboxList())
  const [syncing, setSyncing] = useState(false)
  const [error, setError] = useState('')

  const refresh = useCallback(() => setEntries(outboxList()), [])

  useEffect(() => {
    const onNet = () => setOnline(navigator.onLine)
    window.addEventListener('online', onNet)
    window.addEventListener('offline', onNet)
    window.addEventListener('dams:outbox', refresh)
    return () => {
      window.removeEventListener('online', onNet)
      window.removeEventListener('offline', onNet)
      window.removeEventListener('dams:outbox', refresh)
    }
  }, [refresh])

  if (online && entries.length === 0) return null

  async function syncNow() {
    setSyncing(true)
    setError('')
    try {
      for (const e of outboxList()) {
        try {
          if (e.kind === 'receipt') {
            const { data } = await receiptsApi.create({ ...(e.payload as object), submit: false } as never)
            outboxRemove(e.id)
            navigate(`/app/new-receipt?editDoc=${(data as { id: number }).id}`)
            return
          }
          if (e.kind === 'expense') {
            const { data } = await expensesApi.create({ ...(e.payload as object), submit: false } as never)
            outboxRemove(e.id)
            navigate(`/app/new-expense?editDoc=${(data as { id: number }).id}`)
            return
          }
          await cashApi.create({ ...(e.payload as object), submit: false } as never)
          outboxRemove(e.id)
        } catch (err) {
          const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
            ?? 'Server refused it — check the entry online.'
          outboxMarkError(e.id, msg)
        }
      }
      refresh()
    } finally {
      setSyncing(false)
    }
  }

  return (
    <div style={{
      borderRadius: 9, padding: '10px 14px', fontSize: '0.82rem', marginBottom: 14,
      background: online ? 'var(--navy3)' : 'var(--amber-bg)',
      border: '1px solid var(--line)',
    }}>
      {!online && <div style={{ fontWeight: 700, marginBottom: 4 }}>You're offline — new entries queue on this device.</div>}
      {entries.length > 0 && (
        <div>
          <strong>{outboxCount()} entr{entries.length === 1 ? 'y' : 'ies'} waiting to sync</strong>
          <span style={{ color: 'var(--muted)' }}> — they become drafts, nothing is real until synced.</span>
          <ul style={{ margin: '6px 0', paddingLeft: 18 }}>
            {entries.map((e) => (
              <li key={e.id}>
                {e.label}
                {e.error && <span style={{ color: 'var(--red)' }}> — {e.error}</span>}
                {' '}<button type="button" onClick={() => { outboxRemove(e.id); refresh() }} style={{ ...ghostBtn, minHeight: 26, padding: '1px 8px', fontSize: '0.72rem' }}>Discard</button>
              </li>
            ))}
          </ul>
          <button type="button" onClick={syncNow} disabled={syncing || !online} style={primaryBtn(syncing)}>
            {syncing ? 'Syncing…' : `Sync now${online ? '' : ' (needs connection)'}`}
          </button>
        </div>
      )}
      {error && <div style={{ color: 'var(--red)', marginTop: 6 }}>{error}</div>}
    </div>
  )
}

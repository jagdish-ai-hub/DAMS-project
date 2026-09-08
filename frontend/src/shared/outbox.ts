/**
 * Offline outbox (FEAT-49, scoped v1): when the network drops mid-entry, the
 * cashier's create payload is queued in localStorage instead of lost.
 *
 * Honest limits, stated in the UI: queued entries sync as DRAFTS — document
 * numbers, maker-checker and day locks are server-side, so nothing queued is
 * real until it syncs. Only top-level creates queue (receipt / expense /
 * cash movement with their lines); edits, submits and closes need the server
 * and fail loudly instead of pretending.
 */

export type OutboxKind = 'receipt' | 'expense' | 'cash'

export interface OutboxEntry {
  id: string
  kind: OutboxKind
  label: string
  payload: unknown
  queuedAt: string
  error: string | null
}

const KEY = 'dams_outbox_v1'

function read(): OutboxEntry[] {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw) as OutboxEntry[]
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function write(entries: OutboxEntry[]) {
  try {
    localStorage.setItem(KEY, JSON.stringify(entries))
  } catch {
    /* storage full/blocked — the entry is lost, but so is everything else offline */
  }
}

/** True when the failure is the network, not the server rejecting the payload. */
export function isOfflineError(err: unknown): boolean {
  const e = err as { response?: unknown; request?: unknown; code?: string }
  return e != null && e.response == null && (e.request != null || e.code === 'ERR_NETWORK')
}

export function outboxList(): OutboxEntry[] {
  return read()
}

export function outboxCount(): number {
  return read().length
}

export function outboxEnqueue(kind: OutboxKind, label: string, payload: unknown): OutboxEntry {
  const entry: OutboxEntry = {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    kind,
    label,
    payload,
    queuedAt: new Date().toISOString(),
    error: null,
  }
  write([...read(), entry])
  window.dispatchEvent(new Event('dams:outbox'))
  return entry
}

export function outboxRemove(id: string) {
  write(read().filter((e) => e.id !== id))
  window.dispatchEvent(new Event('dams:outbox'))
}

export function outboxMarkError(id: string, error: string) {
  write(read().map((e) => (e.id === id ? { ...e, error } : e)))
  window.dispatchEvent(new Event('dams:outbox'))
}

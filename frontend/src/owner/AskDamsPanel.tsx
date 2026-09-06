import { useEffect, useRef, useState, type FormEvent } from 'react'
import { aiApi } from '../api/ai'
import { useAuth } from '../auth/useAuth'
import { useCopy } from '../shared/useCopy'
import { Badge, ErrorBanner, Field, Modal, TextInput, SkeletonRows, primaryBtn, ghostBtn } from '../shell/ui'

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

interface ChatMsg {
  id: number
  role: 'user' | 'assistant'
  text: string
  citedDocs?: string[]
  requestId?: string | null
}

const STARTERS = [
  'Which warranty claims are over 60 days?',
  'How did each branch do this month?',
  'Any cash variances I should know about?',
  'Why do entries come back queried?',
]

const FOLLOW_UPS = [
  'What needs attention first?',
  'Compare the branches for me.',
]

let nextId = 1
const freshId = () => nextId++

function loadThread(key: string): ChatMsg[] {
  try {
    const raw = sessionStorage.getItem(key)
    if (!raw) return []
    const parsed = JSON.parse(raw) as ChatMsg[]
    return Array.isArray(parsed) ? parsed.slice(-40) : []
  } catch {
    return []
  }
}

// H5: ids must stay unique across reloads — seed the counter past every
// persisted id, otherwise fresh bubbles reuse keys and copy feedback misfires.
function loadAndSeed(key: string): ChatMsg[] {
  const loaded = loadThread(key)
  for (const m of loaded) {
    if (m.id >= nextId) nextId = m.id + 1
  }
  return loaded
}

/**
 * FEAT-09 Ask DAMS — threaded, scoped Owner/Admin assistant. Multi-turn thread
 * (kept for this browser session only, like the JWT), starter suggestions for
 * the cold start, follow-up chips, clickable cited documents that ask about
 * that document, and per-answer copy. Read-only: it suggests, humans click.
 */
export default function AskDamsPanel({ branchId, scopeLabel, onClose }: {
  branchId?: number
  scopeLabel: string
  onClose: () => void
}) {
  const { user } = useAuth()
  // M7: the thread belongs to one scope — switching branches starts a matching
  // thread instead of showing OOR answers under an OOB badge.
  const storageKey = `dams.ask.thread.${user?.orgId ?? 'none'}.${scopeLabel}`
  const [messages, setMessages] = useState<ChatMsg[]>(() => loadAndSeed(storageKey))
  const [q, setQ] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const { copiedKey, copyError, copy } = useCopy()
  const threadRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    setMessages(loadAndSeed(storageKey))
  }, [storageKey])

  useEffect(() => {
    try {
      sessionStorage.setItem(storageKey, JSON.stringify(messages.slice(-40)))
    } catch {
      // Private browsing — thread just won't survive a reload.
    }
  }, [messages, storageKey])

  useEffect(() => {
    threadRef.current?.scrollTo({ top: threadRef.current.scrollHeight })
  }, [messages, loading])

  async function ask(question: string) {
    const trimmed = question.trim()
    if (!trimmed || loading) return
    setLoading(true)
    setError('')
    const userMsg: ChatMsg = { id: freshId(), role: 'user', text: trimmed }
    setMessages((prev) => [...prev, userMsg])
    setQ('')
    try {
      const { data } = await aiApi.ask(trimmed, branchId)
      setMessages((prev) => [...prev, {
        id: freshId(), role: 'assistant', text: data.answer,
        citedDocs: data.citedDocs, requestId: data.requestId,
      }])
    } catch (err) {
      setError(apiError(err, 'Could not answer that. Try again.'))
    } finally {
      setLoading(false)
    }
  }

  function submit(e: FormEvent) {
    e.preventDefault()
    void ask(q)
  }

  function clear() {
    setMessages([])
    setError('')
    try {
      sessionStorage.removeItem(storageKey)
    } catch {
      // ignore
    }
  }

  async function copyAnswer(msg: ChatMsg) {
    await copy(`answer-${msg.id}`, msg.text)
  }

  const lastAssistant = [...messages].reverse().find((m) => m.role === 'assistant')
  const followUps: string[] = []
  if (lastAssistant?.citedDocs?.[0]) {
    followUps.push(`Tell me about ${lastAssistant.citedDocs[0]}`)
  }
  followUps.push(...FOLLOW_UPS)

  return (
    <Modal title="Ask DAMS" subtitle={`Answers from this organisation only · ${scopeLabel} · read-only`} onClose={onClose} maxWidth={640}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 10 }}>
        <Badge tone="blue">{scopeLabel}</Badge>
        {messages.length > 0 && (
          <button type="button" onClick={clear} style={{ ...ghostBtn, minHeight: 32, padding: '2px 10px', marginLeft: 'auto' }}>
            Clear chat
          </button>
        )}
      </div>

      <div ref={threadRef} aria-live="polite" style={{ display: 'flex', flexDirection: 'column', gap: 10, maxHeight: 380, overflowY: 'auto', padding: '2px 2px 6px' }}>
        {messages.length === 0 && !loading && (
          <div>
            <p style={{ fontSize: '0.84rem', color: 'var(--muted)', marginBottom: 8 }}>Try one of these:</p>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 7 }}>
              {STARTERS.map((s) => (
                <button key={s} type="button" onClick={() => void ask(s)} style={{ ...ghostBtn, minHeight: 34, fontSize: '0.78rem' }}>
                  {s}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.map((m) => m.role === 'user' ? (
          <div key={m.id} style={{ alignSelf: 'flex-end', background: 'var(--navy)', color: '#fff', borderRadius: '12px 12px 3px 12px', padding: '8px 12px', fontSize: '0.85rem', maxWidth: '88%' }}>
            {m.text}
          </div>
        ) : (
          <div key={m.id} style={{ alignSelf: 'flex-start', background: 'var(--bg)', border: '1px solid var(--line)', borderRadius: '12px 12px 12px 3px', padding: '9px 12px', fontSize: '0.85rem', lineHeight: 1.55, maxWidth: '94%', whiteSpace: 'pre-wrap' }}>
            {m.text}
            {m.citedDocs && m.citedDocs.length > 0 && (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 8 }}>
                {m.citedDocs.map((d) => (
                  <button
                    key={d}
                    type="button"
                    onClick={() => void ask(`Tell me about ${d}`)}
                    title={`Ask about ${d}`}
                    style={{ background: 'none', border: 'none', padding: 0, cursor: 'pointer' }}
                  >
                    <Badge tone="blue">{d} →</Badge>
                  </button>
                ))}
              </div>
            )}
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 7 }}>
              <button type="button" onClick={() => void copyAnswer(m)} style={{ background: 'none', border: 'none', color: 'var(--muted)', cursor: 'pointer', fontSize: '0.74rem', padding: 0 }}>
                {copiedKey === `answer-${m.id}` ? 'Copied ✓' : 'Copy answer'}
              </button>
              {m.requestId && <span style={{ fontSize: '0.68rem', color: 'var(--faint)' }}>ref {m.requestId}</span>}
            </div>
          </div>
        ))}

        {loading && (
          <div style={{ alignSelf: 'flex-start', background: 'var(--bg)', border: '1px solid var(--line)', borderRadius: '12px 12px 12px 3px', padding: '6px 14px', maxWidth: '70%' }}>
            <SkeletonRows rows={2} height={13} gap={7} />
          </div>
        )}
      </div>

      {lastAssistant && !loading && (
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 7, marginTop: 10 }}>
          {followUps.slice(0, 3).map((f) => (
            <button key={f} type="button" onClick={() => void ask(f)} style={{ ...ghostBtn, minHeight: 32, fontSize: '0.76rem' }}>
              {f}
            </button>
          ))}
        </div>
      )}

      <ErrorBanner message={error} />
      <ErrorBanner message={copyError} />

      <form onSubmit={submit} style={{ display: 'flex', gap: 8, marginTop: 10 }}>
        <div style={{ flex: 1 }}>
          <Field label="Ask a follow-up">
            <TextInput value={q} onChange={setQ} placeholder="Type a question…" maxLength={500} autoFocus />
          </Field>
        </div>
        <button type="submit" disabled={loading || !q.trim()} style={{ ...primaryBtn(loading || !q.trim()), alignSelf: 'flex-end', minHeight: 38 }}>
          {loading ? '…' : 'Ask'}
        </button>
      </form>
    </Modal>
  )
}

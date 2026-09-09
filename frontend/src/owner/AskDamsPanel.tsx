import { useEffect, useRef, useState, type FormEvent } from 'react'
import { createPortal } from 'react-dom'
import { aiApi } from '../api/ai'
import { useAuth } from '../auth/useAuth'
import { useCopy } from '../shared/useCopy'
import { Badge, ErrorBanner, SkeletonRows, primaryBtn, ghostBtn } from '../shell/ui'

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

  // Close on Escape key
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

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

  return createPortal(
    <>
      {/* Mobile-only touch backdrop to dismiss on small screens */}
      <div
        className="sm:hidden fixed inset-0 bg-black/40 z-[94]"
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Floating Chat Widget Window */}
      <div
        role="dialog"
        aria-modal="false"
        aria-label="Ask DAMS AI Assistant"
        className="dams-anim-modal fixed z-[95] right-0 bottom-0 sm:right-6 sm:bottom-6 w-full sm:w-[420px] max-w-full sm:max-w-[calc(100vw-32px)] h-[85vh] sm:h-[580px] max-h-[calc(100vh-80px)] flex flex-col bg-[var(--surface)] text-[var(--ink)] rounded-t-2xl sm:rounded-2xl border border-[var(--line)] shadow-2xl overflow-hidden"
        style={{
          boxShadow: '0 20px 48px -8px rgba(16, 24, 40, 0.28), 0 0 0 1px rgba(16, 24, 40, 0.08)',
        }}
      >
        {/* Widget Header */}
        <div
          style={{
            background: 'var(--navy)',
            color: '#fff',
            padding: '12px 16px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderBottom: '1px solid rgba(255,255,255,0.1)',
            flexShrink: 0,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 9, minWidth: 0 }}>
            <span style={{ fontSize: '1.15rem', color: '#60A5FA', lineHeight: 1 }}>✦</span>
            <div style={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 7 }}>
                <span style={{ fontSize: '0.92rem', fontWeight: 700, letterSpacing: -0.2 }}>Ask DAMS</span>
                <span
                  style={{
                    fontSize: '0.68rem',
                    fontWeight: 600,
                    background: 'rgba(255,255,255,0.18)',
                    color: '#fff',
                    padding: '1px 6px',
                    borderRadius: 4,
                    textTransform: 'uppercase',
                    letterSpacing: 0.3,
                  }}
                >
                  {scopeLabel}
                </span>
              </div>
              <span style={{ fontSize: '0.70rem', color: 'rgba(255,255,255,0.65)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                Organisation AI · Read-only
              </span>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexShrink: 0 }}>
            {messages.length > 0 && (
              <button
                type="button"
                onClick={clear}
                title="Clear conversation"
                style={{
                  background: 'none',
                  border: 'none',
                  color: 'rgba(255,255,255,0.75)',
                  fontSize: '0.74rem',
                  fontWeight: 600,
                  cursor: 'pointer',
                  padding: '4px 8px',
                  borderRadius: 6,
                }}
                className="hover:bg-white/10 hover:text-white transition-colors"
              >
                Clear
              </button>
            )}
            <button
              type="button"
              onClick={onClose}
              aria-label="Close assistant"
              style={{
                background: 'none',
                border: 'none',
                color: 'rgba(255,255,255,0.8)',
                cursor: 'pointer',
                padding: '4px 7px',
                borderRadius: 6,
                fontSize: '1rem',
                lineHeight: 1,
              }}
              className="hover:bg-white/10 hover:text-white transition-colors"
            >
              ✕
            </button>
          </div>
        </div>

        {/* Scrollable Message Thread */}
        <div
          ref={threadRef}
          aria-live="polite"
          style={{
            flex: 1,
            overflowY: 'auto',
            padding: '14px 14px 8px',
            display: 'flex',
            flexDirection: 'column',
            gap: 12,
            background: 'var(--surface)',
          }}
        >
          {messages.length === 0 && !loading && (
            <div style={{ padding: '8px 4px' }}>
              <div
                style={{
                  background: 'var(--bg)',
                  border: '1px solid var(--line)',
                  borderRadius: 12,
                  padding: '14px 14px',
                  marginBottom: 12,
                  textAlign: 'center',
                }}
              >
                <div style={{ fontSize: '1.4rem', marginBottom: 4 }}>✦</div>
                <div style={{ fontSize: '0.86rem', fontWeight: 600, color: 'var(--navy)', marginBottom: 4 }}>
                  How can I help with your dealership?
                </div>
                <div style={{ fontSize: '0.75rem', color: 'var(--muted)', lineHeight: 1.45 }}>
                  Ask about pending receipts, warranty claims, cash variances, or branch comparisons.
                </div>
              </div>

              <p style={{ fontSize: '0.76rem', fontWeight: 600, color: 'var(--muted)', marginBottom: 8, textTransform: 'uppercase', letterSpacing: 0.4 }}>
                Suggested questions:
              </p>
              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {STARTERS.map((s) => (
                  <button
                    key={s}
                    type="button"
                    onClick={() => void ask(s)}
                    style={{
                      ...ghostBtn,
                      textAlign: 'left',
                      minHeight: 34,
                      fontSize: '0.80rem',
                      padding: '7px 11px',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      borderRadius: 8,
                    }}
                    className="hover:bg-blue-50 hover:border-blue-200 hover:text-blue-900 transition-colors"
                  >
                    <span>{s}</span>
                    <span style={{ color: 'var(--faint)', fontSize: '0.85rem' }}>→</span>
                  </button>
                ))}
              </div>
            </div>
          )}

          {messages.map((m) =>
            m.role === 'user' ? (
              <div
                key={m.id}
                style={{
                  alignSelf: 'flex-end',
                  background: 'var(--navy)',
                  color: '#fff',
                  borderRadius: '14px 14px 2px 14px',
                  padding: '9px 13px',
                  fontSize: '0.84rem',
                  lineHeight: 1.5,
                  maxWidth: '85%',
                  boxShadow: 'var(--shadow)',
                  wordBreak: 'break-word',
                  whiteSpace: 'pre-wrap',
                }}
              >
                {m.text}
              </div>
            ) : (
              <div
                key={m.id}
                style={{
                  alignSelf: 'flex-start',
                  background: 'var(--bg)',
                  border: '1px solid var(--line)',
                  borderRadius: '14px 14px 14px 2px',
                  padding: '10px 13px',
                  fontSize: '0.84rem',
                  lineHeight: 1.55,
                  maxWidth: '92%',
                  whiteSpace: 'pre-wrap',
                  boxShadow: 'var(--shadow)',
                  wordBreak: 'break-word',
                }}
              >
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
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 8, paddingTop: 6, borderTop: '1px solid var(--line)' }}>
                  <button
                    type="button"
                    onClick={() => void copyAnswer(m)}
                    style={{
                      background: 'none',
                      border: 'none',
                      color: 'var(--muted)',
                      cursor: 'pointer',
                      fontSize: '0.73rem',
                      fontWeight: 500,
                      padding: 0,
                    }}
                    className="hover:text-[var(--navy)]"
                  >
                    {copiedKey === `answer-${m.id}` ? 'Copied ✓' : 'Copy answer'}
                  </button>
                  {m.requestId && (
                    <span style={{ fontSize: '0.67rem', color: 'var(--faint)' }}>
                      ref {m.requestId.slice(0, 8)}
                    </span>
                  )}
                </div>
              </div>
            )
          )}

          {loading && (
            <div
              style={{
                alignSelf: 'flex-start',
                background: 'var(--bg)',
                border: '1px solid var(--line)',
                borderRadius: '14px 14px 14px 2px',
                padding: '10px 14px',
                maxWidth: '75%',
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 6 }}>
                <span className="dams-spinner" style={{ width: 12, height: 12 }} />
                <span style={{ fontSize: '0.75rem', color: 'var(--muted)', fontWeight: 500 }}>Thinking…</span>
              </div>
              <SkeletonRows rows={2} height={11} gap={6} />
            </div>
          )}

          {lastAssistant && !loading && (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 4, paddingTop: 4 }}>
              {followUps.slice(0, 3).map((f) => (
                <button
                  key={f}
                  type="button"
                  onClick={() => void ask(f)}
                  style={{
                    ...ghostBtn,
                    minHeight: 28,
                    fontSize: '0.74rem',
                    padding: '3px 9px',
                    borderRadius: 999,
                  }}
                  className="hover:bg-blue-50 hover:border-blue-200 transition-colors"
                >
                  {f}
                </button>
              ))}
            </div>
          )}

          <ErrorBanner message={error} />
          <ErrorBanner message={copyError} />
        </div>

        {/* Footer Input Area */}
        <form
          onSubmit={submit}
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            padding: '10px 12px',
            borderTop: '1px solid var(--line)',
            background: 'var(--surface)',
            flexShrink: 0,
          }}
        >
          <input
            value={q}
            onChange={(e) => setQ(e.target.value)}
            placeholder="Ask a question or follow-up…"
            maxLength={500}
            autoFocus
            disabled={loading}
            style={{
              flex: 1,
              border: '1.5px solid var(--line)',
              borderRadius: 8,
              padding: '8px 12px',
              fontSize: '0.84rem',
              background: 'var(--bg)',
              outline: 'none',
              color: 'var(--ink)',
            }}
            className="focus:border-[var(--navy)] focus:bg-white transition-colors"
          />
          <button
            type="submit"
            disabled={loading || !q.trim()}
            style={{
              ...primaryBtn(loading || !q.trim()),
              minHeight: 36,
              padding: '6px 14px',
              fontSize: '0.82rem',
              flexShrink: 0,
            }}
          >
            {loading ? '…' : 'Ask'}
          </button>
        </form>
      </div>
    </>,
    document.body
  )
}

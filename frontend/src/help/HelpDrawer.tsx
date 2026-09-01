import { useEffect, useMemo, useState } from 'react'
import Markdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import { HELP_MANIFEST, articleBody, type HelpRole } from './manifest'

/**
 * Slide-over Help panel opened from the shell header. Left: a search box + the role's
 * table of contents. Right: the selected article, rendered from bundled Markdown.
 * Screen headers can open it straight to one article via `initialSlug`.
 */
export default function HelpDrawer({ role, open, onClose, initialSlug }: {
  role: HelpRole
  open: boolean
  onClose: () => void
  initialSlug?: string
}) {
  const entries = useMemo(() => HELP_MANIFEST[role] ?? [], [role])
  const [slug, setSlug] = useState(initialSlug ?? entries[0]?.slug ?? '')
  const [query, setQuery] = useState('')

  // Keep the panel mounted briefly after `open` flips false so it can slide out.
  const [render, setRender] = useState(open)
  const [closing, setClosing] = useState(false)
  useEffect(() => {
    if (open) {
      setRender(true)
      setClosing(false)
      return
    }
    setClosing(true)
    const t = setTimeout(() => { setRender(false); setClosing(false) }, 240)
    return () => clearTimeout(t)
  }, [open])

  // Re-point when opened with a specific article (contextual "?").
  useEffect(() => {
    if (open && initialSlug) {
      setSlug(initialSlug)
    }
  }, [open, initialSlug])

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.key === 'Escape') onClose()
    }
    if (open) {
      document.addEventListener('keydown', onKey)
      return () => document.removeEventListener('keydown', onKey)
    }
  }, [open, onClose])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return entries
    return entries.filter((e) =>
      e.title.toLowerCase().includes(q) || articleBody(role, e.slug).toLowerCase().includes(q))
  }, [query, entries, role])

  if (!render) return null

  const body = articleBody(role, slug)

  return (
    <div
      className={closing ? 'dams-anim-backdrop-out' : 'dams-anim-backdrop'}
      onMouseDown={onClose}
      style={{
        position: 'fixed', inset: 0, background: 'rgba(16,24,40,.45)',
        display: 'flex', justifyContent: 'flex-end', zIndex: 60, overflow: 'hidden',
      }}
    >
      <div
        className={closing ? 'dams-anim-drawer-out' : 'dams-anim-drawer'}
        onMouseDown={(e) => e.stopPropagation()}
        style={{
          background: 'var(--surface, #fff)', width: 'min(760px, 100%)', maxWidth: '100%', height: '100%',
          display: 'flex', flexDirection: 'column', overflow: 'hidden',
          boxShadow: '-8px 0 30px rgba(16,24,40,.18)',
        }}
      >
        <header style={{
          display: 'flex', alignItems: 'center', justifyContent: 'space-between',
          padding: '14px 18px', borderBottom: '1px solid var(--line)', flexShrink: 0,
        }}>
          <strong style={{ fontSize: '0.95rem', color: 'var(--navy)' }}>Help</strong>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close help"
            style={{ background: 'none', border: 'none', fontSize: '1.1rem', color: 'var(--muted)', cursor: 'pointer' }}
          >
            ✕
          </button>
        </header>

        <div style={{ display: 'flex', flex: 1, minHeight: 0, overflow: 'hidden' }}>
          <nav style={{
            width: 'clamp(180px, 32%, 240px)', flexShrink: 0,
            borderRight: '1px solid var(--line)', padding: 14,
            display: 'flex', flexDirection: 'column', gap: 8, minHeight: 0, overflowY: 'auto',
          }}>
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search help…"
              style={{
                border: '1.5px solid var(--line)', borderRadius: 8, padding: '8px 10px',
                fontSize: '0.82rem', outline: 'none', width: '100%',
              }}
            />
            {filtered.length === 0 && (
              <span style={{ fontSize: '0.78rem', color: 'var(--faint)' }}>No matching articles.</span>
            )}
            {filtered.map((e) => (
              <button
                key={e.slug}
                type="button"
                onClick={() => setSlug(e.slug)}
                style={{
                  textAlign: 'left', border: 'none', cursor: 'pointer', borderRadius: 7,
                  padding: '8px 10px', fontSize: '0.83rem', lineHeight: 1.3,
                  fontWeight: e.slug === slug ? 700 : 500,
                  background: e.slug === slug ? 'var(--navy)' : 'transparent',
                  color: e.slug === slug ? '#fff' : 'var(--ink)',
                }}
              >
                {e.title}
              </button>
            ))}
          </nav>

          <article
            className="dams-help-body"
            style={{
              flex: 1, minWidth: 0, minHeight: 0,
              padding: '20px 26px', overflowY: 'auto', overflowX: 'hidden',
            }}
          >
            {body
              ? (
                <Markdown
                  remarkPlugins={[remarkGfm]}
                  components={{
                    // Keep wide tables inside their own horizontal scroller.
                    table: ({ node: _node, ...props }) => (
                      <div className="dams-help-table-wrap"><table {...props} /></div>
                    ),
                  }}
                >
                  {body}
                </Markdown>
              )
              : <p style={{ color: 'var(--faint)', fontSize: '0.85rem' }}>Select an article.</p>}
          </article>
        </div>
      </div>
    </div>
  )
}

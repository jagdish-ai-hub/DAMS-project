import { useState } from 'react'
import { useAuth } from '../auth/useAuth'
import HelpDrawer from './HelpDrawer'
import { HELP_MANIFEST, ROLE_TO_HELP } from './manifest'

/**
 * Per-screen contextual "?" (AGENT.md in-app help): opens the Help Center straight
 * at one article. Renders nothing when the slug is not in the signed-in role's
 * article set — each role sees only its own articles, never another role's.
 */
export default function HelpButton({ slug, label }: { slug: string; label?: string }) {
  const { user } = useAuth()
  const [open, setOpen] = useState(false)
  if (!user) return null
  const entries = HELP_MANIFEST[ROLE_TO_HELP[user.role]] ?? []
  if (!entries.some((e) => e.slug === slug)) return null

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        title={label ?? 'Help for this screen'}
        aria-label={label ?? 'Help for this screen'}
        style={{
          border: '1px solid var(--line)', background: 'var(--surface)', color: 'var(--muted)',
          borderRadius: '50%', width: 26, height: 26, minWidth: 26, fontSize: '0.8rem', fontWeight: 800,
          cursor: 'pointer', display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
        }}
      >
        ?
      </button>
      <HelpDrawer
        role={ROLE_TO_HELP[user.role]}
        open={open}
        onClose={() => setOpen(false)}
        initialSlug={slug}
      />
    </>
  )
}

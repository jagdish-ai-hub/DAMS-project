import { ROLE_LABEL } from '../auth/roleLabels'
import { useAuth } from '../auth/useAuth'
import { ErrorBanner, ghostBtn } from './ui'
import { useRoleSwitch } from './useRoleSwitch'

/**
 * Full-width strip under the header while the user is working in a role other than their own
 * (plan.md rev 55). It says which role and branch, reminds them entries still carry their own
 * name, and offers a one-tap way back.
 */
export default function ActingRoleBanner() {
  const { user } = useAuth()
  const { switchBack, busy, error } = useRoleSwitch()

  if (!user || !user.isActing) return null

  return (
    <div
      role="status"
      className="dams-anim-notice"
      style={{
        background: 'var(--amber-bg)', borderBottom: '1px solid #EAD3AE', borderLeft: '4px solid var(--amber)',
        padding: '9px clamp(10px, 2.5vw, 20px)', display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap',
        fontSize: '0.82rem', color: 'var(--ink)',
      }}
    >
      <span style={{ flex: 1, minWidth: 220 }}>
        You&rsquo;re working as{' '}
        <strong>
          {ROLE_LABEL[user.role]}
          {user.actingBranchLabel ? ` · ${user.actingBranchLabel}` : ''}
        </strong>
        . Entries are recorded under <strong>{user.name}</strong>.
      </span>
      {error && <ErrorBanner message={error} />}
      <button
        type="button"
        onClick={() => { void switchBack() }}
        disabled={busy}
        style={{ ...ghostBtn, background: 'var(--surface)', minHeight: 32, opacity: busy ? 0.6 : 1 }}
      >
        Switch back to {ROLE_LABEL[user.primaryRole]}
      </button>
    </div>
  )
}

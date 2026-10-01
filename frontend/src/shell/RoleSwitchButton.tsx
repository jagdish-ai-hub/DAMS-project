import { useState } from 'react'
import { useAuth } from '../auth/useAuth'
import RoleSwitchModal from './RoleSwitchModal'

/**
 * The header's "Switch role" button, sitting beside Help (plan.md rev 55). Shown to the Owner
 * always, and to anyone else only once the Owner has granted them an extra role.
 */
export default function RoleSwitchButton() {
  const { user } = useAuth()
  const [open, setOpen] = useState(false)

  if (!user || !user.canSwitchRole) return null

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        title="Work in another role or branch"
        style={{
          background: user.isActing ? 'rgba(255,214,120,.28)' : 'rgba(255,255,255,.14)',
          color: '#fff', border: 'none', borderRadius: 7,
          padding: '6px 12px', fontSize: '0.83rem', fontWeight: 600, cursor: 'pointer',
          minHeight: 32, display: 'flex', alignItems: 'center', whiteSpace: 'nowrap',
        }}
      >
        ⇄ Switch role
      </button>
      {open && <RoleSwitchModal onClose={() => setOpen(false)} />}
    </>
  )
}

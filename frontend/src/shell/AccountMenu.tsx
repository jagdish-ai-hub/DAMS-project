import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import type { Role } from '../auth/AuthContext'

const ROLE_LABEL: Record<Role, string> = {
  SUPER_ADMIN: 'Super Admin',
  OWNER: 'Owner',
  FINANCE_MANAGER: 'Finance Manager',
  ACCOUNTANT: 'Accountant',
  CASHIER: 'Cashier',
}

/**
 * Topbar account menu — name, role, branch context, and Logout.
 * Testers switch accounts constantly in the test phase, so Logout is always one click away.
 */
export default function AccountMenu() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', onClickOutside)
    return () => document.removeEventListener('mousedown', onClickOutside)
  }, [])

  if (!user) return null

  const initials = user.name
    .split(' ')
    .map((p) => p[0])
    .filter(Boolean)
    .slice(0, 2)
    .join('')
    .toUpperCase()

  const branchContext =
    user.role === 'CASHIER'
      ? user.homeBranchId
        ? `Branch #${user.homeBranchId}`
        : 'No home branch set'
      : user.role === 'ACCOUNTANT'
        ? user.branchIds.length > 0
          ? `${user.branchIds.length} branch${user.branchIds.length === 1 ? '' : 'es'}`
          : 'No branches assigned'
        : user.role === 'SUPER_ADMIN'
          ? 'Platform'
          : 'All branches'

  function handleLogout() {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="menu"
        aria-expanded={open}
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: 8,
          background: 'rgba(255,255,255,.12)',
          border: 'none',
          color: '#fff',
          borderRadius: 8,
          padding: '5px 10px 5px 6px',
          cursor: 'pointer',
          minHeight: 36,
        }}
      >
        <span style={{
          width: 26, height: 26, borderRadius: '50%',
          background: 'rgba(255,255,255,.22)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: '0.7rem', fontWeight: 700, flexShrink: 0,
        }}>
          {initials || '?'}
        </span>
        <span style={{
          fontSize: '0.8rem', fontWeight: 600,
          maxWidth: 'clamp(70px, 18vw, 140px)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
        }}>
          {user.name || 'Account'}
        </span>
        <span style={{ fontSize: '0.6rem', opacity: 0.8, flexShrink: 0 }}>▼</span>
      </button>

      {open && (
        <div
          role="menu"
          className="dams-anim-menu"
          style={{
            position: 'absolute',
            right: 0,
            top: 'calc(100% + 6px)',
            background: 'var(--surface)',
            color: 'var(--ink)',
            border: '1px solid var(--line)',
            borderRadius: 10,
            boxShadow: 'var(--shadow-lift)',
            minWidth: 210,
            maxWidth: 'calc(100vw - 24px)',
            overflow: 'hidden',
            zIndex: 50,
          }}
        >
          <div style={{ padding: '12px 14px', borderBottom: '1px solid var(--line)' }}>
            <div style={{ fontWeight: 700, fontSize: '0.85rem' }}>{user.name}</div>
            <div style={{ fontSize: '0.76rem', color: 'var(--muted)', marginTop: 2 }}>
              {ROLE_LABEL[user.role]} · {branchContext}
            </div>
            {user.orgId != null && (
              <div style={{ fontSize: '0.72rem', color: 'var(--faint)', marginTop: 2 }}>
                Org #{user.orgId}
              </div>
            )}
          </div>
          <button
            onClick={handleLogout}
            role="menuitem"
            style={{
              width: '100%',
              textAlign: 'left',
              background: 'transparent',
              border: 'none',
              padding: '12px 14px',
              fontSize: '0.83rem',
              fontWeight: 600,
              color: 'var(--red)',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              minHeight: 44,
            }}
          >
            Log out
          </button>
        </div>
      )}
    </div>
  )
}

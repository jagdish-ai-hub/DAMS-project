import { Routes, Route, Navigate, NavLink } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import type { Role } from '../auth/AuthContext'
import AccountMenu from './AccountMenu'
import RoleLanding from './RoleLanding'
import OrganizationsPage from '../superadmin/OrganizationsPage'
import TeamAndBranchesPage from '../owner/TeamAndBranchesPage'
import MastersPage from '../owner/MastersPage'
import SettingsPage from '../settings/SettingsPage'
import CashierHomePage from '../cashier/CashierHomePage'
import NewReceiptPage from '../cashier/NewReceiptPage'
import MyEntriesPage from '../cashier/MyEntriesPage'

type NavItem = { to: string; label: string; end?: boolean }

const NAV_BY_ROLE: Record<Role, NavItem[]> = {
  SUPER_ADMIN: [
    { to: 'organizations', label: 'Organizations' },
    { to: 'settings', label: 'Settings' },
  ],
  OWNER: [
    { to: '.', label: 'Home', end: true },
    { to: 'team', label: 'Team & Branches' },
    { to: 'masters', label: 'Masters' },
    { to: 'settings', label: 'Settings' },
  ],
  FINANCE_MANAGER: [{ to: '.', label: 'Home', end: true }, { to: 'settings', label: 'Settings' }],
  ACCOUNTANT: [{ to: '.', label: 'Home', end: true }, { to: 'settings', label: 'Settings' }],
  CASHIER: [
    { to: '.', label: 'Home', end: true },
    { to: 'my-entries', label: 'My Entries' },
    { to: 'settings', label: 'Settings' },
  ],
}

/**
 * Authenticated app frame: navy topbar (logo + role-conditional nav + account menu),
 * then the routed content. The view is driven entirely by the JWT's role — there is
 * no client-side role switch anywhere (see AGENT.md auth section).
 */
export default function AppShell() {
  const { user } = useAuth()

  if (!user) {
    return <Navigate to="/login" replace />
  }

  const isSuperAdmin = user.role === 'SUPER_ADMIN'
  const isOwner = user.role === 'OWNER'
  const isCashier = user.role === 'CASHIER'
  const navItems = NAV_BY_ROLE[user.role]

  const homeElement = isSuperAdmin
    ? <Navigate to="organizations" replace />
    : isCashier
      ? <CashierHomePage />
      : <RoleLanding />

  return (
    <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', background: 'var(--bg)' }}>
      <header style={{
        background: 'var(--navy)', color: '#fff', padding: '0 20px',
        display: 'flex', alignItems: 'center', gap: 22, height: 52,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <div style={{
            width: 28, height: 28, borderRadius: 7, background: 'rgba(255,255,255,.14)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontWeight: 700, fontSize: '0.72rem',
          }}>
            DA
          </div>
          <span style={{ fontWeight: 700, fontSize: '0.95rem' }}>DAMS</span>
        </div>

        <nav style={{ display: 'flex', gap: 4, flex: 1 }}>
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              style={({ isActive }) => ({
                color: '#fff', textDecoration: 'none', fontSize: '0.83rem', fontWeight: 600,
                padding: '6px 12px', borderRadius: 7,
                background: isActive ? 'rgba(255,255,255,.16)' : 'transparent',
              })}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <AccountMenu />
      </header>

      <main style={{ flex: 1, width: '100%', maxWidth: 1200, margin: '0 auto', padding: '24px 20px' }}>
        <Routes>
          <Route index element={homeElement} />
          {isSuperAdmin && <Route path="organizations" element={<OrganizationsPage />} />}
          {isOwner && <Route path="team" element={<TeamAndBranchesPage />} />}
          {isOwner && <Route path="masters" element={<MastersPage />} />}
          {isCashier && <Route path="new-receipt" element={<NewReceiptPage />} />}
          {isCashier && <Route path="my-entries" element={<MyEntriesPage />} />}
          <Route path="settings" element={<SettingsPage />} />
          <Route path="*" element={<Navigate to="." replace />} />
        </Routes>
      </main>
    </div>
  )
}

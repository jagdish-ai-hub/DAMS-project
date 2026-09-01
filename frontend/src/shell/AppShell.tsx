import { useState } from 'react'
import { Routes, Route, Navigate, NavLink, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import type { Role } from '../auth/AuthContext'
import AccountMenu from './AccountMenu'
import HelpDrawer from '../help/HelpDrawer'
import { ROLE_TO_HELP } from '../help/manifest'
import RoleLanding from './RoleLanding'
import OrganizationsPage from '../superadmin/OrganizationsPage'
import TeamAndBranchesPage from '../owner/TeamAndBranchesPage'
import MastersPage from '../owner/MastersPage'
import SettingsPage from '../settings/SettingsPage'
import CashierHomePage from '../cashier/CashierHomePage'
import NewReceiptPage from '../cashier/NewReceiptPage'
import NewExpensePage from '../cashier/NewExpensePage'
import CashPage from '../cashier/CashPage'
import MyEntriesPage from '../cashier/MyEntriesPage'
import ReviewQueuePage from '../accountant/ReviewQueuePage'
import FmQueuePage from '../finance/FmQueuePage'
import OverrideAuditPage from '../overrideaudit/OverrideAuditPage'
import DashboardPage from '../owner/DashboardPage'

type NavItem = { to: string; label: string; end?: boolean }

// Absolute paths on purpose. AppShell is mounted at `/app/*`, so a relative `to="settings"`
// resolves against the *current* URL — click it from `/app/override-audit` and you land on
// `/app/override-audit/settings`, which matches nothing and just keeps stacking.
const NAV_BY_ROLE: Record<Role, NavItem[]> = {
  SUPER_ADMIN: [
    { to: '/app/organizations', label: 'Organizations' },
    { to: '/app/settings', label: 'Settings' },
  ],
  OWNER: [
    { to: '/app', label: 'Dashboard', end: true },
    { to: '/app/team', label: 'Team & Branches' },
    { to: '/app/masters', label: 'Masters' },
    { to: '/app/override-audit', label: 'Override Audit' },
    { to: '/app/settings', label: 'Settings' },
  ],
  FINANCE_MANAGER: [
    { to: '/app', label: 'Approvals & Claims', end: true },
    { to: '/app/override-audit', label: 'Override Audit' },
    { to: '/app/settings', label: 'Settings' },
  ],
  ACCOUNTANT: [
    { to: '/app', label: 'Review Queue', end: true },
    { to: '/app/settings', label: 'Settings' },
  ],
  CASHIER: [
    { to: '/app', label: 'Home', end: true },
    { to: '/app/cash', label: 'Cash' },
    { to: '/app/my-entries', label: 'My Entries' },
    { to: '/app/settings', label: 'Settings' },
  ],
}

/**
 * Authenticated app frame: navy topbar (logo + role-conditional nav + account menu),
 * then the routed content. The view is driven entirely by the JWT's role — there is
 * no client-side role switch anywhere (see AGENT.md auth section).
 */
export default function AppShell() {
  const { user } = useAuth()
  const [helpOpen, setHelpOpen] = useState(false)
  const location = useLocation()

  if (!user) {
    return <Navigate to="/login" replace />
  }

  const isSuperAdmin = user.role === 'SUPER_ADMIN'
  const isOwner = user.role === 'OWNER'
  const isCashier = user.role === 'CASHIER'
  const isAccountant = user.role === 'ACCOUNTANT'
  const isFinanceManager = user.role === 'FINANCE_MANAGER'
  const navItems = NAV_BY_ROLE[user.role]

  const homeElement = isSuperAdmin
    ? <Navigate to="/app/organizations" replace />
    : isCashier
      ? <CashierHomePage />
      : isAccountant
        ? <ReviewQueuePage />
        : isFinanceManager
          ? <FmQueuePage />
          : isOwner
            ? <DashboardPage />
            : <RoleLanding />

  return (
    <div className="dams-app" style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', background: 'var(--bg)' }}>
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

        <button
          type="button"
          onClick={() => setHelpOpen(true)}
          style={{
            background: 'rgba(255,255,255,.14)', color: '#fff', border: 'none', borderRadius: 7,
            padding: '6px 12px', fontSize: '0.83rem', fontWeight: 600, cursor: 'pointer',
          }}
        >
          ? Help
        </button>

        <AccountMenu />
      </header>

      <HelpDrawer
        role={ROLE_TO_HELP[user.role]}
        open={helpOpen}
        onClose={() => setHelpOpen(false)}
      />

      <main style={{ flex: 1, width: '100%', maxWidth: 1200, margin: '0 auto', padding: '24px 20px' }}>
        {/* Keyed on the path (not the query string) so each screen fades in on arrival,
            but an in-place `?editDoc=…` replace doesn't re-trigger the animation. */}
        <div key={location.pathname} className="dams-route-fade">
          <Routes>
            <Route index element={homeElement} />
            {isSuperAdmin && <Route path="organizations" element={<OrganizationsPage />} />}
            {isOwner && <Route path="team" element={<TeamAndBranchesPage />} />}
            {isOwner && <Route path="masters" element={<MastersPage />} />}
            {isCashier && <Route path="new-receipt" element={<NewReceiptPage />} />}
            {isCashier && <Route path="new-expense" element={<NewExpensePage />} />}
            {isCashier && <Route path="cash" element={<CashPage />} />}
            {isCashier && <Route path="my-entries" element={<MyEntriesPage />} />}
            {(isOwner || isFinanceManager) && <Route path="override-audit" element={<OverrideAuditPage />} />}
            <Route path="settings" element={<SettingsPage />} />
            <Route path="*" element={<Navigate to="/app" replace />} />
          </Routes>
        </div>
      </main>
    </div>
  )
}

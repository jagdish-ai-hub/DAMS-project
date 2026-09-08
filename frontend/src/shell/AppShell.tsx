import { useEffect, useState } from 'react'
import { Routes, Route, Navigate, NavLink, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import type { Role } from '../auth/AuthContext'
import { myEntriesApi } from '../api/myEntries'
import { reviewApi } from '../api/review'
import AccountMenu from './AccountMenu'
import HelpDrawer from '../help/HelpDrawer'
import { ROLE_TO_HELP } from '../help/manifest'
import RoleLanding from './RoleLanding'
import OrganizationsPage from '../superadmin/OrganizationsPage'
import TeamAndBranchesPage from '../owner/TeamAndBranchesPage'
import MastersPage from '../owner/MastersPage'
import SettingsPage from '../settings/SettingsPage'
import CashierHomePage from '../cashier/CashierHomePage'
import EstimatesPage from '../cashier/EstimatesPage'
import NewReceiptPage from '../cashier/NewReceiptPage'
import NewExpensePage from '../cashier/NewExpensePage'
import CashPage from '../cashier/CashPage'
import MyEntriesPage from '../cashier/MyEntriesPage'
import ReviewQueuePage from '../accountant/ReviewQueuePage'
import AwaitingBillsPage from '../accountant/AwaitingBillsPage'
import ReconPage from '../accountant/ReconPage'
import FmQueuePage from '../finance/FmQueuePage'
import ClaimsChasePage from '../finance/ClaimsChasePage'
import OverrideAuditPage from '../overrideaudit/OverrideAuditPage'
import DashboardPage from '../owner/DashboardPage'
import ReceivablesPage from '../owner/ReceivablesPage'
import FloorPage from '../owner/FloorPage'
import MessagesPage from '../owner/MessagesPage'
import StaffPage from '../owner/StaffPage'
import AskDamsPanel from '../owner/AskDamsPanel'

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
    { to: '/app/receivables', label: 'Dues' },
    { to: '/app/floor', label: 'Floor' },
    { to: '/app/claims-chase', label: 'Chase' },
    { to: '/app/messages', label: 'Messages' },
    { to: '/app/recon', label: 'Reconcile' },
    { to: '/app/staff', label: 'Staff' },
    { to: '/app/team', label: 'Team & Branches' },
    { to: '/app/masters', label: 'Masters' },
    { to: '/app/override-audit', label: 'Override Audit' },
    { to: '/app/settings', label: 'Settings' },
  ],
  AUDITOR: [
    { to: '/app', label: 'Dashboard', end: true },
    { to: '/app/override-audit', label: 'Override Audit' },
    { to: '/app/masters', label: 'Masters' },
    { to: '/app/settings', label: 'Settings' },
  ],
  FINANCE_MANAGER: [
    { to: '/app', label: 'Approvals & Claims', end: true },
    { to: '/app/claims-chase', label: 'Chase' },
    { to: '/app/receivables', label: 'Dues' },
    { to: '/app/floor', label: 'Floor' },
    { to: '/app/messages', label: 'Messages' },
    { to: '/app/recon', label: 'Reconcile' },
    { to: '/app/staff', label: 'Staff' },
    { to: '/app/override-audit', label: 'Override Audit' },
    { to: '/app/settings', label: 'Settings' },
  ],
  ACCOUNTANT: [
    { to: '/app', label: 'Review Queue', end: true },
    { to: '/app/awaiting-bills', label: 'Bills' },
    { to: '/app/recon', label: 'Reconcile' },
    { to: '/app/receivables', label: 'Dues' },
    { to: '/app/staff', label: 'Staff' },
    { to: '/app/floor', label: 'Floor' },
    { to: '/app/settings', label: 'Settings' },
  ],
  CASHIER: [
    { to: '/app', label: 'Home', end: true },
    { to: '/app/cash', label: 'Cash' },
    { to: '/app/receivables', label: 'Dues' },
    { to: '/app/floor', label: 'Floor' },
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
  const [navOpen, setNavOpen] = useState(false)
  const [askOpen, setAskOpen] = useState(false)
  const location = useLocation()

  // Nav count pills — polled every 5 min + on route change, fail silently.
  const [badges, setBadges] = useState<Record<string, number>>({})
  useEffect(() => {
    if (!user) return
    const role = user.role
    let live = true
    async function load() {
      try {
        if (role === 'CASHIER') {
          const { data } = await myEntriesApi.list()
          let queried = 0
          for (const e of data) {
            if (e.queried) queried++
          }
          if (live) setBadges(queried > 0 ? { '/app/my-entries': queried } : {})
        } else if (role === 'ACCOUNTANT') {
          const [r, e, c] = await Promise.all([
            reviewApi.receiptQueue(),
            reviewApi.expenseQueue(),
            reviewApi.cashQueue(),
          ])
          const total = r.data.length + e.data.length + c.data.length
          if (live) setBadges(total > 0 ? { '/app': total } : {})
        } else if (role === 'FINANCE_MANAGER') {
          const [r, e, c] = await Promise.all([
            reviewApi.fmQueue('receipt'),
            reviewApi.fmQueue('expense'),
            reviewApi.fmQueue('cash'),
          ])
          const total = r.data.awaitingApproval.length + e.data.awaitingApproval.length + c.data.awaitingApproval.length
          if (live) setBadges(total > 0 ? { '/app': total } : {})
        }
      } catch {
        // Badges are hints only — never surface an error banner for them.
      }
    }
    load()
    const timer = setInterval(load, 5 * 60 * 1000)
    return () => { live = false; clearInterval(timer) }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user?.role, location.pathname])

  // Hooks must run before the early return below (rules-of-hooks) — so the
  // role gate is computed here from the nullable user, not after the return.
  // Ask DAMS answers from org aggregates the Accountant/Cashier roles must not
  // see — Owner and FM only, enforced again server-side per endpoint.
  const canAsk = user != null && (user.role === 'OWNER' || user.role === 'FINANCE_MANAGER')

  useEffect(() => {
    if (!canAsk) return
    function onKey(e: KeyboardEvent) {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setAskOpen(true)
      }
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [canAsk])

  if (!user) {
    return <Navigate to="/login" replace />
  }

  const isSuperAdmin = user.role === 'SUPER_ADMIN'
  const isOwner = user.role === 'OWNER'
  const isCashier = user.role === 'CASHIER'
  const isAccountant = user.role === 'ACCOUNTANT'
  const isFinanceManager = user.role === 'FINANCE_MANAGER'
  const isAuditor = user.role === 'AUDITOR'
  const navItems = NAV_BY_ROLE[user.role]

  const homeElement = isSuperAdmin
    ? <Navigate to="/app/organizations" replace />
    : isAuditor
      ? <DashboardPage />
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
        position: 'sticky', top: 0, zIndex: 40,
        background: 'var(--navy)', color: '#fff', padding: '0 clamp(10px, 2.5vw, 20px)',
        display: 'flex', alignItems: 'center', gap: 'clamp(10px, 2vw, 22px)', height: 52,
      }}>
        {/* Mobile hamburger toggle (< 1024px) */}
        <button
          type="button"
          onClick={() => setNavOpen(!navOpen)}
          className="flex lg:hidden"
          aria-label="Toggle navigation"
          aria-expanded={navOpen}
          style={{
            background: 'none', border: 'none', color: '#fff', cursor: 'pointer',
            padding: 6, minWidth: 40, minHeight: 40, display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}
        >
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            {navOpen ? (
              <>
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </>
            ) : (
              <>
                <line x1="3" y1="12" x2="21" y2="12" />
                <line x1="3" y1="6" x2="21" y2="6" />
                <line x1="3" y1="18" x2="21" y2="18" />
              </>
            )}
          </svg>
        </button>

        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <div style={{
            width: 28, height: 28, borderRadius: 7, background: 'rgba(255,255,255,.14)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontWeight: 700, fontSize: '0.72rem',
          }}>
            DA
          </div>
          <span style={{ fontWeight: 700, fontSize: '0.95rem' }}>DAMS</span>
        </div>

        {/* Desktop Navigation (>= 1024px) */}
        <nav className="hidden lg:flex" style={{ gap: 4, flex: 1 }}>
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
              {badges[item.to] != null && (
                <span style={{
                  marginLeft: 6, fontSize: '0.66rem', fontWeight: 800, background: 'var(--amber)',
                  color: '#fff', borderRadius: 999, padding: '1px 7px',
                }}>
                  {badges[item.to]}
                </span>
              )}
            </NavLink>
          ))}
        </nav>

        <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 10 }}>
          {canAsk && (
            <button
              type="button"
              onClick={() => setAskOpen(true)}
              title="Ask DAMS (Ctrl+K)"
              style={{
                background: 'rgba(255,255,255,.14)', color: '#fff', border: 'none', borderRadius: 7,
                padding: '6px 12px', fontSize: '0.83rem', fontWeight: 600, cursor: 'pointer',
                minHeight: 32, display: 'flex', alignItems: 'center',
              }}
            >
              ✦ Ask DAMS
            </button>
          )}
          <button
            type="button"
            onClick={() => setHelpOpen(true)}
            style={{
              background: 'rgba(255,255,255,.14)', color: '#fff', border: 'none', borderRadius: 7,
              padding: '6px 12px', fontSize: '0.83rem', fontWeight: 600, cursor: 'pointer',
              minHeight: 32, display: 'flex', alignItems: 'center',
            }}
          >
            ? Help
          </button>

          <AccountMenu />
        </div>
      </header>

      {/* Mobile navigation slide-out drawer (< 1024px) */}
      {navOpen && (
        <div
          className="dams-anim-backdrop lg:hidden"
          onClick={() => setNavOpen(false)}
          style={{
            position: 'fixed', inset: 0, background: 'rgba(16,24,40,.45)', zIndex: 45,
          }}
        >
          <div
            onClick={(e) => e.stopPropagation()}
            style={{
              position: 'absolute', top: 52, left: 0, bottom: 0, width: 'min(280px, 80vw)',
              background: 'var(--navy)', color: '#fff', padding: '16px 12px',
              display: 'flex', flexDirection: 'column', gap: 6, boxShadow: 'var(--shadow-lift)',
              overflowY: 'auto',
            }}
          >
            <div style={{ fontSize: '0.75rem', fontWeight: 700, color: 'rgba(255,255,255,.5)', padding: '4px 10px', textTransform: 'uppercase', letterSpacing: 0.5 }}>
              Navigation
            </div>
            {navItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                onClick={() => setNavOpen(false)}
                style={({ isActive }) => ({
                  color: '#fff', textDecoration: 'none', fontSize: '0.9rem', fontWeight: 600,
                  padding: '10px 14px', borderRadius: 8,
                  background: isActive ? 'rgba(255,255,255,.18)' : 'transparent',
                  display: 'flex', alignItems: 'center', minHeight: 44,
                })}
              >
                {item.label}
                {badges[item.to] != null && (
                  <span style={{
                    marginLeft: 8, fontSize: '0.68rem', fontWeight: 800, background: 'var(--amber)',
                    color: '#fff', borderRadius: 999, padding: '1px 8px',
                  }}>
                    {badges[item.to]}
                  </span>
                )}
              </NavLink>
            ))}
          </div>
        </div>
      )}

      <HelpDrawer
        role={ROLE_TO_HELP[user.role]}
        open={helpOpen}
        onClose={() => setHelpOpen(false)}
      />

      {askOpen && canAsk && (
        <AskDamsPanel scopeLabel="All branches" onClose={() => setAskOpen(false)} />
      )}

      <main style={{ flex: 1, width: '100%', maxWidth: 1200, margin: '0 auto', padding: 'clamp(14px, 2.5vw, 24px) clamp(10px, 2.5vw, 20px)' }}>
        {/* Keyed on the path (not the query string) so each screen fades in on arrival,
            but an in-place `?editDoc=…` replace doesn't re-trigger the animation. */}
        <div key={location.pathname} className="dams-route-fade">
          <Routes>
            <Route index element={homeElement} />
            {isSuperAdmin && <Route path="organizations" element={<OrganizationsPage />} />}
            {isOwner && <Route path="team" element={<TeamAndBranchesPage />} />}
            {(isOwner || isAuditor) && <Route path="masters" element={<MastersPage readOnly={isAuditor} />} />}
            {isCashier && <Route path="new-receipt" element={<NewReceiptPage />} />}
            {isCashier && <Route path="new-expense" element={<NewExpensePage />} />}
            {(isCashier || isAccountant) && <Route path="cash" element={<CashPage />} />}
            {isCashier && <Route path="my-entries" element={<MyEntriesPage />} />}
            {(isOwner || isFinanceManager || isAuditor) && <Route path="override-audit" element={<OverrideAuditPage />} />}
            {(isOwner || isFinanceManager || isAccountant || isCashier) && <Route path="receivables" element={<ReceivablesPage />} />}
            {(isOwner || isFinanceManager || isAccountant || isCashier) && <Route path="floor" element={<FloorPage />} />}
            {(isOwner || isFinanceManager) && <Route path="claims-chase" element={<ClaimsChasePage />} />}
            {(isOwner || isFinanceManager || isAccountant) && <Route path="messages" element={<MessagesPage />} />}
            {(isOwner || isFinanceManager || isAccountant) && <Route path="recon" element={<ReconPage />} />}
            {(isOwner || isFinanceManager || isAccountant) && <Route path="staff" element={<StaffPage />} />}
            {(isCashier || isAccountant || isFinanceManager || isOwner) && <Route path="estimates" element={<EstimatesPage />} />}
            {(isAccountant || isFinanceManager || isOwner) && <Route path="awaiting-bills" element={<AwaitingBillsPage />} />}
            <Route path="settings" element={<SettingsPage />} />
            <Route path="*" element={<Navigate to="/app" replace />} />
          </Routes>
        </div>
      </main>
    </div>
  )
}

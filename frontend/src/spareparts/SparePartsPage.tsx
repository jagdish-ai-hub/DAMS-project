import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import { Skeleton, card } from '../shell/ui'
import BillPage from './BillPage'
import HistoryPage from './HistoryPage'
import OverviewPage from './OverviewPage'
import RequestsPage from './RequestsPage'
import StockPage from './StockPage'
import UploadPage from './UploadPage'
import { useSpareAccess } from './SpareAccessContext'
import { Notice } from './spareUi'

/**
 * Spare Parts home: a heading, a strip of sections the signed-in person is allowed to use, and the section itself.
 * Which sections show follows what the server said in /access; the server still refuses anything not allowed.
 */
export default function SparePartsPage() {
  const { access, loading, visible, can } = useSpareAccess()

  if (loading) {
    return <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}><Skeleton height={36} width={240} /><Skeleton height={180} radius={10} /></div>
  }
  if (!visible) {
    return (
      <section style={card}>
        <Notice tone="warn">
          Spare Parts is not turned on for your organization, or your role cannot use it. Ask your Super Admin to switch it on.
        </Notice>
      </section>
    )
  }

  const sections = [
    access.canSeeDashboard ? { to: 'overview', label: 'Overview' } : null,
    { to: 'stock', label: 'Stock' },
    access.permissions.includes('UPLOAD_ADD') && !access.owner ? { to: 'upload', label: 'Upload & add' } : null,
    access.permissions.includes('BILL_PART') && !access.owner ? { to: 'bill', label: 'Bill a part' } : null,
    { to: 'requests', label: 'Requests' },
    { to: 'history', label: 'History' },
  ].filter((s): s is { to: string; label: string } => s !== null)

  const mine = access.branches.filter((b) => access.actBranchIds.includes(b.id)).map((b) => b.code)
  const workingAt = access.owner
    ? 'View only. Use Switch role to act as Inventory Manager at a branch.'
    : mine.length > 0 ? `Working at ${mine.join(' · ')}. You can see every branch’s stock.` : 'No branch is assigned to you yet.'

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      <div>
        <h1 style={{ fontSize: '1.15rem', fontWeight: 700, color: 'var(--navy)', margin: 0 }}>Spare Parts</h1>
        <p style={{ fontSize: '0.82rem', color: 'var(--muted)', margin: '2px 0 0' }}>{workingAt}</p>
      </div>

      <nav aria-label="Spare Parts sections" style={{ display: 'flex', gap: 4, overflowX: 'auto', padding: 4, background: 'var(--surface)', border: '1px solid var(--line)', borderRadius: 11, boxShadow: 'var(--shadow-xs)', alignSelf: 'flex-start', maxWidth: '100%' }}>
        {sections.map((s) => (
          <NavLink
            key={s.to}
            to={`/app/spare-parts/${s.to}`}
            style={({ isActive }) => ({
              textDecoration: 'none', whiteSpace: 'nowrap', fontSize: '0.83rem', fontWeight: 600, padding: '7px 14px', borderRadius: 8,
              minHeight: 36, display: 'inline-flex', alignItems: 'center',
              color: isActive ? '#fff' : 'var(--ink)',
              background: isActive ? 'var(--navy)' : 'transparent',
            })}
          >
            {s.label}
          </NavLink>
        ))}
      </nav>

      <Routes>
        <Route index element={<Navigate to={access.canSeeDashboard ? 'overview' : 'stock'} replace />} />
        {access.canSeeDashboard && <Route path="overview" element={<OverviewPage />} />}
        <Route path="stock" element={<StockPage />} />
        {can('UPLOAD_ADD') && <Route path="upload" element={<UploadPage />} />}
        {can('BILL_PART') && <Route path="bill" element={<BillPage />} />}
        <Route path="requests" element={<RequestsPage />} />
        <Route path="history" element={<HistoryPage />} />
        <Route path="*" element={<Navigate to="/app/spare-parts" replace />} />
      </Routes>
    </div>
  )
}

import { Routes, Route, Navigate, useLocation } from 'react-router-dom'
import LoginPage from './auth/LoginPage'
import AcceptInvitePage from './auth/AcceptInvitePage'
import AppShell from './shell/AppShell'
import ProtectedRoute from './auth/ProtectedRoute'
import MaintenancePage from './maintenance/MaintenancePage'
import { MAINTENANCE_MODE, isMaintenanceBlocked, maintenanceBypassed } from './maintenance/maintenance'

export default function App() {
  const { search } = useLocation()

  // Maintenance mode (plan.md revs 59-60), currently off — see maintenance/maintenance.ts. When on,
  // every screen shows the maintenance page unless this tab came in through the ?bypass= link.
  if (isMaintenanceBlocked(MAINTENANCE_MODE, maintenanceBypassed(search))) {
    return <MaintenancePage />
  }

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/accept-invite" element={<AcceptInvitePage />} />

      {/* Everything under /app requires a valid session */}
      <Route
        path="/app/*"
        element={
          <ProtectedRoute>
            <AppShell />
          </ProtectedRoute>
        }
      />

      <Route path="/" element={<Navigate to="/app" replace />} />
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  )
}

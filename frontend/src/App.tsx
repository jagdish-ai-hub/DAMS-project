import { Routes, Route, Navigate, useLocation } from 'react-router-dom'
import LoginPage from './auth/LoginPage'
import AcceptInvitePage from './auth/AcceptInvitePage'
import AppShell from './shell/AppShell'
import ProtectedRoute from './auth/ProtectedRoute'
import MaintenancePage from './maintenance/MaintenancePage'
import { MAINTENANCE_MODE, isMaintenanceBlocked, maintenanceBypassed } from './maintenance/maintenance'

export default function App() {
  const { search } = useLocation()

  // TEMPORARY maintenance mode (plan.md rev 59): every screen, the sign-in page included, shows
  // the maintenance page — unless this tab came in through the private ?bypass= link.
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

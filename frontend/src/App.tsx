import { Routes, Route, Navigate } from 'react-router-dom'
import LoginPage from './auth/LoginPage'
import AcceptInvitePage from './auth/AcceptInvitePage'
import AppShell from './shell/AppShell'
import ProtectedRoute from './auth/ProtectedRoute'

export default function App() {
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

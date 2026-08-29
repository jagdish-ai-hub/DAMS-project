import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuth } from './useAuth'

interface Props {
  children: ReactNode
}

/**
 * Redirects unauthenticated users to /login.
 * Every /app route is wrapped in this — nobody lands on any screen without authenticating
 * first. See AGENT.md — auth section.
 */
export default function ProtectedRoute({ children }: Props) {
  const { isAuthenticated } = useAuth()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  return <>{children}</>
}

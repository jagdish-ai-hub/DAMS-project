import { useContext } from 'react'
import { AuthContext } from './AuthContext'

/**
 * Convenience hook for consuming the auth context.
 * Usage: const { user, login, logout } = useAuth()
 */
export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used inside AuthProvider')
  }
  return ctx
}

import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { authApi } from '../api/auth'
import type { Role } from '../auth/AuthContext'
import { useAuth } from '../auth/useAuth'

function apiError(err: unknown, fallback: string) {
  return (err as { response?: { data?: { message?: string } } })?.response?.data?.message ?? fallback
}

/**
 * The two server calls behind role switching (plan.md rev 55). The server checks the grant and
 * issues a new token whose role IS the acting role — this hook only stores that token and sends
 * the user to the new role's home screen. It never picks a role on the client's own authority.
 */
export function useRoleSwitch() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  /** Switch into `role` at `branch`. Resolves true on success so a caller can close its dialog. */
  async function switchTo(role: Role, branch: { id: number; code: string }): Promise<boolean> {
    return run(() => authApi.switchRole(role, branch.id), branch.code)
  }

  /** Back to the user's own role. */
  async function switchBack(): Promise<boolean> {
    if (!user) return false
    return run(() => authApi.switchRole(user.primaryRole), null)
  }

  async function run(
    call: () => ReturnType<typeof authApi.switchRole>,
    branchLabel: string | null,
  ): Promise<boolean> {
    setBusy(true)
    setError('')
    try {
      const { data } = await call()
      login(data.accessToken, data.name, { canSwitchRole: data.canSwitchRole, actingBranchLabel: branchLabel })
      navigate('/app', { replace: true })
      return true
    } catch (err: unknown) {
      setError(apiError(err, 'Could not switch role — please try again'))
      return false
    } finally {
      setBusy(false)
    }
  }

  return { switchTo, switchBack, busy, error, clearError: () => setError('') }
}

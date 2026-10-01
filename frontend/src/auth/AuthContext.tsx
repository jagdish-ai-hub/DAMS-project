import { createContext, useCallback, useState, type ReactNode } from 'react'
import { jwtDecode } from 'jwt-decode'
import { ACCESS_TOKEN_KEY, SESSION_EXTRAS_KEY, USER_NAME_KEY, clearSession, readExtras, type SessionExtras } from './session'

export type Role =
  | 'SUPER_ADMIN'
  | 'OWNER'
  | 'FINANCE_MANAGER'
  | 'ACCOUNTANT'
  | 'CASHIER'

export interface AuthUser {
  userId: number
  orgId: number | null
  /** The ACTING role — what every screen and gate follows. Equals `primaryRole` unless switched. */
  role: Role
  name: string
  /** ACCOUNTANT's assigned branches (empty for other roles). */
  branchIds: number[]
  /** CASHIER's single posting branch (null for other roles). */
  homeBranchId: number | null
  /** The user's own role (plan.md rev 55). */
  primaryRole: Role
  /** True while working in a role other than their own. */
  isActing: boolean
  /** The one branch a switched session is scoped to (null when not switched). */
  actingBranchId: number | null
  /** Code of that branch for display, e.g. "OOR". */
  actingBranchLabel: string | null
  /** Show the Switch role button: Owner always, others only with an Owner-granted extra role. */
  canSwitchRole: boolean
}

interface AuthContextValue {
  user: AuthUser | null
  /** Store the token + identity from a successful login / accept-invite / switch-role response. */
  login: (accessToken: string, name: string, extras?: Partial<SessionExtras>) => void
  logout: () => void
  isAuthenticated: boolean
}

interface JwtPayload {
  sub: string
  orgId: number | null
  role: Role
  primaryRole?: Role | null
  actingBranchId?: number | null
  branchIds: number[] | null
  homeBranchId: number | null
  exp: number
}

export const AuthContext = createContext<AuthContextValue | null>(null)

function parseUser(token: string, name: string, extras: SessionExtras): AuthUser | null {
  try {
    const payload = jwtDecode<JwtPayload>(token)
    if (payload.exp * 1000 <= Date.now()) {
      return null
    }
    // Tokens issued before role switching carry no primaryRole — their role is their own.
    const primaryRole = payload.primaryRole ?? payload.role
    const isActing = primaryRole !== payload.role
    return {
      userId: parseInt(payload.sub, 10),
      orgId: payload.orgId ?? null,
      role: payload.role,
      name,
      branchIds: payload.branchIds ?? [],
      homeBranchId: payload.homeBranchId ?? null,
      primaryRole,
      isActing,
      actingBranchId: isActing ? payload.actingBranchId ?? null : null,
      actingBranchLabel: isActing ? extras.actingBranchLabel : null,
      canSwitchRole: extras.canSwitchRole || isActing,
    }
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => {
    // sessionStorage only — a fresh tab / restarted browser starts logged out
    const token = sessionStorage.getItem(ACCESS_TOKEN_KEY)
    const name = sessionStorage.getItem(USER_NAME_KEY) ?? ''
    if (!token) return null
    const parsed = parseUser(token, name, readExtras())
    if (!parsed) clearSession()
    return parsed
  })

  const login = useCallback((accessToken: string, name: string, extras?: Partial<SessionExtras>) => {
    const full: SessionExtras = {
      canSwitchRole: extras?.canSwitchRole === true,
      actingBranchLabel: extras?.actingBranchLabel ?? null,
    }
    sessionStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
    sessionStorage.setItem(USER_NAME_KEY, name)
    sessionStorage.setItem(SESSION_EXTRAS_KEY, JSON.stringify(full))
    setUser(parseUser(accessToken, name, full))
  }, [])

  const logout = useCallback(() => {
    clearSession()
    setUser(null)
  }, [])

  return (
    <AuthContext.Provider value={{ user, login, logout, isAuthenticated: user !== null }}>
      {children}
    </AuthContext.Provider>
  )
}

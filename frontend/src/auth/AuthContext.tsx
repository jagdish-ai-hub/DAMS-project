import { createContext, useCallback, useState, type ReactNode } from 'react'
import { jwtDecode } from 'jwt-decode'
import { ACCESS_TOKEN_KEY, USER_NAME_KEY, clearSession } from './session'

export type Role =
  | 'SUPER_ADMIN'
  | 'OWNER'
  | 'FINANCE_MANAGER'
  | 'ACCOUNTANT'
  | 'CASHIER'

export interface AuthUser {
  userId: number
  orgId: number | null
  role: Role
  name: string
  /** ACCOUNTANT's assigned branches (empty for other roles). */
  branchIds: number[]
  /** CASHIER's single posting branch (null for other roles). */
  homeBranchId: number | null
}

interface AuthContextValue {
  user: AuthUser | null
  /** Store the token + identity from a successful login / accept-invite response. */
  login: (accessToken: string, name: string) => void
  logout: () => void
  isAuthenticated: boolean
}

interface JwtPayload {
  sub: string
  orgId: number | null
  role: Role
  branchIds: number[] | null
  homeBranchId: number | null
  exp: number
}

export const AuthContext = createContext<AuthContextValue | null>(null)

function parseUser(token: string, name: string): AuthUser | null {
  try {
    const payload = jwtDecode<JwtPayload>(token)
    if (payload.exp * 1000 <= Date.now()) {
      return null
    }
    return {
      userId: parseInt(payload.sub, 10),
      orgId: payload.orgId ?? null,
      role: payload.role,
      name,
      branchIds: payload.branchIds ?? [],
      homeBranchId: payload.homeBranchId ?? null,
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
    const parsed = parseUser(token, name)
    if (!parsed) clearSession()
    return parsed
  })

  const login = useCallback((accessToken: string, name: string) => {
    sessionStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
    sessionStorage.setItem(USER_NAME_KEY, name)
    setUser(parseUser(accessToken, name))
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

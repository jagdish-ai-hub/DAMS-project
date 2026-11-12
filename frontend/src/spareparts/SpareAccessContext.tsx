import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { sparePartsApi, type SpareAccess, type SparePermissionKey } from '../api/spareParts'
import { useAuth } from '../auth/useAuth'

/**
 * Whether the Spare Parts module is on for the signed-in user's organization, and what they may do in it. Asked once per
 * login / role switch; the shell uses it to show (or not show) the menu, and the pages use it to show only the buttons
 * the server would accept. The server re-checks every action, so this is a convenience, never the gate.
 *
 * Any failure (module off, old server, network) means "not enabled": the module then adds nothing anywhere.
 */

const OFF: SpareAccess = {
  enabled: false, canView: false, canSeeDashboard: false, owner: false, role: null,
  actBranchIds: [], permissions: [], branches: [],
}

interface SpareAccessValue {
  access: SpareAccess
  loading: boolean
  reload: () => void
  /** True when the module is on and the user may look at stock (Owner, Inventory Manager, Store Keeper). */
  visible: boolean
  can: (permission: SparePermissionKey, branchId?: number | null) => boolean
}

const SpareAccessContext = createContext<SpareAccessValue | null>(null)

export function SpareAccessProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const [access, setAccess] = useState<SpareAccess>(OFF)
  const [loading, setLoading] = useState(true)
  const [tick, setTick] = useState(0)

  // A different person, role or branch (after Switch role) is a different answer.
  const key = user ? `${user.userId}:${user.role}:${user.actingBranchId ?? ''}` : ''

  useEffect(() => {
    if (!user || user.role === 'SUPER_ADMIN') {
      setAccess(OFF)
      setLoading(false)
      return
    }
    let cancelled = false
    setLoading(true)
    async function ask() {
      try {
        const { data } = await sparePartsApi.access()
        if (!cancelled) setAccess(data)
      } catch {
        // Module off, an older server, no network: all mean "not enabled", and the app carries on as before.
        if (!cancelled) setAccess(OFF)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    ask()
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, tick])

  const reload = useCallback(() => setTick((t) => t + 1), [])

  const value = useMemo<SpareAccessValue>(() => ({
    access,
    loading,
    reload,
    visible: access.enabled && access.canView,
    can: (permission, branchId) =>
      !access.owner
      && access.permissions.includes(permission)
      && (branchId == null || access.actBranchIds.includes(branchId)),
  }), [access, loading, reload])

  return <SpareAccessContext.Provider value={value}>{children}</SpareAccessContext.Provider>
}

/** Falls back to "off" outside the provider so a screen rendered on its own (a test) simply shows nothing extra. */
export function useSpareAccess(): SpareAccessValue {
  const ctx = useContext(SpareAccessContext)
  if (ctx) return ctx
  return { access: OFF, loading: false, reload: () => undefined, visible: false, can: () => false }
}

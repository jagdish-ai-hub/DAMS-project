import { useEffect, useState } from 'react'
import { branchesApi } from '../api/branches'

/**
 * Branch id → "CODE · Name" labels for display purposes (account menu, settings summary).
 * The JWT only carries branch ids, so any human-readable branch context needs one lookup.
 * Returns an empty map until loaded (callers fall back to the raw id) — never throws.
 */
export function useBranchNames(enabled = true) {
  const [names, setNames] = useState<Map<number, string>>(new Map())
  useEffect(() => {
    if (!enabled) return
    let live = true
    branchesApi.list()
      .then(({ data }) => {
        if (!live) return
        setNames(new Map(data.map((b) => [b.id, `${b.code} · ${b.name}`])))
      })
      .catch(() => { /* display-only — the id fallback below still renders */ })
    return () => { live = false }
  }, [enabled])
  return names
}

/** "OOR · Rayagada", falling back to "Branch #id" while loading (or when unknown). */
export function branchLabel(names: Map<number, string>, id: number | null | undefined) {
  if (id == null) return null
  return names.get(id) ?? `Branch #${id}`
}

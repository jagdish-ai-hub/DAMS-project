/*
 * TEMPORARY maintenance mode (plan.md rev 59) — FRONTEND ONLY. The backend is untouched and keeps
 * running; this just makes the web app show the maintenance page to anyone who loads it, the
 * sign-in screen included. Everything for it lives in this folder plus one gate in App.tsx. To
 * end it, revert the PR that added them (or set MAINTENANCE_MODE to false).
 *
 * This is a "closed" sign on the door, not a lock: nothing server-side stops a client that
 * already holds a token, and a tab that was open before the deploy keeps running the old app
 * until it is refreshed (or its login expires).
 */
export const MAINTENANCE_MODE = true

/*
 * A private way back in, so whoever is doing the maintenance can still use the app. Open any
 * page with ?bypass=<token> once; that tab then works normally for as long as it stays open.
 * The token ships in the page's JavaScript, so this is a convenience, not security — which is
 * fine, because the backend is not gated either.
 */
export const BYPASS_TOKEN = 'dams-staff-4f7a'
const BYPASS_KEY = 'dams_maintenance_bypass'

/** Has this tab been let past the maintenance page? Reads (and remembers) the ?bypass= link. */
export function maintenanceBypassed(search: string): boolean {
  try {
    if (new URLSearchParams(search).get('bypass') === BYPASS_TOKEN) {
      sessionStorage.setItem(BYPASS_KEY, '1')
    }
    return sessionStorage.getItem(BYPASS_KEY) === '1'
  } catch {
    return false
  }
}

/** True when this visitor should see the maintenance page instead of the requested screen. */
export function isMaintenanceBlocked(enabled: boolean, bypassed: boolean): boolean {
  return enabled && !bypassed
}

/** The hashed app bundle named in an index.html, e.g. "/assets/index-Bw5KtGjn.js". */
export function bundleOf(html: string): string | null {
  const match = /\/assets\/index-[\w-]+\.js/.exec(html)
  return match ? match[0] : null
}

/**
 * Has a newer build been deployed since this page loaded? Maintenance ends with a deploy, so a
 * changed bundle name is the signal to reload — no polling endpoint needed. Returns false in
 * dev (no hashed bundle) and on any network failure.
 */
export async function newBuildAvailable(): Promise<boolean> {
  try {
    const current = bundleOf(
      document.querySelector('script[type="module"][src*="/assets/"]')?.getAttribute('src') ?? '',
    )
    if (!current) return false
    const res = await fetch('/', { cache: 'no-store' })
    const next = bundleOf(await res.text())
    return next !== null && next !== current
  } catch {
    return false
  }
}

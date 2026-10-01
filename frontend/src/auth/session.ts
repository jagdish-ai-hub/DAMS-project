/**
 * Session storage keys and helpers.
 *
 * v1 keeps the session deliberately thin: a single access token in `sessionStorage`,
 * so it does NOT survive a browser restart and testers can switch between the seeded
 * role accounts freely. No refresh token, no "remember me". See plan.md rev 3.
 */
export const ACCESS_TOKEN_KEY = 'dams_access_token'
export const USER_NAME_KEY = 'dams_user_name'
/**
 * Display-only extras that aren't in the JWT (plan.md rev 55): whether the Switch role button
 * shows, and the picked branch's code for the "working as" banner. Never used for authorisation.
 */
export const SESSION_EXTRAS_KEY = 'dams_session_extras'

export interface SessionExtras {
  canSwitchRole: boolean
  /** Code of the branch a switched session is scoped to, e.g. "OOR". */
  actingBranchLabel: string | null
}

export function readExtras(): SessionExtras {
  try {
    const raw = sessionStorage.getItem(SESSION_EXTRAS_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as Partial<SessionExtras>
      return { canSwitchRole: parsed.canSwitchRole === true, actingBranchLabel: parsed.actingBranchLabel ?? null }
    }
  } catch {
    // unreadable storage or bad JSON — fall through to the safe default
  }
  return { canSwitchRole: false, actingBranchLabel: null }
}

export function clearSession(): void {
  sessionStorage.removeItem(ACCESS_TOKEN_KEY)
  sessionStorage.removeItem(USER_NAME_KEY)
  sessionStorage.removeItem(SESSION_EXTRAS_KEY)
}
